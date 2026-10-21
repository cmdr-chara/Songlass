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

/**
 * Platform-owned evidence for the file whose tags were read. The key only binds restored drafts;
 * a writer must independently validate its own opaque witness before touching the source file.
 */
interface TagEditWitness { val sourceKey: String }

data class TagEditSnapshot(val tags: EditableTags, val witness: TagEditWitness) {
    val sourceKey: String get() = witness.sourceKey
}

data class TagEditRequest(
    val track: Track,
    val snapshot: TagEditSnapshot,
    val edited: EditableTags,
    val coverUri: String? = null,
) {
    val original: EditableTags get() = snapshot.tags
}

/** No write was authorized: the form must be reopened against the current library selection. */
class TagEditSourceChangedException : IllegalStateException("The selected audio file changed; reopen it before editing")

/** Optional platform capability. An editor is exposed only when a real file writer is installed. */
interface FileTagEditor {
    suspend fun read(track: Track): TagEditSnapshot
    suspend fun save(request: TagEditRequest)

    /** An interrupted edit can be handled independently of a now-missing library track. */
    suspend fun recoveryInfo(): TagRecoveryInfo? = null

    /** Writes retained copies to a new user-selected document and verifies its contents. */
    suspend fun exportRecovery(recoveryId: String, destinationUri: String) {
        throw UnsupportedOperationException("Tag recovery export is unavailable")
    }

    /** Explicit acknowledgement after verified export. Retains private originals in an archive. */
    suspend fun archiveRecovery(recoveryId: String) {
        throw UnsupportedOperationException("Tag recovery archive is unavailable")
    }
}

data class TagRecoveryInfo(
    /** Identifies the exact journal and retained copies; an old UI request cannot close a new edit. */
    val id: String,
    val retainedFiles: Int,
    val bytes: Long,
    val hasVerifiedExport: Boolean,
)
