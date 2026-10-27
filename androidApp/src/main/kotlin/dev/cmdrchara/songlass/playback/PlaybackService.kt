package dev.cmdrchara.songlass.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.os.Looper
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
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.LibrarySnapshotUnavailableException
import dev.cmdrchara.songlass.MainActivity
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.R
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.artwork.ArtworkProvider
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.core.browse.BrowseLabels
import dev.cmdrchara.songlass.core.browse.BrowseLayout
import dev.cmdrchara.songlass.core.browse.BrowseNode
import dev.cmdrchara.songlass.core.browse.BrowseTree
import dev.cmdrchara.songlass.core.browse.play
import dev.cmdrchara.songlass.core.browse.resumeForExternalPlayback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private lateinit var forwardingPlayer: QueueForwardingPlayer

    override fun onCreate() {
        super.onCreate()
        graph = (application as SonglassApplication).graph
        buttons = HeadsetButtons(graph.controller, multiPress = { graph.settings.state.value.headsetMultiPress },
            ignoreCommand = { eventUptime ->
                val elapsed = dev.cmdrchara.songlass.core.playback.BluetoothCommandFilter.elapsedKeyTime(eventUptime,
                    android.os.SystemClock.uptimeMillis(), android.os.SystemClock.elapsedRealtime())
                graph.ignoreHeadsetCommand(elapsed)
            }, connectionRevision = { graph.headsetConnectionRevision })
        val audio = graph.player as Media3AudioPlayer
        forwardingPlayer = QueueForwardingPlayer(audio.exoPlayer, graph.controller, audio,
            ignoreTransport = { graph.ignoreBluetoothController(session?.controllerForCurrentRequest?.packageName) })
        // The notification uses the app's own status icon and an artist-album secondary line;
        // artwork, transport actions and the system progress bar stay Media3's defaults.
        setMediaNotificationProvider(SonglassMediaNotificationProvider(this))
        session = MediaLibrarySession.Builder(this, forwardingPlayer, LibraryCallback())
            .setBitmapLoader(EmbeddedArtworkBitmapLoader(this))
            // Without it neither the notification nor the lock-screen card opens the app.
            .setSessionActivity(sessionActivity())
            .build()
        // With crossfade the track that starts becomes the current one on the other ExoPlayer: the
        // session (notification, lock screen, Android Auto) moves to it at that moment.
        scope.launch {
            audio.activePlayer.collect { active ->
                forwardingPlayer.switchPlayer(active)
            }
        }
        // A manually selected track prepares paused until its fade can start. Expose the logical
        // play intent to the session, including cancellations that do not change ExoPlayer state.
        scope.launch {
            audio.state.map { it.status to it.playWhenReady }.distinctUntilChanged().collect {
                forwardingPlayer.refreshPlaybackIntent()
            }
        }
        // UI and remote mode changes share one owner. Refresh even if no decoder event is
        // emitted (e.g. turning repeat on while paused), so connected controllers stay current.
        scope.launch {
            graph.controller.queue.map { it.repeatMode to it.isShuffled }.distinctUntilChanged().collect {
                forwardingPlayer.refreshPlaybackIntent()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    /** Tapping the media notification or its lock-screen card brings the app back to the front. */
    private fun sessionActivity(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        // The player belongs to the application graph, so only the session is released here.
        if (::buttons.isInitialized) buttons.release()
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
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            if (graph.ignoreBluetoothController(controller.packageName)) return ignoredBluetoothRequest()
            val connection = graph.headsetConnectionRevision
            return withPlaybackTree { tree ->
                check(graph.acceptsBluetoothRequest(controller.packageName, connection)) { "Bluetooth command superseded" }
                val item = mediaItems.getOrNull(startIndex) ?: mediaItems.firstOrNull()
                val request = item?.mediaId?.takeIf { it.isNotEmpty() }?.let(tree::resolvePlay)
                    ?: item?.requestMetadata?.searchQuery?.let(tree::resolveQuery)
                    ?: throw IllegalArgumentException("The requested library item is unavailable")
                // The forwarder acknowledges resolved items without reopening native media.
                // Apply an explicit position with the accepted selection, never as a later
                // seek that could affect the old decoder after a rejected library request.
                graph.controller.play(request, startPositionMs.takeIf { it >= 0 })
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex.coerceAtLeast(0), startPositionMs)
            }
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
            if (graph.ignoreBluetoothController(controller.packageName))
                return forwardingPlayer.acknowledgeResumptionResult(ignoredBluetoothRequest())
            if (!isForPlayback) return Futures.immediateFailedFuture(UnsupportedOperationException("No resumption card"))
            val connection = graph.headsetConnectionRevision
            return forwardingPlayer.acknowledgeResumptionResult(withPlaybackTree { tree ->
                check(graph.acceptsBluetoothRequest(controller.packageName, connection)) { "Bluetooth command superseded" }
                graph.controller.resumeForExternalPlayback {
                    tree.resume(graph.history.state.value.recentPlays.firstOrNull())
                }
                playbackResumptionResult(graph.controller)
            })
        }

        override fun onMediaButtonEvent(session: MediaSession, controllerInfo: MediaSession.ControllerInfo, intent: Intent): Boolean =
            buttons.handle(intent)
    }

    private fun ignoredBluetoothRequest(): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
        Futures.immediateFailedFuture(UnsupportedOperationException("Bluetooth command window"))

    /** Runs [block] on the main thread with a tree of the current library, loading it first after a cold start. */
    private fun <T> withTree(block: (BrowseTree) -> T): ListenableFuture<T> = scopeResult(scope) {
        awaitBrowsableLibrary(graph)
        block(buildTree())
    }

    private fun <T> withPlaybackTree(block: (BrowseTree) -> T): ListenableFuture<T> =
        deferredPlaybackRequest(scope, graph.controller, forwardingPlayer,
            awaitReady = { awaitBrowsableLibrary(graph) }, select = { block(buildTree()) })

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
        val from = page.coerceAtLeast(0).toLong() * pageSize
        return if (from >= size) emptyList() else subList(from.toInt(), minOf(size.toLong(), from + pageSize).toInt())
    }
}


