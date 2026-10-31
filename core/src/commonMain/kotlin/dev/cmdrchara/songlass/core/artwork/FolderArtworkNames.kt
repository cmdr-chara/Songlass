package dev.cmdrchara.songlass.core.artwork

/** Exact candidates only: never borrow the first image of an unrelated sibling album. */
object FolderArtworkNames {
    val imageExtensions = listOf("jpg", "jpeg", "png", "webp")
    val artworkDirectories = listOf("Artwork", "Artworks", "Covers", "Cover", "Scans")
    private val conventional = listOf("cover", "folder", "front", "album", "albumart")

    fun imageNames(fileName: String?, album: String?, title: String? = null, generic: Boolean = true): List<String> {
        val exact = listOfNotNull(fileName?.let { it.substringBeforeLast('.', it) }, album, title)
            .map(String::trim).filter { it.isNotEmpty() && '/' !in it && '\\' !in it && it != "." && it != ".." }
        return (exact + if (generic) conventional else emptyList()).distinctBy(String::lowercase)
            .flatMap { name -> imageExtensions.map { "$name.$it" } }
    }

    /** Streaming provider scans retain only requested entries, regardless of cursor position. */
    fun isRequestedEntry(name: String, directory: Boolean, imageNames: List<String>, directoryNames: List<String>): Boolean =
        if (directory) directoryNames.any { sameName(name, it) }
        else imageNames.any { it.equals(name, ignoreCase = true) }

    /** Disc directories may inherit their own album's cover, never an arbitrary music-root cover. */
    fun isDiscDirectory(name: String): Boolean = Regex("(?i)(?:cd|disc|disk)[ _-]*\\d{1,2}").matches(name.trim())

    fun sameName(first: String, second: String?): Boolean =
        second != null && normalizeArtworkText(first) == normalizeArtworkText(second) && normalizeArtworkText(first).isNotEmpty()
}
