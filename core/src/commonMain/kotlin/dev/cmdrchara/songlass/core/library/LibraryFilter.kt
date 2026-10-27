package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.model.Track

data class LibraryFolderOption(val path: String, val trackCount: Int, val children: List<LibraryFolderOption>) {
    val name: String get() = path.substringAfterLast('/')
}

/** A view of the library: exclusions never delete files or saved playlist/favourite ids. */
data class LibraryFilter(
    val excludedFolders: Set<String> = emptySet(),
    val minimumDurationMs: Long = 0,
    /**
     * Smallest star rating a track needs to stay visible: 0 shows every track, 1..5 keep the
     * tracks rated at least that many stars. A rating is not a tag: [includes] cannot read it, so
     * the vote part of the filter is applied where the ratings are known (see [LibraryIndex] and
     * `SmartRules.resolve`). Values outside 0..5 are not produced by the settings; a positive
     * out-of-range value matches no track, so a corrupt value hides the library instead of
     * pretending the filter is off.
     */
    val minimumRating: Int = 0,
) {
    fun includes(track: Track): Boolean {
        // An unknown duration is not evidence that a file is short.
        if (track.durationMs > 0 && track.durationMs < minimumDurationMs) return false
        val folder = track.folder?.trim('/') ?: return true
        return excludedFolders.none { folder == it || folder.startsWith("$it/") }
    }

    fun apply(tracks: List<Track>): List<Track> = tracks.filter(::includes)

    companion object {
        val durationChoicesMs = listOf(0L, 30_000L, 60_000L, 90_000L, 120_000L)

        /** A hierarchy for the exclusion picker, retaining unavailable excluded folders. */
        fun folderTree(tracks: List<Track>, excludedPaths: Set<String> = emptySet()): List<LibraryFolderOption> {
            val paths = (foldersIn(tracks) + excludedPaths.flatMap { path ->
                val parts = path.trim('/').split('/')
                parts.indices.map { parts.take(it + 1).joinToString("/") }
            }).filter { it.isNotBlank() }.toSet()
            val counts = mutableMapOf<String, Int>()
            tracks.forEach { track ->
                val parts = track.folder?.trim('/')?.split('/') ?: return@forEach
                parts.indices.forEach { index ->
                    val path = parts.take(index + 1).joinToString("/")
                    counts[path] = (counts[path] ?: 0) + 1
                }
            }
            val byParent = paths.groupBy { it.substringBeforeLast('/', "") }
            fun build(path: String): LibraryFolderOption = LibraryFolderOption(path, counts[path] ?: 0,
                byParent[path].orEmpty().sortedBy { it.lowercase() }.map(::build))
            return byParent[""].orEmpty().sortedBy { it.lowercase() }.map(::build)
        }

        /** All real ancestors as well as leaf folders, retaining their full unambiguous paths. */
        fun foldersIn(tracks: List<Track>): List<String> = tracks.mapNotNull { it.folder }
            .flatMap { path ->
                val parts = path.split('/').filter { it.isNotBlank() }
                parts.indices.map { parts.take(it + 1).joinToString("/") }
            }.distinct().sortedBy { it.lowercase() }
    }
}