/** Same production boundary used by the service and the controllable native integration probe. */
@OptIn(UnstableApi::class)
internal fun <T> deferredPlaybackRequest(
    scope: CoroutineScope,
    controller: PlaybackController,
    forwardingPlayer: QueueForwardingPlayer,
    awaitReady: suspend () -> Unit,
    select: () -> T,
): ListenableFuture<T> {
    check(Looper.myLooper() === forwardingPlayer.applicationLooper) { "Playback requests belong to the player looper" }
    val requestedRevision = controller.beginAsyncPlaybackRequest()
    return scopeResult(scope) {
        awaitReady()
        check(Looper.myLooper() === forwardingPlayer.applicationLooper) { "Playback selection must return to the player looper" }
        check(controller.transportIntentRevision == requestedRevision) { "A newer playback request replaced this one" }
        select()
    }
}

private fun <T> scopeResult(scope: CoroutineScope, block: suspend () -> T): ListenableFuture<T> {
    val future = SettableFuture.create<T>()
    val job = scope.launch {
        try {
            future.set(block())
        } catch (cancelled: CancellationException) {
            future.cancel(false)
            throw cancelled
        } catch (error: Exception) {
            future.setException(error)
        }
    }
    future.addListener({ if (future.isCancelled) job.cancel() }, MoreExecutors.directExecutor())
    // A service can be destroyed before the dispatched coroutine even enters its try block.
    job.invokeOnCompletion { failure ->
        if (failure is CancellationException) future.cancel(false)
        else if (failure != null) future.setException(failure)
    }
    return future
}


/** A successful empty/filtered scan is valid; track count is never a readiness signal. */
internal suspend fun awaitBrowsableLibrary(graph: AppGraph) {
    graph.isRestoringBackup.first { !it }
    graph.awaitLibraryScan()
    if (!graph.source.hasAccess.value || !graph.hasCompleteLibraryScan.value ||
        graph.isLibraryLoading.value || graph.isRestoringBackup.value || graph.libraryRefreshFailure.value != null) {
        throw LibrarySnapshotUnavailableException()
    }
}

/** Native onEvents may still describe the previous item until this main-loop turn ends. */
@OptIn(UnstableApi::class)
internal fun playbackResumptionResult(controller: PlaybackController): MediaSession.MediaItemsWithStartPosition {
    val entry = checkNotNull(controller.queue.value.current?.takeIf { it.isAvailable }) { "No available music" }
    return MediaSession.MediaItemsWithStartPosition(listOf(entry.track.toMediaItem()), 0, C.TIME_UNSET)
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
            // Smart playlists can include a minimum rating: the car sees the same votes as the app.
            ratings = graph.ratings.values.value,
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
                facetTitles = mapOf(
                    LibraryFacetKind.AlbumArtist to getString(R.string.browse_album_artists),
                    LibraryFacetKind.Year to getString(R.string.browse_years),
                    LibraryFacetKind.Composer to getString(R.string.browse_composers),
                ),
                songs = { resources.getQuantityString(R.plurals.browse_songs, it, it) },
            ),
        )
    }
}
