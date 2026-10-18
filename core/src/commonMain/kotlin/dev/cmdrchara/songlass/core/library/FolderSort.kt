package dev.cmdrchara.songlass.core.library

/** How the Library orders its folders. */
enum class FolderSort {
    /** Alphabetical. */
    Name,

    /** Folders whose newest file arrived last come first. */
    RecentlyAdded,

    /** Most counted plays across everything inside first. */
    MostPlayed;

    fun sort(folders: List<Folder>, playCounts: Map<String, Int>): List<Folder> = when (this) {
        Name -> folders.sortedBy { it.name.lowercase() }
        RecentlyAdded -> folders.sortedByDescending { folder -> folder.allTracks.maxOfOrNull { it.dateAddedMs ?: 0L } ?: 0L }
        MostPlayed -> folders.sortedWith(
            compareByDescending<Folder> { folder -> folder.allTracks.sumOf { playCounts[it.id] ?: 0 } }.thenBy { it.name.lowercase() },
        )
    }
}
