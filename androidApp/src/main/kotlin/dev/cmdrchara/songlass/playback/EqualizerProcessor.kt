package dev.cmdrchara.songlass.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import dev.cmdrchara.songlass.core.equalizer.EqChain
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
    private var preamp = 1.0
    private var coefficients = DoubleArray(0)
    private var memory = DoubleArray(0)
    private var filterCount = 0

    fun setChain(chain: EqChain) {
        pending = chain
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        // Coefficients depend on the sample rate.
        applied = null
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val chain = pending
        if (chain !== applied) prepare(chain)
        val output = replaceOutputBuffer(size)
        val channels = inputAudioFormat.channelCount
        when {
            filterCount == 0 && preamp == 1.0 -> output.put(inputBuffer)
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT -> while (inputBuffer.remaining() >= 4 * channels) {
                for (channel in 0 until channels) {
                    output.putFloat(process(inputBuffer.getFloat().toDouble(), channel).coerceIn(-1.0, 1.0).toFloat())
                }
            }
            else -> while (inputBuffer.remaining() >= 2 * channels) {
                for (channel in 0 until channels) {
                    val y = process(inputBuffer.getShort() / 32768.0, channel) * 32768.0
                    output.putShort(y.coerceIn(-32768.0, 32767.0).toInt().toShort())
                }
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
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        memory.fill(0.0)
    }

    override fun onReset() {
        applied = null
        memory = DoubleArray(0)
        filterCount = 0
    }
}
