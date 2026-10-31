package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track

/**
 * The logical album a downloaded cover belongs to: the artist and album tags, never a file ID, a
 * MediaStore row or a path.
 *
 * A download stays attached to this identity, so a rescanned or re-indexed library keeps showing
 * the cover the user chose until they remove it. Explicit track searches use the canonical track identity, so a chosen
 * single cover cannot leak into album siblings or an untagged collection.
 */
data class ArtworkSubject(val artist: String, val album: String, val trackId: String? = null) {
    /** Stable key of this album in the private download cache. */
    val key: String = if (trackId == null) artworkSubjectKey(artist, album) else "track|$trackId"

    companion object {
        /** A track search is always scoped to one canonical file, never a folder-derived album. */
        fun forTrack(track: Track): ArtworkSubject? = track.title.trim()
            .takeIf { it.isNotEmpty() && track.id.isNotBlank() }
            ?.let { title -> ArtworkSubject(track.artist?.trim()?.takeUnless {
                it.equals("<unknown>", ignoreCase = true)
            }.orEmpty(), title, track.id) }

        /** The subject for an album's tags, or null when the album tag is blank. */
        fun of(artist: String?, album: String?): ArtworkSubject? {
            val albumTag = album?.trim().orEmpty()
            if (albumTag.isEmpty()) return null
            return ArtworkSubject(artist?.trim().orEmpty(), albumTag)
        }
    }
}

/**
 * `artist|album`, lowercased and accent-folded. Two spellings that differ only in case,
 * accents or punctuation address the same downloaded cover.
 */
fun artworkSubjectKey(artist: String?, album: String?): String =
    normalizeArtworkText(artist.orEmpty()) + "|" + normalizeArtworkText(album.orEmpty())

/** Which source supplies the cover drawn for one logical album. */
enum class ArtworkSource {
    /** The cover the files themselves carry (embedded picture or folder image). */
    Local,

    /** A cover the user downloaded into the app's private cache. */
    Downloaded,

    /** No image at all: the generated placeholder the app already draws. */
    Generated,
}

/**
 * Cover precedence for one logical album.
 *
 * The product decision is local first, then downloaded, then generated: a file that carries its
 * own cover is never silently replaced by one fetched online. [applied] marks a cover the user
 * explicitly chose for this album: that choice wins over the local cover until it is removed,
 * which is what makes "restore the local cover" meaningful. [hasDownloadedCover] without
 * [applied] covers a cached result that was never confirmed, which must not displace anything.
 */
object ArtworkCovers {
    /** Resolve embedded/folder art first, then the album's local stand-in if distinct. */
    suspend fun <T : Any> resolveLocalImage(
        primaryKey: String?,
        fallbackKey: String?,
        load: suspend (String) -> T?,
    ): T? {
        primaryKey?.let { key -> load(key)?.let { return it } }
        return fallbackKey?.takeIf { it != primaryKey }?.let { load(it) }
    }

    /** A per-track choice wins; an existing album choice still supplies untouched siblings. */
    fun downloadedForTrack(track: Track, covers: Map<String, DownloadedArtwork>): DownloadedArtwork? {
        val single = ArtworkSubject.forTrack(track)?.let { covers[it.key] }
        val album = ArtworkSubject.of(track.groupingArtist, track.album)?.let { covers[it.key] }
        return single?.takeUnless { it.automatic } ?: album?.takeUnless { it.automatic } ?: single ?: album
    }

    fun source(hasLocalCover: Boolean, hasDownloadedCover: Boolean, applied: Boolean): ArtworkSource = when {
        applied && hasDownloadedCover -> ArtworkSource.Downloaded
        hasLocalCover -> ArtworkSource.Local
        hasDownloadedCover -> ArtworkSource.Downloaded
        else -> ArtworkSource.Generated
    }
}

/**
 * Lowercase, accent-folded, punctuation-free form of [text] with single spaces between words.
 *
 * Used for two things that must agree: the identity key of a logical album and the comparison
 * between a local tag and a provider result, so "Björk — Homogénic" matches "bjork homogenic".
 * Only Latin accents are folded, which is all the supported tag vocabulary needs; every other
 * script passes through lowercased but unchanged.
 */
fun normalizeArtworkText(text: String): String = buildString {
    var separator = false
    for (original in text.lowercase()) {
        val character = when (original) {
            'à', 'á', 'â', 'ä', 'ã', 'å', 'ā', 'ă', 'ą' -> 'a'
            'è', 'é', 'ê', 'ë', 'ē', 'ĕ', 'ė', 'ę' -> 'e'
            'ì', 'í', 'î', 'ï', 'ī' -> 'i'
            'ò', 'ó', 'ô', 'ö', 'õ', 'ø', 'ō' -> 'o'
            'ù', 'ú', 'û', 'ü', 'ū' -> 'u'
            'ç', 'ć', 'č' -> 'c'
            'ñ', 'ń' -> 'n'
            'š', 'ś' -> 's'
            'ź', 'ż' -> 'z'
            'ý', 'ÿ' -> 'y'
            'ğ' -> 'g'
            'ř' -> 'r'
            'ł' -> 'l'
            'đ' -> 'd'
            'ß' -> 's' // "Straße" and "strasse" normalise to the same words closely enough.
            in '\u0300'..'\u036f' -> continue // Decomposed accents produced by NFC/NFD differences.
            else -> original
        }
        if (character.isLetterOrDigit()) {
            if (separator && isNotEmpty()) append(' ')
            append(character)
            separator = false
        } else separator = isNotEmpty()
    }
}
