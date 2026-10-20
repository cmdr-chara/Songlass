package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.player.Crossfade

/**
 * An exact millisecond value for either duration editor. Invalid input stays a draft: it must not
 * disable a transition or silently round/clamp the user's requested duration.
 */
fun parseCrossfadeDurationMs(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any { it !in '0'..'9' }) return null
    return trimmed.toLongOrNull()?.takeIf {
        it == 0L || it in Crossfade.MIN_EFFECTIVE_MS..Crossfade.MAX_DURATION_MS
    }
}
