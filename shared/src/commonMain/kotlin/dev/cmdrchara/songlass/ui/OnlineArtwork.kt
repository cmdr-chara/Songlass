package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import dev.cmdrchara.songlass.core.artwork.ArtworkCovers
import dev.cmdrchara.songlass.core.artwork.ArtworkDownloader
import dev.cmdrchara.songlass.core.artwork.ArtworkFileStore
import dev.cmdrchara.songlass.core.artwork.ArtworkSearch
import dev.cmdrchara.songlass.core.artwork.ArtworkSource
import dev.cmdrchara.songlass.core.artwork.ArtworkSubject
import dev.cmdrchara.songlass.core.artwork.DownloadedArtwork
import dev.cmdrchara.songlass.core.artwork.DownloadedArtworks
import dev.cmdrchara.songlass.core.artwork.OnlineArtworkPolicy
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.net.platformHttpTransport
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.ArtworkImageLoader
import dev.cmdrchara.songlass.designsystem.foundation.CachedArtworkImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.time.Clock

/**
 * Platform corner of the cover lookup: where downloaded covers may live, how image bytes are
 * fetched and whether the current connection is metered. Null where the platform cannot offer the
 * feature (today: iOS, which has no tested HTTP engine — the entry points stay hidden there).
 *
 * Android supplies all three; nothing gives this class access to audio files or to the media store.
 */
class OnlineArtworkPlatform(
    val fileStore: ArtworkFileStore,
    val downloader: ArtworkDownloader?,
    val isMeteredNetwork: () -> Boolean,
)

@Composable
expect fun rememberPlatformOnlineArtwork(): OnlineArtworkPlatform?

/**
 * Cover lookup as the screens see it: the provider search, the private download cache and the
 * platform's connection state. Created once per composition root, never per screen.
 */
class OnlineArtworkController(
    val downloaded: DownloadedArtworks,
    val search: ArtworkSearch,
    val downloader: ArtworkDownloader?,
    private val isMeteredNetwork: () -> Boolean,
) {
    /** Whether raw image bytes can be fetched on this platform; search alone is not enough. */
    val downloadsAvailable: Boolean get() = downloader != null

    /**
     * The user's switches decide first; the platform only reports the connection. See
     * [OnlineArtworkPolicy]: an unknown connection counts as metered.
     */
    fun canUseNetwork(preferences: AppPreferences): Boolean =
        OnlineArtworkPolicy.canUseNetwork(
            enabled = preferences.onlineArtworkEnabled,
            hasEngine = true,
            wifiOnly = preferences.onlineArtworkWifiOnly,
            metered = runCatching(isMeteredNetwork).getOrNull(),
        )
}

/** The cover lookup of this composition, or null where the platform has no engine. */
val LocalOnlineArtwork = compositionLocalOf<OnlineArtworkController?> { null }

/** Remembers the controller over the whole composition: the download cache is read once. */
@Composable
fun rememberOnlineArtworkController(): OnlineArtworkController? {
    val platform = rememberPlatformOnlineArtwork() ?: return null
    val transport = remember { platformHttpTransport() } ?: return null
    val controller = remember(platform, transport) {
        OnlineArtworkController(
            downloaded = DownloadedArtworks(
                files = platform.fileStore,
                now = { Clock.System.now().toEpochMilliseconds() },
            ),
            search = ArtworkSearch(transport),
            downloader = platform.downloader,
            isMeteredNetwork = platform.isMeteredNetwork,
        )
    }
    androidx.compose.runtime.LaunchedEffect(controller) { controller.downloaded.load() }
    return controller
}

/** Image key prefix for a downloaded cover: it addresses the logical album, not a file. */
private const val DOWNLOADED_KEY_PREFIX = "songlass-online:"

/** The image key the album page and the picker use for a downloaded cover. */
fun downloadedArtworkKey(subject: ArtworkSubject): String = DOWNLOADED_KEY_PREFIX + subject.key

/**
 * Draws downloaded covers on top of the platform loader.
 *
 * The platform loader resolves audio-URI keys only; a downloaded cover lives in the app's private
 * storage, so this decorator reads its bytes through the cache and decodes them. Every other key is
 * passed through unchanged, including its cache and its revision, so permission recovery keeps
 * working for real files.
 */
