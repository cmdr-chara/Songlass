package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.net.HttpFailureKind
import dev.cmdrchara.songlass.core.net.HttpResult
import dev.cmdrchara.songlass.core.net.HttpTransport

/** Why a cover lookup produced nothing; the UI turns each into a readable sentence. */
enum class ArtworkSearchFailure {
    /** The caller has not enabled online covers, or this platform has no HTTP engine. */
    Disabled,

    /** No connectivity: the request never reached the provider. */
    Offline,

    /** The provider took too long to answer. */
    Timeout,

    /** The provider answered with an error status (4xx/5xx). */
    ProviderError,

    /** The answer could not be used: broken JSON, unexpected shape or a refused response. */
    Malformed,
}

/** Outcome of one lookup. Nothing is downloaded until the user picks a candidate. */
sealed interface ArtworkSearchResult {
    data class Found(val candidates: List<ArtworkCandidate>) : ArtworkSearchResult

    /** The provider answered but no entry matched this album: not an error, just no cover. */
    data object NoMatch : ArtworkSearchResult

    data class Failed(val failure: ArtworkSearchFailure) : ArtworkSearchResult
}

/**
 * Cover lookup over the shared [HttpTransport].
 *
 * The only data sent is the artist and album tags plus the search parameters derived from them:
 * never a file path, a file name, a song title or a track ID. No retry and no caching live here;
 * the caller decides when to ask again. The search term is the artist and album as they are
 * tagged, encoded once by [ITunesArtwork.searchUrl].
 */
class ArtworkSearch(
    private val transport: HttpTransport,
    private val country: String = ITunesArtwork.DEFAULT_COUNTRY,
) {
    /** URL this lookup would request, exposed so tests and diagnostics can assert the payload. */
    fun searchUrl(artist: String?, album: String): String =
        ITunesArtwork.searchUrl(artist, album, country)

    suspend fun search(album: String, artist: String?): ArtworkSearchResult {
        if (album.isBlank()) return ArtworkSearchResult.NoMatch
        val result = transport.get(searchUrl(artist, album))
        return when (result) {
            is HttpResult.Success -> {
                val candidates = ITunesArtwork.parseCandidates(result.body, artist, album)
                if (candidates.isEmpty()) ArtworkSearchResult.NoMatch else ArtworkSearchResult.Found(candidates)
            }
            is HttpResult.Failure -> ArtworkSearchResult.Failed(
                when (result.kind) {
                    HttpFailureKind.Network -> ArtworkSearchFailure.Offline
                    HttpFailureKind.Timeout -> ArtworkSearchFailure.Timeout
                    HttpFailureKind.HttpError -> ArtworkSearchFailure.ProviderError
                    HttpFailureKind.Malformed -> ArtworkSearchFailure.Malformed
                },
            )
        }
    }
}

/** Outcome of fetching the bytes of one candidate image. */
sealed interface ArtworkImageResult {
    data class Success(val bytes: ByteArray, val contentType: String?) : ArtworkImageResult

    data class Failure(val kind: HttpFailureKind) : ArtworkImageResult
}

/**
 * Byte-level image download.
 *
 * The shared [HttpTransport] contract hands back the body already decoded as text, which would
 * corrupt a JPEG or PNG (invalid UTF-8 sequences are replaced), so cover images need their own
 * path. Implementations must keep the same policy as the transport: HTTPS only, bounded size,
 * timeouts, a fixed identifiable User-Agent, no redirects to another scheme and no logging of the
 * URL. The interface is injectable so tests never touch the network; the Android implementation
 * lives in `shared/src/androidMain` next to the platform services.
 */
interface ArtworkDownloader {
    suspend fun downloadImage(url: String): ArtworkImageResult
}

/**
 * Whether a lookup or download may use the network right now.
 *
 * The preference is checked first: with online covers off the app never contacts a provider, even
 * when the user opens the picker. [metered] is the platform's view of the current connection, or
 * null when the platform cannot tell: an unknown connection counts as metered, so "Wi-Fi only"
 * never silently spends mobile data.
 */
object OnlineArtworkPolicy {
    fun canUseNetwork(
        enabled: Boolean,
        hasEngine: Boolean,
        wifiOnly: Boolean,
        metered: Boolean?,
    ): Boolean = enabled && hasEngine && (!wifiOnly || metered == false)
}
