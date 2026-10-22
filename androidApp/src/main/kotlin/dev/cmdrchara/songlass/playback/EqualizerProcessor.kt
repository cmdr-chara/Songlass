package dev.cmdrchara.songlass.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.cmdrchara.songlass.core.equalizer.EqChain
import dev.cmdrchara.songlass.core.player.ReplayGainPlan
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.player.ReplayGainDsp
import dev.cmdrchara.songlass.core.player.ReplayGainApplication
import dev.cmdrchara.songlass.core.player.AudioProcessingInfo
import dev.cmdrchara.songlass.core.player.EqualizerProcessing
import dev.cmdrchara.songlass.core.player.PcmEncoding
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.pow

/**
 * Applies the equalizer ([EqChain]) inside ExoPlayer's audio sink: a preamp, then the biquads in
 * series (transposed direct form II, in double precision). Works on 16-bit and float PCM; other
 * encodings pass through untouched. [setChain] may be called from any thread: the audio thread
 * picks the new chain up at the next buffer and keeps the filter memory when only gains change,
 * so dragging a band does not click.
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {
    @Volatile
    private var pending: EqChain = EqChain.Flat
    private var applied: EqChain? = null
    @Volatile private var pendingReplayGain = ReplayGainPlan(ReplayGainMode.Off, 1.0)
    private var appliedReplayGain: ReplayGainPlan? = null
    private var replayGainDsp: ReplayGainDsp? = null
    private var frame = DoubleArray(0)
    private val _replayGainApplication = MutableStateFlow<ReplayGainApplication?>(null)
    val replayGainApplication: StateFlow<ReplayGainApplication?> = _replayGainApplication.asStateFlow()
    fun setReplayGain(plan: ReplayGainPlan) { pendingReplayGain = plan }
    fun observedReplayGain(plan: ReplayGainPlan): ReplayGainApplication? =
        _replayGainApplication.value?.takeIf { appliedReplayGain == plan }
    private var preamp = 1.0
    private var coefficients = DoubleArray(0)
    private var memory = DoubleArray(0)
    private var filterCount = 0
    private val _processingInfo = MutableStateFlow<AudioProcessingInfo?>(null)
    val processingInfo: StateFlow<AudioProcessingInfo?> = _processingInfo.asStateFlow()

    fun setChain(chain: EqChain) {
        pending = chain
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val encoding = when (inputAudioFormat.encoding) {
            C.ENCODING_PCM_16BIT -> PcmEncoding.Signed16
            C.ENCODING_PCM_FLOAT -> PcmEncoding.Float32
            else -> null
        }
        applied = null
        appliedReplayGain = null
        _replayGainApplication.value = null
        replayGainDsp = ReplayGainDsp(inputAudioFormat.sampleRate)
        frame = DoubleArray(inputAudioFormat.channelCount)
        _processingInfo.value = AudioProcessingInfo(
            sampleRateHz = inputAudioFormat.sampleRate,
            channelCount = inputAudioFormat.channelCount,
            encoding = encoding,
            equalizer = if (encoding == null) EqualizerProcessing.Unsupported else EqualizerProcessing.AwaitingAudio,
        )
        if (encoding == null) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val chain = pending
        if (chain !== applied) prepare(chain)
        val plan = pendingReplayGain
        if (plan != appliedReplayGain) {
            appliedReplayGain = plan
            _replayGainApplication.value = plan.application
        }
        val output = replaceOutputBuffer(size)
        val channels = inputAudioFormat.channelCount
        val floating = inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        val frameBytes = (if (floating) 4 else 2) * channels
        if (filterCount == 0 && preamp == 1.0 && plan.gain == 1.0 && !plan.limiter) {
            output.put(inputBuffer)
        } else while (inputBuffer.remaining() >= frameBytes) {
            for (channel in 0 until channels) {
                val sample = if (floating) inputBuffer.getFloat().toDouble() else inputBuffer.getShort() / 32768.0
                frame[channel] = process(sample, channel)
            }
            replayGainDsp?.processFrame(frame, plan)
            for (channel in 0 until channels) {
                if (floating) output.putFloat(frame[channel].coerceIn(-1.0, 1.0).toFloat())
                else output.putShort((frame[channel] * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort())
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun process(sample: Double, channel: Int): Double {
        var x = sample * preamp
        var c = 0
        var m = channel * filterCount * 2
        repeat(filterCount) {
            val y = coefficients[c] * x + memory[m]
            memory[m] = coefficients[c + 1] * x - coefficients[c + 3] * y + memory[m + 1]
            memory[m + 1] = coefficients[c + 2] * x - coefficients[c + 4] * y
            x = y
            c += 5
            m += 2
        }
        return x
    }

    private fun prepare(chain: EqChain) {
        val sampleRate = inputAudioFormat.sampleRate
        val channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val count = chain.filters.size
        coefficients = DoubleArray(count * 5)
        chain.filters.forEachIndexed { i, filter ->
            val b = filter.coefficients(sampleRate)
            coefficients[i * 5] = b.b0
            coefficients[i * 5 + 1] = b.b1
            coefficients[i * 5 + 2] = b.b2
            coefficients[i * 5 + 3] = b.a1
            coefficients[i * 5 + 4] = b.a2
        }
        if (count != filterCount || memory.size != count * 2 * channels) memory = DoubleArray(count * 2 * channels)
        filterCount = count
        preamp = 10.0.pow(chain.preampDb / 20.0)
        applied = chain
        // This branch runs only for a newly configured stream or changed curve. Nothing is
        // allocated or emitted for ordinary buffers, and no UI work runs on the audio thread.
        _processingInfo.value = AudioProcessingInfo(
            sampleRateHz = sampleRate,
            channelCount = channels,
            encoding = if (inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT) PcmEncoding.Float32 else PcmEncoding.Signed16,
            equalizer = if (chain.isFlat) EqualizerProcessing.Bypassed else EqualizerProcessing.Active,
            headroomDb = chain.preampDb,
            filterCount = count,
        )
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        memory.fill(0.0)
        replayGainDsp?.reset()
    }

    override fun onReset() {
        applied = null
        memory = DoubleArray(0)
        filterCount = 0
        appliedReplayGain = null
        _replayGainApplication.value = null
        replayGainDsp = null
        frame = DoubleArray(0)
        _processingInfo.value = null
    }
}
