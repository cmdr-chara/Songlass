package dev.cmdrchara.songlass.core.model

import kotlin.math.abs

/**
 * Whether metadata can still describe the already opened file. A platform id/content URI may
 * be reused after deletion, so it is never sufficient when paths or recording facts conflict.
 * Stable file locations allow tag edits without restarting the decoder. When no file location
 * is available, retaining the old metadata is safer than attaching it to an unrelated recording.
 */
fun Track.isSameFileAs(other: Track): Boolean {
    if (id != other.id || uri != other.uri || path != other.path || clipStartMs != other.clipStartMs || clipEndMs != other.clipEndMs) return false
    if (dateAddedMs != null && other.dateAddedMs != null && dateAddedMs != other.dateAddedMs) return false
    if (durationMs > 0 && other.durationMs > 0 && abs(durationMs - other.durationMs) > 1_000) return false
    return path != null || uri.startsWith("file:") ||
        (title == other.title && artist == other.artist && album == other.album)
}
