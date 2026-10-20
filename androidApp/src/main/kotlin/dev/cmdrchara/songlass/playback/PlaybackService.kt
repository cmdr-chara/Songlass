package dev.cmdrchara.songlass.playback

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.R
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.artwork.ArtworkProvider
import dev.cmdrchara.songlass.core.browse.BrowseLabels
import dev.cmdrchara.songlass.core.browse.BrowseLayout
import dev.cmdrchara.songlass.core.browse.BrowseNode
import dev.cmdrchara.songlass.core.browse.BrowseTree
import dev.cmdrchara.songlass.core.browse.PlayRequest
import dev.cmdrchara.songlass.core.browse.play
import dev.cmdrchara.songlass.core.playback.PlaybackController
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts the media session, so playback continues in the background with system controls, and
 * the media library that Android Auto (and other media browsers) browse and search.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null
    private val scope = MainScope()
    private lateinit var graph: AppGraph
    private lateinit var buttons: HeadsetButtons

    override fun onCreate() {
        super.onCreate()
        graph = (application as SonglassApplication).graph
        buttons = HeadsetButtons(graph.controller)
        val audio = graph.player as Media3AudioPlayer
        val forwardingPlayer = QueueForwardingPlayer(audio.exoPlayer, graph.controller, audio)
        session = MediaLibrarySession.Builder(this, forwardingPlayer, LibraryCallback())
            .setBitmapLoader(EmbeddedArtworkBitmapLoader(this))
            .build()
        // With crossfade the track that starts becomes the current one on the other ExoPlayer: the
        // session (notification, lock screen, Android Auto) moves to it at that moment.
        scope.launch {
            audio.activePlayer.collect { active ->
                forwardingPlayer.switchPlayer(active)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        // The player belongs to the application graph, so only the session is released here.
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            // Covers are served by our provider; the browser may read them, nothing else.
            if (browser.packageName != packageName) {
                grantUriPermission(browser.packageName, ArtworkProvider.baseUri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
            }
            val root = MediaItem.Builder()
                .setMediaId(BrowseTree.ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                        .build(),
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = withTree { tree ->
            val nodes = tree.children(parentId) ?: return@withTree LibraryResult.ofError(SessionError.ERROR_BAD_VALUE)
            LibraryResult.ofItemList(nodes.page(page, pageSize).map(::toMediaItem), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> = withTree { tree ->
            session.notifySearchResultChanged(browser, query, tree.search(query).size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = withTree { tree ->
            LibraryResult.ofItemList(tree.search(query).page(page, pageSize).map(::toMediaItem), params)
        }

        /**
         * A browser chose something to play (or asked by voice, "play …"). The queue is core's,
         * so the choice goes to PlaybackController; the player then ignores the items it is
         * handed (see QueueForwardingPlayer).
         */
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = withTree { tree ->
            val item = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
            val request = item?.mediaId?.takeIf { it.isNotEmpty() }?.let(tree::resolvePlay)
                ?: item?.requestMetadata?.searchQuery?.let(tree::resolveQuery)
            request?.let(::play)
            MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex.coerceAtLeast(0), startPositionMs)
        }

        /**
         * Play pressed with nothing loaded, e.g. after Android closed the app (car, Bluetooth,
         * widget): continue the last collection. Only for playback; no resumption card.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (!isForPlayback) return Futures.immediateFailedFuture(UnsupportedOperationException("No resumption card"))
            return withTree { tree ->
                val request = tree.resume(graph.history.state.value.recentPlays.firstOrNull()) ?: throw IllegalStateException("No music")
                play(request)
                val current = mediaSession.player.currentMediaItem
                MediaSession.MediaItemsWithStartPosition(listOfNotNull(current), 0, C.TIME_UNSET)
            }
        }

        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean =
            buttons.handle(intent)
    }

    private fun play(request: PlayRequest) = graph.controller.play(request)

    /** Runs [block] on the main thread with a tree of the current library, loading it first after a cold start. */
    private fun <T> withTree(block: (BrowseTree) -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        scope.launch {
            try {
                if (graph.library.value.tracks.isEmpty()) graph.refreshLibrary()
                future.set(block(buildTree()))
            } catch (error: Exception) {
                future.setException(error)
            }
        }
        return future
    }

    private fun buildTree(): BrowseTree = browseTree(this, graph)

    private fun toMediaItem(node: BrowseNode): MediaItem {
        val extras = Bundle().apply {
            // Hint for the car screen: how to lay out this node's children.
            val style = if (node.childLayout == BrowseLayout.Grid) MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM else MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, style)
            putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM)
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(node.title)
            .setArtist(node.subtitle)
            .setSubtitle(node.subtitle)
            .setIsBrowsable(node.browsable)
            .setIsPlayable(node.playable)
            .setMediaType(if (node.browsable) MediaMetadata.MEDIA_TYPE_FOLDER_MIXED else MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(node.artworkTrack?.let { ArtworkProvider.uriFor(it.id, it.artworkRevision) })
            .setExtras(extras)
            .build()
        return MediaItem.Builder().setMediaId(node.id).setMediaMetadata(metadata).build()
    }

    private fun <T> List<T>.page(page: Int, pageSize: Int): List<T> {
        if (pageSize <= 0 || pageSize == Int.MAX_VALUE) return this
        val from = page.coerceAtLeast(0) * pageSize
        return if (from >= size) emptyList() else subList(from, minOf(size, from + pageSize))
    }
}


/** The tree media browsers and voice requests see, with labels from [context]'s resources. */
internal fun browseTree(context: android.content.Context, graph: AppGraph): BrowseTree {
    val library = graph.library.value
    with(context) {
        return BrowseTree(
            library = library,
            favorites = graph.favorites.tracksIn(library),
            userPlaylists = graph.userPlaylists.playlists.value,
            smartPlaylists = graph.smartPlaylists.playlists.value,
            history = graph.history.state.value,
            nowMs = kotlin.time.Clock.System.now().toEpochMilliseconds(),
            recent = graph.history.state.value.recentContexts.map { it.context },
            labels = BrowseLabels(
                home = getString(R.string.browse_home),
                playlists = getString(R.string.browse_playlists),
                albums = getString(R.string.browse_albums),
                artists = getString(R.string.browse_artists),
                favorites = getString(R.string.browse_favorites),
                shuffleAll = getString(R.string.browse_shuffle_all),
                unknownArtist = getString(R.string.browse_unknown_artist),
                unknownAlbum = getString(R.string.browse_unknown_album),
                songs = { resources.getQuantityString(R.plurals.browse_songs, it, it) },
            ),
        )
    }
}

/**
 * The single button of headphones and headsets: one press plays or pauses, two skip to the next
 * song, three go back. Presses within [TAP_WINDOW_MS] of each other count as one gesture.
 * Other media keys (next, previous, play, pause from widgets or cars) keep their default meaning.
 */
private class HeadsetButtons(private val controller: PlaybackController) {
    private val handler = Handler(Looper.getMainLooper())
    private var presses = 0
    private val resolve = Runnable {
        when (presses) {
            1 -> controller.togglePlayPause()
            2 -> controller.skipToNext()
            else -> controller.skipToPrevious()
        }
        presses = 0
    }

    fun handle(intent: Intent): Boolean {
        val event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return false
        if (event.keyCode != KeyEvent.KEYCODE_HEADSETHOOK && event.keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            presses++
            handler.removeCallbacks(resolve)
            handler.postDelayed(resolve, TAP_WINDOW_MS)
        }
        return true
    }

    private companion object {
        const val TAP_WINDOW_MS = 400L
    }
}
