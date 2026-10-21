package dev.cmdrchara.songlass.core.tags

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.model.isSameFileAs

/**
 * Rejects a recycled URI before opening an edit session when the library's known file facts
 * conflict with a fresh platform observation. Application IDs are intentionally not row IDs.
 * This does not replace the native descriptor identity and full content witness for later Save.
 */
object TagEditSelection {
    /** Use a complete, successful, unfiltered scan. A stale route must not become a different file. */
    fun isCurrent(selected: Track, library: List<Track>): Boolean {
        var current: Track? = null
        for (track in library) if (track.id == selected.id) {
            if (current != null) return false
            current = track
        }
        return current?.isSameFileAs(selected) == true
    }

    fun matches(
        selected: Track,
        uri: String,
        fileName: String?,
        folder: String?,
        dateAddedMs: Long?,
        durationMs: Long?,
    ): Boolean {
        if (selected.uri != uri) return false
        if (selected.fileName != null && selected.fileName != fileName) return false
        if (selected.folder != null && selected.folder.trim('/') != folder?.trim('/')) return false
        val added = selected.dateAddedMs?.takeIf { it > 0 }
        if (added != null && added != dateAddedMs) return false
        if (selected.durationMs > 0) {
            val duration = durationMs?.takeIf { it > 0 } ?: return false
            // Compare positive values without an overflowing subtraction of arbitrary signed input.
            if (maxOf(selected.durationMs, duration) - minOf(selected.durationMs, duration) > 1_000) return false
        }
        return true
    }
}
