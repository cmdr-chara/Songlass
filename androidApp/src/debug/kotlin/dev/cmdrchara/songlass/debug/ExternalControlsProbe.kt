package dev.cmdrchara.songlass.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.LibrarySnapshotUnavailableException
import dev.cmdrchara.songlass.MusicSource
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.playback.HeadsetButtonScheduler
import dev.cmdrchara.songlass.playback.HeadsetButtons
import dev.cmdrchara.songlass.playback.QueueForwardingPlayer
import dev.cmdrchara.songlass.playback.awaitBrowsableLibrary
import dev.cmdrchara.songlass.playback.playbackResumptionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Actual Media3/Android adapters, isolated core graphs and synthetic metadata; no audio file I/O. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object ExternalControlsProbe {
    suspend fun run(context: Context) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            withTimeout(20_000) {
                forwarder(context, scope)
                headset(scope)
                browsing(scope)
            }
            Log.i(TAG, "PASS modes, mutation boundary, single selection, resumption, headset teardown, library readiness")
        } finally {
            scope.cancel()
        }
    }

    private suspend fun forwarder(context: Context, scope: CoroutineScope) {
        val audio = FakeAudioPlayer()
        val core = PlaybackController(audio, scope)
        val decoder = ExoPlayer.Builder(context).build()
        val items = listOf("a", "b", "c").map { id ->
            MediaItem.Builder().setMediaId(id).setUri("asset:///unused-external-probe-$id").build()
        }
        decoder.setMediaItems(items) // Never prepare: this probe checks transport routing, not audio.
        core.playTracks(listOf(track("a"), track("b"), track("c")))
        val bridge = QueueForwardingPlayer(decoder, core, audio)
        var session: MediaSession? = null
        var remote: MediaController? = null
        try {
            check(bridge.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
            for (command in listOf(Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
                Player.COMMAND_SET_PLAYLIST_METADATA, Player.COMMAND_SET_SPEED_AND_PITCH)) {
                check(!bridge.isCommandAvailable(command)) { "Unsafe decoder command still advertised: $command" }
            }
            val before = core.queue.value
            val replacement = MediaItem.Builder().setMediaId("replacement").setUri("asset:///never-open").build()
            bridge.addMediaItem(replacement)
            bridge.moveMediaItem(0, 1)
            bridge.replaceMediaItem(0, replacement)
            bridge.removeMediaItem(0)
            bridge.clearMediaItems()
            bridge.setMediaItems(listOf(replacement, replacement))
            bridge.seekTo(1, 500)
            bridge.playbackParameters = PlaybackParameters(1.5f)
            bridge.playlistMetadata = MediaMetadata.Builder().setTitle("Unowned playlist").build()
            check((0 until decoder.mediaItemCount).map { decoder.getMediaItemAt(it).mediaId } == listOf("a", "b", "c"))
            check(decoder.currentMediaItemIndex == 0 && decoder.playbackParameters.speed == 1f)
            check(core.queue.value == before && audio.state.value.positionMs == 0L)

            val selected = CompletableDeferred<Unit>()
            val resumed = CompletableDeferred<Unit>()
            session = MediaSession.Builder(context, bridge).setId("songlass-external-probe-${System.nanoTime()}")
                .setCallback(object : MediaSession.Callback {
                    override fun onSetMediaItems(
                        mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                        mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
                    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                        check(mediaItems.single().mediaId == "selected")
                        core.playTracks(listOf(track("selected")))
                        selected.complete(Unit)
                        return Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(mediaItems, 0, C.TIME_UNSET))
                    }

                    override fun onPlaybackResumption(
                        mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean,
                    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                        check(isForPlayback)
                        check(bridge.currentMediaItem == null)
                        core.playTracks(listOf(track("resumed")))
                        val result = playbackResumptionResult(core)
                        check(result.mediaItems.single().mediaId == "resumed") {
                            "Resumption must describe the accepted core selection before native onEvents"
                        }
                        resumed.complete(Unit)
                        return Futures.immediateFuture(result)
                    }
                }).build()
            val connected = MediaController.Builder(context, session.token).buildAsync().awaitResult()
            remote = connected
            check(connected.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
            check(!connected.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS))
            connected.repeatMode = Player.REPEAT_MODE_ONE
            core.queue.first { it.repeatMode == RepeatMode.One }
            check(bridge.repeatMode == Player.REPEAT_MODE_ONE && decoder.repeatMode == Player.REPEAT_MODE_OFF)
            connected.shuffleModeEnabled = true
            core.queue.first { it.isShuffled }
            check(bridge.shuffleModeEnabled && !decoder.shuffleModeEnabled)
            core.setRepeatMode(RepeatMode.All)
            core.setShuffle(false)
            bridge.refreshPlaybackIntent()
            connected.awaitState { repeatMode == Player.REPEAT_MODE_ALL && !shuffleModeEnabled }

            // Media3 1.11.1 must still route command 31 through onSetMediaItems even when
            // command 20 is absent. The callback owns core selection; the bridge acknowledges it.
            connected.setMediaItem(MediaItem.Builder().setMediaId("selected").build())
            selected.await()
            check(audio.loadedTrack?.id == "selected")
            core.clearQueue()
            decoder.clearMediaItems()
            connected.awaitState { mediaItemCount == 0 }
            connected.play()
            resumed.await()
            check(audio.loadedTrack?.id == "resumed")
            Log.i(TAG, "forwarder actual MediaController mode routing=true native repeat/shuffle unchanged=true selection/resumption=true")
        } finally {
            remote?.release()
            session?.release()
            decoder.release()
        }
    }

    private fun headset(scope: CoroutineScope) {
        val audio = FakeAudioPlayer()
        val core = PlaybackController(audio, scope)
        val scheduler = GestureScheduler()
        val buttons = HeadsetButtons(core, scheduler)
        fun press(key: Int = KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) = buttons.handle(
            Intent(Intent.ACTION_MEDIA_BUTTON).putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, key)),
        )
        check(!press()) { "An empty queue must leave Play to MediaSession resumption" }
        check(scheduler.pending == null)
        core.playTracks(listOf(track("a"), track("b"), track("c")))
        check(press())
        scheduler.fire()
        check(!audio.state.value.playWhenReady)
        check(press() && press())
        scheduler.fire()
        check(core.queue.value.current?.track?.id == "b" && !audio.state.value.playWhenReady)
        check(press() && press() && press())
        scheduler.fire()
        check(core.queue.value.current?.track?.id == "a")
        check(press())
        check(!press(KeyEvent.KEYCODE_MEDIA_STOP))
        check(scheduler.pending == null) { "A newer explicit key cancels an unresolved toggle" }
        core.resume()
        check(press())
        core.pause()
        scheduler.fire()
        check(!audio.state.value.playWhenReady) { "A delayed headset tap must not undo a newer UI pause" }
        check(press())
        core.playTracks(listOf(track("replacement")))
        val replacementState = audio.state.value
        val replacementGeneration = audio.eventGeneration
        scheduler.fire()
        check(audio.state.value == replacementState && audio.eventGeneration == replacementGeneration) {
            "A delayed gesture must not act on the new collection"
        }
        check(press())
        core.clearQueue()
        val generation = audio.eventGeneration
        scheduler.fire()
        check(audio.eventGeneration == generation)
        core.playTracks(listOf(track("a")))
        check(press())
        val staleCallback = checkNotNull(scheduler.pending)
        val state = audio.state.value
        buttons.release()
        check(scheduler.pending == null)
        staleCallback.run() // Also reject a callback already removed from the Handler queue.
        check(audio.state.value == state && !press())
        Log.i(TAG, "headset empty passthrough=true single/double/triple=true newer UI/key intent cancellation=true teardown=true")
    }

    private suspend fun browsing(scope: CoroutineScope) {
        val source = ProbeSource()
        val graph = AppGraph(FakeAudioPlayer(), source, InMemoryKeyValueStore(), scope)
        awaitBrowsableLibrary(graph)
        awaitBrowsableLibrary(graph)
        check(source.scans == 1 && graph.library.value.tracks.isEmpty())
        source.tracks = listOf(track("filtered").copy(folder = "Hidden"))
        graph.settings.setFolderExcluded("Hidden", true)
        graph.refreshLibrary()
        val scans = source.scans
        check(graph.library.value.tracks.isEmpty())
        awaitBrowsableLibrary(graph)
        check(source.scans == scans) { "A complete filtered library must not rescan on every browse" }

        val scanGate = CompletableDeferred<Unit>()
        source.gate = scanGate
        val refresh = scope.async { graph.refreshLibrary() }
        graph.isLibraryLoading.first { it }
        val waiting = scope.async { awaitBrowsableLibrary(graph) }
        check(!waiting.isCompleted)
        scanGate.complete(Unit)
        refresh.await()
        waiting.await()
        check(source.scans == scans + 1) { "Browsing must join the current scan instead of duplicating it" }
        source.gate = null
        source.fail = true
        graph.refreshLibrary()
        check(graph.allTracks.value.isNotEmpty())
        check(runCatching { awaitBrowsableLibrary(graph) }.exceptionOrNull() is LibrarySnapshotUnavailableException)
        source.hasAccess.value = false
        val deniedScans = source.scans
        check(runCatching { awaitBrowsableLibrary(graph) }.exceptionOrNull() is LibrarySnapshotUnavailableException)
        check(source.scans == deniedScans)
        Log.i(TAG, "browse empty/filtered no-rescan=true in-flight coalesced=true failed/revoked snapshots rejected=true")
    }

    private fun track(id: String) = Track(id = id, title = "Probe $id", uri = "file:///external-probe/$id.wav", durationMs = 60_000)

    private class GestureScheduler : HeadsetButtonScheduler {
        var pending: Runnable? = null
        override fun post(callback: Runnable, delayMs: Long) {
            check(delayMs == 400L)
            pending = callback
        }
        override fun remove(callback: Runnable) { if (pending === callback) pending = null }
        fun fire() { val callback = pending; pending = null; callback?.run() }
    }

    private class ProbeSource : MusicSource {
        override val hasAccess = MutableStateFlow(true)
        var tracks: List<Track> = emptyList()
        var scans = 0
        var fail = false
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun loadTracks(): List<Track> {
            scans++
            gate?.await()
            if (fail) throw IOException("Synthetic scan failure")
            return tracks
        }
    }

    private suspend fun Player.awaitState(ready: Player.() -> Boolean) {
        if (ready()) return
        suspendCancellableCoroutine { continuation ->
            val listener = object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (ready() && continuation.isActive) {
                        removeListener(this)
                        continuation.resume(Unit)
                    }
                }
            }
            addListener(listener)
            continuation.invokeOnCancellation { removeListener(listener) }
            if (ready() && continuation.isActive) {
                removeListener(listener)
                continuation.resume(Unit)
            }
        }
    }

    private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            try { if (continuation.isActive) continuation.resume(get()) }
            catch (failure: ExecutionException) {
                if (continuation.isActive) continuation.resumeWithException(failure.cause ?: failure)
            }
            catch (failure: Exception) { if (continuation.isActive) continuation.resumeWithException(failure) }
        }, MoreExecutors.directExecutor())
        continuation.invokeOnCancellation { cancel(false) }
    }

    private const val TAG = "SonglassExternal"
}
