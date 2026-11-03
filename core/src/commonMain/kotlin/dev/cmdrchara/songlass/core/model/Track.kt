package dev.cmdrchara.songlass.core.model

/**
 * A playable audio file with its tag metadata.
 *
 * Missing tags are `null`: the UI shows a localized placeholder (e.g. "Unknown artist"),
 * so core never invents display strings.
 */
data class Track(
    /** Application identifier; registry-backed sources preserve it across platform reindexing.
     * Opaque to consumers: it must never be parsed as a MediaStore row ID. */
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
    /** Individual ARTISTS / multi-value artist credits; the original display tag is preserved. */
    val artists: List<String> = emptyList(),
    val releaseDate: ReleaseDate? = null,
    /** Changes when file metadata/artwork changes; never used as the playable URI. */
    val artworkRevision: Long = 0,
    /** Genre as tagged/indexed by the platform; null when unknown. */
    val genre: String? = null,
    /** Composer credits from actual file/platform metadata; empty when unavailable. */
    val composers: List<String> = emptyList(),
    /** Platform-reported absolute file path for playlist export; never used as a playable URI. */
    val absoluteFilePath: String? = null,
    /** CUE segment bounds in the physical file; player positions remain segment-relative. */
    val clipStartMs: Long = 0,
    val clipEndMs: Long? = null,
    val cueSourceId: String? = null,
) {
    init { require(clipStartMs >= 0 && (clipEndMs == null || clipEndMs > clipStartMs)) }
    val isCueTrack: Boolean get() = clipEndMs != null

    /** `folder/fileName` relative to the storage root, as written in M3U playlists; null if unknown. */
    val path: String? get() = fileName?.let { name -> folder?.takeIf { it.isNotBlank() }?.let { "${it.trim('/')}/$name" } ?: name }

    /** A cache address, never a playable URI. Reused platform rows cannot inherit another file's cover. */
    val artworkKey: String = dev.cmdrchara.songlass.core.artwork.ArtworkKeys.forTrack(this)

    val artistNames: List<String> get() = ArtistTags.names(artist, artists)

    /** Artist used to group albums: the album artist when tagged, otherwise the track artist. */
    val groupingArtist: String? get() = albumArtist ?: artist
}