class DownloadedArtworkImageLoader(
    private val controller: OnlineArtworkController,
    private val delegate: ArtworkImageLoader?,
) : ArtworkImageLoader {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    // Decoded 600 px covers by image key. Composition happens on the main thread and every write
    // below resumes there, so reads from `cached` never race a write.
    private val decoded = LinkedHashMap<String, ImageBitmap>()

    private val _revision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = _revision.asStateFlow()

    init {
        scope.launch {
            controller.downloaded.revision.drop(1).collect {
                decoded.clear()
                _revision.value = _revision.value + 1
            }
        }
        delegate?.revision?.let { delegateRevision ->
            scope.launch { delegateRevision.drop(1).collect { _revision.value = _revision.value + 1 } }
        }
    }

    override suspend fun load(key: String, sizePx: Int): ImageBitmap? {
        val subjectKey = downloadedSubjectKey(key) ?: return delegate?.load(key, sizePx)
        decoded[key]?.let { return it }
        val bitmap = withContext(Dispatchers.Default) {
            val bytes = controller.downloaded.bytesOf(subjectKey) ?: return@withContext null
            runCatching { bytes.decodeToImageBitmap() }.getOrNull()
        } ?: return null
        // Resumes on the calling dispatcher (the main thread inside composition).
        if (decoded.size >= MAX_DECODED) decoded.remove(decoded.keys.first())
        decoded[key] = bitmap
        return bitmap
    }

    override fun cached(key: String, sizePx: Int): CachedArtworkImage? {
        if (downloadedSubjectKey(key) == null) return delegate?.cached(key, sizePx)
        return decoded[key]?.let(::CachedArtworkImage)
    }

    override fun prefetch(key: String, sizePx: Int): Job? {
        if (downloadedSubjectKey(key) == null) return delegate?.prefetch(key, sizePx)
        return scope.launch { load(key, sizePx) }
    }

    private fun downloadedSubjectKey(key: String): String? =
        key.takeIf { it.startsWith(DOWNLOADED_KEY_PREFIX) }?.removePrefix(DOWNLOADED_KEY_PREFIX)

    private companion object {
        /** Enough covers for a screen of album rows; each one is a single 600 px decode. */
        const val MAX_DECODED = 24
    }
}

/**
 * The platform loader decorated with downloaded covers, or the loader alone when the feature has
 * no controller on this platform. [controller] is passed explicitly because the caller provides the
 * composition local in the same provider call.
 */
@Composable
fun rememberOnlineArtworkImageLoader(
    controller: OnlineArtworkController?,
    delegate: ArtworkImageLoader?,
): ArtworkImageLoader? {
    if (controller == null) return delegate
    return remember(controller, delegate) { DownloadedArtworkImageLoader(controller, delegate) }
}

/**
 * The cover the album page shows, and the downloaded record behind it when the user applied one.
 *
 * The base artwork stays the local pipeline (embedded picture, then generated placeholder); an
 * applied download replaces its image key and keeps the local one as the stand-in drawn while the
 * stored file decodes.
 */
class AlbumCoverView(val artwork: Artwork, val downloaded: DownloadedArtwork?)

@Composable
fun rememberAlbumCoverView(album: Album): AlbumCoverView {
    val base = album.artwork()
    val controller = LocalOnlineArtwork.current ?: return remember(base) { AlbumCoverView(base, null) }
    val covers by controller.downloaded.state.collectAsState()
    return remember(base, covers, album.title, album.artist) {
        val subject = ArtworkSubject.of(album.artist, album.title)
        val record = subject?.let { covers[it.key] }
        // The local pipeline is authoritative when the user did not choose a cover; an applied one
        // wins over it until removed, which is what "restore the local cover" undoes.
        val source = ArtworkCovers.source(
            hasLocalCover = true,
            hasDownloadedCover = record != null,
            applied = record != null,
        )
        if (source == ArtworkSource.Downloaded && record != null) {
            AlbumCoverView(base.copy(imageKey = downloadedArtworkKey(record.subject), fallbackImageKey = base.imageKey), record)
        } else {
            AlbumCoverView(base, null)
        }
    }
}
