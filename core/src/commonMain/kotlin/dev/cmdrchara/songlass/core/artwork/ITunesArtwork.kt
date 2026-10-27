package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.net.arrayOrNull
import dev.cmdrchara.songlass.core.net.asJsonObjectOrNull
import dev.cmdrchara.songlass.core.net.isSecureHttpUrl
import dev.cmdrchara.songlass.core.net.longOrNull
import dev.cmdrchara.songlass.core.net.parseJsonOrNull
import dev.cmdrchara.songlass.core.net.stringOrNull

/**
 * One album cover a provider offers for a logical album.
 *
 * Untrusted data: [artist] and [album] are whatever the provider sent, kept only to label the
 * candidate and to double-check the match. [imageUrl] is the largest size the provider offers for
 * the same picture (usually 600 px), [previewUrl] the small one used in the picker.
 */
data class ArtworkCandidate(
    /** Provider's own identifier, used to collapse duplicates; never shown to the user. */
    val providerId: String,
    val artist: String,
    val album: String,
    /** HTTPS URL of the full-size image to download. */
    val imageUrl: String,
    /** HTTPS URL of a small preview, or null when the provider sent only one size. */
    val previewUrl: String?,
)

/**
 * iTunes Search API provider, kept pure so it can be tested without a network.
 *
 * Only the search result is parsed here: no JSON model is generated, every field is read through
 * the safe readers in `core/net`, and a malformed entry is skipped instead of failing the whole
 * response. The API is public and needs no key or account; the app sends the artist and album
 * tags as the search term and nothing else (see docs/PRIVACY.md).
 */
object ITunesArtwork {
    const val SEARCH_ENDPOINT = "https://itunes.apple.com/search"

    /** Store country used for the search; the user's own tags are unaffected by it. */
    const val DEFAULT_COUNTRY = "IT"

    /** Results requested per search: enough candidates to choose from, few enough to preview. */
    const val DEFAULT_LIMIT = 8

    /** The API refuses more than 200, and this feature never needs more than a handful. */
    const val MAX_LIMIT = 50

    /** Size downloaded into the private cache: sharp on a phone at a modest number of bytes. */
    const val DOWNLOAD_PX = 600

    /** Size of the thumbnails shown while choosing. */
    const val PREVIEW_PX = 100

    /** Slightly odd tags still resolve to the right entry, but a different album is rejected. */
    const val MIN_ALBUM_SCORE = 60

    /** Search URL for [artist] and [album] tags. [country] falls back when it is not a 2-letter code. */
    fun searchUrl(
        artist: String?,
        album: String,
        country: String = DEFAULT_COUNTRY,
        limit: Int = DEFAULT_LIMIT,
    ): String {
        val term = listOfNotNull(artist?.trim()?.takeIf { it.isNotEmpty() }, album.trim().takeIf { it.isNotEmpty() })
            .joinToString(" ")
        val requested = limit.coerceIn(1, MAX_LIMIT)
        val storeCountry = country.trim().uppercase().takeIf { code -> code.length == 2 && code.all { it in 'A'..'Z' } }
            ?: DEFAULT_COUNTRY
        return SEARCH_ENDPOINT + "?term=" + encodeQueryValue(term) +
            "&media=music&entity=album&limit=" + requested + "&country=" + storeCountry
    }

