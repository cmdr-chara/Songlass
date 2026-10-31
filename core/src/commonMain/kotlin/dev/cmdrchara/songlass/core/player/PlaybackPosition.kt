package dev.cmdrchara.songlass.core.player

/** Short display interpolation from a stamped native sample, never an audio transport command. */
fun PlayerState.presentationPositionAt(nowMs: Long): Long {
    val sample = positionSampledAtMs ?: return positionMs
    if (!positionAdvancing || !isPlaying || status != PlayerStatus.Ready) return positionMs
    val elapsed = (nowMs.toDouble() - sample.toDouble()).coerceIn(0.0, 500.0)
    val speed = playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f
    val advance = (elapsed * speed).toLong()
    val target = if (positionMs > Long.MAX_VALUE - advance) Long.MAX_VALUE else positionMs + advance
    return if (durationMs > 0) target.coerceIn(0, durationMs) else target.coerceAtLeast(0)
}
