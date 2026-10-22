package dev.cmdrchara.songlass.core.player

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/** Independent, neutral-by-default settings. Percentages are stored as whole integers. */
data class AudioEffectsSettings(
    val balancePercent: Int = 0,
    val stereoExpandPercent: Int = 0,
    val tempoPercent: Int = 100,
    val reverbPercent: Int = 0,
) {
    init {
        require(balancePercent in -100..100 && stereoExpandPercent in 0..100)
        require(tempoPercent in 50..200 && reverbPercent in 0..100)
    }
    val speed: Float get() = tempoPercent / 100f
    fun pcmForChannels(channels: Int) = AudioEffectsProcessing(
        balancePercent = if (channels == 2) balancePercent else 0,
        stereoExpandPercent = if (channels == 2) stereoExpandPercent else 0,
        reverbPercent = if (channels in 1..2) reverbPercent else 0,
    )
}

/** The three PCM controls actually applicable to a decoded stream; tempo belongs to Media3. */
data class AudioEffectsProcessing(val balancePercent: Int = 0, val stereoExpandPercent: Int = 0, val reverbPercent: Int = 0) {
    init { require(balancePercent in -100..100 && stereoExpandPercent in 0..100 && reverbPercent in 0..100) }
    val isNeutral: Boolean get() = balancePercent == 0 && stereoExpandPercent == 0 && reverbPercent == 0
}

enum class AudioEffect(val minimum: Int, val maximum: Int, val default: Int) {
    Balance(-100, 100, 0), StereoExpand(0, 100, 0), Tempo(50, 200, 100), Reverb(0, 100, 0);

    fun value(settings: AudioEffectsSettings): Int = when (this) {
        Balance -> settings.balancePercent
        StereoExpand -> settings.stereoExpandPercent
        Tempo -> settings.tempoPercent
        Reverb -> settings.reverbPercent
    }
    fun changing(settings: AudioEffectsSettings, value: Int): AudioEffectsSettings {
        require(value in minimum..maximum)
        return when (this) {
            Balance -> settings.copy(balancePercent = value)
            StereoExpand -> settings.copy(stereoExpandPercent = value)
            Tempo -> settings.copy(tempoPercent = value)
            Reverb -> settings.copy(reverbPercent = value)
        }
    }
    fun fromSlider(position: Float): Int {
        require(position.isFinite())
        return minimum + (position.coerceIn(0f, 1f) * (maximum - minimum)).roundToInt()
    }
    fun sliderPosition(value: Int): Float = (value.coerceIn(minimum, maximum) - minimum).toFloat() / (maximum - minimum)
}

/**
 * Per-voice, allocation-free frame processing: headroom-preserving mid/side expansion,
 * fixed-room algorithmic reverb, then balance. Does not change channel count or sample rate.
 * Set parameters at buffer boundaries; a 10 ms one-pole transition smooths gain changes.
 * Unsupported surround layouts pass through. Flush discards the room's tail.
 */
class AudioEffectsDsp(private val sampleRateHz: Int, private val channels: Int,
    initial: AudioEffectsProcessing = AudioEffectsProcessing()) {
    init { require(sampleRateHz > 0 && channels > 0) }
    private var parameters = initial
    private val smoothing = 1.0 - exp(-1.0 / (sampleRateHz * .01))
    private var balance = initial.balancePercent / 100.0
    private var expansion = initial.stereoExpandPercent / 100.0
    private var wet = initial.reverbPercent / 200.0
    private var room: Array<Room>? = if (initial.reverbPercent > 0 && channels in 1..2) createRoom() else null

    val isBypassed: Boolean get() = parameters.isNeutral && balance == 0.0 && expansion == 0.0 && wet == 0.0

    fun setParameters(next: AudioEffectsProcessing) {
        if (next == parameters) return
        if (next.reverbPercent > 0 && channels in 1..2 && room == null) room = createRoom()
        parameters = next
    }

    fun processFrame(frame: DoubleArray) {
        require(frame.size == channels)
        if (channels !in 1..2) return
        balance = approach(balance, parameters.balancePercent / 100.0)
        expansion = approach(expansion, parameters.stereoExpandPercent / 100.0)
        wet = approach(wet, parameters.reverbPercent / 200.0)
        for (i in frame.indices) if (!frame[i].isFinite()) frame[i] = 0.0
        if (channels == 2 && expansion != 0.0) {
            // Divide by width: the absolute row sums are one, preserving sample headroom.
            val middle = (frame[0] + frame[1]) * .5 / (1.0 + expansion)
            val side = (frame[0] - frame[1]) * .5
            frame[0] = middle + side
            frame[1] = middle - side
        }
        val rooms = room
        if (wet != 0.0 && rooms != null) {
            for (i in frame.indices) frame[i] = frame[i] * (1.0 - wet) + rooms[i].process(frame[i]) * wet
        } else if (rooms != null && parameters.reverbPercent == 0) {
            // Once after the smooth fade to zero, so enabling it again cannot resurrect a tail.
            rooms.forEach { it.reset() }
            room = null
        }
        if (channels == 2) {
            frame[0] *= 1.0 - maxOf(0.0, balance)
            frame[1] *= 1.0 + minOf(0.0, balance)
        }
    }

    fun reset() {
        balance = parameters.balancePercent / 100.0
        expansion = parameters.stereoExpandPercent / 100.0
        wet = parameters.reverbPercent / 200.0
        room?.forEach { it.reset() }
    }

    private fun approach(current: Double, target: Double): Double =
        if (abs(current - target) < .000001) target else current + (target - current) * smoothing
    private fun createRoom() = Array(channels) { Room(sampleRateHz, it) }
}

/** Four damped feedback delays with two all-pass diffusion stages, owned by one PCM channel. */
private class Room(sampleRateHz: Int, channel: Int) {
    private val combs = doubleArrayOf(29.7, 37.1, 41.1, 43.7).map {
        Comb((sampleRateHz * (it + channel * 2.3) / 1000).roundToInt().coerceAtLeast(1))
    }
    private val diffusers = doubleArrayOf(5.3, 1.7).map {
        AllPass((sampleRateHz * (it + channel * .3) / 1000).roundToInt().coerceAtLeast(1))
    }
    fun process(input: Double): Double {
        var value = 0.0
        for (comb in combs) value += comb.process(input)
        value *= .25
        for (diffuser in diffusers) value = diffuser.process(value)
        // Leave headroom for transient peaks from the diffusion stages.
        return value * .5
    }
    fun reset() { combs.forEach { it.reset() }; diffusers.forEach { it.reset() } }
}
private class Comb(length: Int) {
    private val delay = DoubleArray(length)
    private var index = 0
    private var damped = 0.0
    fun process(input: Double): Double {
        val output = delay[index]
        damped = output * .7 + damped * .3
        delay[index] = input * .28 + damped * .72
        if (++index == delay.size) index = 0
        return output
    }
    fun reset() { delay.fill(0.0); index = 0; damped = 0.0 }
}
private class AllPass(length: Int) {
    private val delay = DoubleArray(length)
    private var index = 0
    fun process(input: Double): Double {
        val delayed = delay[index]
        val next = input + delayed * .5
        delay[index] = next
        if (++index == delay.size) index = 0
        return delayed - next * .5
    }
    fun reset() { delay.fill(0.0); index = 0 }
}
