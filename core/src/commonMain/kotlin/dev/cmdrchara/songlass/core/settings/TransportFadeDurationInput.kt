package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.player.TransportFadeSettings

/** Invalid millisecond input stays a draft and never changes a saved audio preference. */
fun parseTransportFadeDurationMs(text: String): Long? {
    val value = text.trim()
    if (value.isEmpty() || value.any { it !in '0'..'9' }) return null
    return value.toLongOrNull()?.takeIf { it in 0..TransportFadeSettings.MAX_DURATION_MS }
}
