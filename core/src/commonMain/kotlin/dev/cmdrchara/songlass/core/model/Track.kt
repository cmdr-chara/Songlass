package dev.cmdrchara.songlass.core.model

/**
 * A playable audio file with its tag metadata.
 *
 * Missing tags are `null`: the UI shows a localized placeholder (e.g. "Unknown artist"),
 * so core never invents display strings.
 */
data class Track(
    /** Stable identifier from the platform media store. */
    val id: String,
    val title: String,
    /** Location the platform player can open (content URI on Android, file URL on iOS). */
    val uri: String,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val durationMs: Long = 0,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    /** Folder containing the file, as `/`-separated segments (e.g. `Music/Chill`); null if unknown. */
    val folder: String? = null,
    /** When the file arrived on the device (epoch ms), if known. */
    val dateAddedMs: Long? = null,
    /** File name with extension (e.g. `01 Intro.flac`), used for playlist files; null if unknown. */
    val fileName: String? = null,
) {
    /** `folder/fileName` relative to the storage root, as written in M3U playlists; null if unknown. */
    val path: String? get() = fileName?.let { name -> folder?.takeIf { it.isNotBlank() }?.let { "${it.trim('/')}/$name" } ?: name }

    /** Artist used to group albums: the album artist when tagged, otherwise the track artist. */
    val groupingArtist: String? get() = albumArtist ?: artist
}
