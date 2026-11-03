package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track

/** Only local file lyrics use whole-file timestamps; online/imported lyrics describe the cut. */
internal fun Lyrics.forSegment(track: Track): Lyrics? {
    if (!track.isCueTrack) return this
    if (!isSynced) return null
    val start = track.clipStartMs
    val end = track.clipEndMs ?: return this
    val previous = lines.indexOfLast { it.timeMs!! <= start }
    val selected = lines.filterIndexed { index, line -> (index == previous || line.timeMs!! >= start) && line.timeMs!! < end }
    if (selected.isEmpty()) return null
    return copy(lines = selected.map { line ->
        line.copy(timeMs = (line.timeMs!! - start).coerceAtLeast(0), words = line.words.filter {
            it.startMs < end && (it.endMs == null || it.endMs > start)
        }.map { word -> word.copy(startMs = (word.startMs - start).coerceAtLeast(0),
            endMs = word.endMs?.let { (it.coerceAtMost(end) - start).coerceAtLeast(0) }) })
    })
}
