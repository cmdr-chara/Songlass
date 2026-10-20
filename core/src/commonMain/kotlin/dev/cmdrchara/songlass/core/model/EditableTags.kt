package dev.cmdrchara.songlass.core.model

/** File tag values stay separate from library fallbacks. Blank optional values remove tags. */
data class EditableTags(
    val title: String,
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val trackNumber: String = "",
    val discNumber: String = "",
    val date: String = "",
) {
    /** Existing unusual tags may be preserved; only newly edited numeric/date values must parse. */
    fun isValidComparedTo(original: EditableTags): Boolean = title.isNotBlank() &&
        listOf(title, artist, album, albumArtist, trackNumber, discNumber, date).all { it.length <= 4096 && '\u0000' !in it } &&
        (trackNumber == original.trackNumber || numberIsValid(trackNumber)) &&
        (discNumber == original.discNumber || numberIsValid(discNumber)) &&
        (date == original.date || date.isBlank() || ReleaseDate.parse(date) != null)

    /** Only changed keys are written: unrelated metadata is the platform writer's responsibility. */
    fun changesFrom(original: EditableTags): Map<String, String> = buildMap {
        fun field(key: String, before: String, after: String) { if (before != after) put(key, after.trim()) }
        field("TITLE", original.title, title)
        field("ARTIST", original.artist, artist)
        field("ALBUM", original.album, album)
        field("ALBUMARTIST", original.albumArtist, albumArtist)
        field("TRACKNUMBER", original.trackNumber, trackNumber)
        field("DISCNUMBER", original.discNumber, discNumber)
        field("DATE", original.date, date)
    }

    /** ARTISTS is related to ARTIST: refresh existing explicit credits only when the artist was edited. */
    fun artistCreditsChangedFrom(original: EditableTags): List<String>? =
        if (artist == original.artist) null else ArtistTags.names(artist)

    private fun numberIsValid(text: String): Boolean = text.isBlank() ||
        (Regex("[0-9]{1,6}(?:/[0-9]{1,6})?").matches(text.trim()) && text.trim().split('/').all { it.toInt() > 0 })
}

data class TagEditRequest(val track: Track, val original: EditableTags, val edited: EditableTags, val coverUri: String? = null)

/** Optional platform capability. An editor is exposed only when a real file writer is installed. */
interface FileTagEditor {
    suspend fun read(track: Track): EditableTags
    suspend fun save(request: TagEditRequest)
}