    /**
     * Candidates for [album] (and [artist] when known), best match first.
     *
     * An entry is kept only when its album title matches, its artist does not contradict the tags,
     * its artwork URL is HTTPS and it is not a duplicate. Malformed JSON, a missing `results`
     * array or a body that is not an object at all all yield an empty list: the caller reports
     * "no cover found" instead of showing unverified entries.
     */
    fun parseCandidates(
        json: String,
        artist: String?,
        album: String,
        limit: Int = DEFAULT_LIMIT,
    ): List<ArtworkCandidate> {
        if (normalizeArtworkText(album).isEmpty()) return emptyList()
        val root = parseJsonOrNull(json).asJsonObjectOrNull() ?: return emptyList()
        val results = runCatching { root.arrayOrNull("results") }.getOrNull() ?: return emptyList()
        return results.asSequence()
            .mapNotNull { element -> element.asJsonObjectOrNull()?.let(::providerAlbum) }
            .mapNotNull { entry -> matchScore(entry, artist, album)?.let { score -> Scored(score, entry) } }
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.entry.providerId })
            .map { it.entry.candidate() }
            .distinctBy { it.providerId }
            .take(limit.coerceIn(1, MAX_LIMIT))
            .toList()
    }

    /**
     * [url] rewritten to a [sizePx] square, or null when the URL does not carry a size token.
     *
     * iTunes returns `.../100x100bb.jpg`; the same picture is available at larger bounding boxes.
     * The extension and the flags after the size (`bb`) are preserved, so an unexpected asset shape
     * keeps working instead of being rewritten into a broken URL. Callers fall back to the
     * provider's original URL when this returns null.
     */
    fun urlForResolution(url: String, sizePx: Int): String? {
        if (sizePx <= 0) return null
        val match = SIZE_TOKEN.find(url) ?: return null
        val flags = match.groupValues[3]
        val extension = match.groupValues[4]
        val head = url.substring(0, match.range.first)
        if (head.isEmpty()) return null
        val rewritten = head + sizePx + "x" + sizePx + flags + extension
        return rewritten.takeIf(::isSecureHttpUrl)
    }

    /** Score of a provider entry against the local tags, or null when it is a different album. */
    internal fun matchScore(entry: ProviderAlbum, artist: String?, album: String): Int? {
        val albumScore = albumScore(album, entry.album) ?: return null
        if (albumScore < MIN_ALBUM_SCORE) return null
        val artistScore = artistScore(artist, entry.artist) ?: return null
        return albumScore + artistScore
    }

    /** One `results` entry, already reduced to the fields this feature uses. */
    internal data class ProviderAlbum(
        val providerId: String,
        val artist: String,
        val album: String,
        val imageUrl: String,
        val previewUrl: String?,
    ) {
        fun candidate(): ArtworkCandidate = ArtworkCandidate(providerId, artist, album, imageUrl, previewUrl)
    }

    private data class Scored(val score: Int, val entry: ProviderAlbum)

    /** `100x100bb.jpg`-shaped tail: size, optional flags and the extension, anchored at the end. */
    private val SIZE_TOKEN = Regex("""(\d{2,5})x(\d{2,5})([a-z]{0,3})(\.[A-Za-z]{2,5})$""")

    private fun providerAlbum(obj: kotlinx.serialization.json.JsonObject): ProviderAlbum? {
        // entity=album returns collections; a track result would be a different kind of answer.
        val wrapper = obj.stringOrNull("wrapperType")
        if (wrapper != null && wrapper != "collection") return null
        val album = obj.stringOrNull("collectionName")?.trim().orEmpty()
        if (album.isEmpty()) return null
        val artwork = listOfNotNull(obj.stringOrNull("artworkUrl100"), obj.stringOrNull("artworkUrl60"))
            .map(String::trim)
            .firstOrNull(::isSecureHttpUrl)
            ?: return null
        val id = obj.longOrNull("collectionId")?.toString() ?: obj.stringOrNull("collectionId") ?: artwork
        return ProviderAlbum(
            providerId = id,
            artist = obj.stringOrNull("artistName")?.trim().orEmpty(),
            album = album,
            imageUrl = urlForResolution(artwork, DOWNLOAD_PX) ?: artwork,
            previewUrl = urlForResolution(artwork, PREVIEW_PX) ?: artwork,
        )
    }

    private fun albumScore(target: String?, candidate: String): Int? {
        val wanted = normalizeArtworkText(target.orEmpty())
        val offered = normalizeArtworkText(candidate)
        if (wanted.isEmpty() || offered.isEmpty()) return null
        if (wanted == offered) return 100
        val stripped = normalizeArtworkText(withoutBracketed(candidate))
        if (stripped.isNotEmpty() && stripped == wanted) return 95
        val wantedWords = wanted.split(' ').toSet()
        val offeredWords = offered.split(' ').toSet()
        // A provider title that is a subset (local "Abbey Road (Remastered)") is still the album.
        if (offeredWords.containsAll(wantedWords)) return 85
        if (wantedWords.containsAll(offeredWords)) return 80
        if (offered.startsWith("$wanted ")) return 75
        if (stripped.startsWith("$wanted ")) return 70
        // "Album - EP" and friends: the title before the marker is the album being searched.
        val head = wanted.substringBefore(" - ").trim()
        if (head.isNotEmpty() && head != wanted && (offered == head || offered.startsWith("$head "))) return 65
        return null
    }

    private fun artistScore(target: String?, candidate: String): Int? {
        val wanted = normalizeArtworkText(target.orEmpty())
        if (wanted.isEmpty()) return NEUTRAL_ARTIST_SCORE // Nothing to contradict: the album decides.
        val offered = normalizeArtworkText(candidate)
        if (offered.isEmpty()) return null
        if (wanted == offered) return 100
        val wantedWords = wanted.split(' ').toSet()
        val offeredWords = offered.split(' ').toSet()
        if (offered.startsWith("$wanted ") || wanted.startsWith("$offered ")) return 90
        if (offeredWords.containsAll(wantedWords)) return 80
        if (wantedWords.containsAll(offeredWords)) return 75
        return null
    }

    /** Words that are neutral when the local artist tag is missing. */
    private const val NEUTRAL_ARTIST_SCORE = 40

    /** Everything inside brackets describes an edition, not the album being searched. */
    private fun withoutBracketed(text: String): String = text.replace(BRACKETS, " ")

    private val BRACKETS = Regex("""\([^)]*\)|\[[^\]]*]""")

    /** RFC 3986 percent-encoding; '+' would be accepted, but a literal is unambiguous. */
    private fun encodeQueryValue(value: String): String = buildString {
        for (byte in value.encodeToByteArray()) {
            val unsigned = byte.toInt() and 0xff
            val character = unsigned.toChar()
            when {
                unsigned < 0x80 && (character in 'a'..'z' || character in 'A'..'Z' || character in '0'..'9') -> append(character)
                character in "-._~" -> append(character)
                character == ' ' -> append("%20")
                else -> append('%').append(HEX[unsigned ushr 4]).append(HEX[unsigned and 0x0f])
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"
}
