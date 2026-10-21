package dev.cmdrchara.songlass.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.LibrarySnapshotUnavailableException
import dev.cmdrchara.songlass.MusicSource
import dev.cmdrchara.songlass.core.browse.PlayRequest
import dev.cmdrchara.songlass.core.browse.resumeForExternalPlayback
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.queue.RepeatMode
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.playback.HeadsetButtonScheduler
import dev.cmdrchara.songlass.playback.HeadsetButtons
import dev.cmdrchara.songlass.playback.QueueForwardingPlayer
import dev.cmdrchara.songlass.playback.awaitBrowsableLibrary
import dev.cmdrchara.songlass.playback.deferredPlaybackRequest
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
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
                deferredRequests(context, scope)
                completionScopes(context, scope)
                prepareBoundary(context, scope)
                headset(scope)
                browsing(scope)
            }
            Log.i(TAG, "PASS modes, mutation boundary, retained Stop/resumption, deferred intent/future acknowledgements, safe Prepare boundary, headset teardown, library readiness")
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
            for (command in listOf(Player.COMMAND_PREPARE, Player.COMMAND_CHANGE_MEDIA_ITEMS, Player.COMMAND_SEEK_TO_MEDIA_ITEM,
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
            var resumed = CompletableDeferred<Result<MediaSession.MediaItemsWithStartPosition>>()
            var historyReads = 0
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
                        val outcome = runCatching {
                            core.resumeForExternalPlayback {
                                historyReads++
                                PlayRequest(listOf(track("resumed")), 0, PlayContext.Artist("Earlier history"))
                            }
                            playbackResumptionResult(core)
                        }
                        resumed.complete(outcome)
                        return bridge.acknowledgeResumptionResult(outcome.fold(
                            onSuccess = { Futures.immediateFuture(it) },
                            onFailure = { Futures.immediateFailedFuture(it) },
                        ))
                    }
                }).build()
            val connected = MediaController.Builder(context, session.token).buildAsync().awaitResult()
            remote = connected
            check(connected.isCommandAvailable(Player.COMMAND_SET_MEDIA_ITEM))
            check(!connected.isCommandAvailable(Player.COMMAND_PREPARE))
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

            // This probe has a silent FakeAudioPlayer for transport assertions. Empty the
            // separate, unprepared ExoPlayer timeline after Stop exactly as Media3AudioPlayer
            // does, then let the real MediaSession/MediaController choose its resumption path.
            val retainedContext = PlayContext.UserPlaylist("retained-probe")
            core.playTracks(listOf(track("a"), track("b"), track("c")), context = retainedContext)
            core.setShuffle(true)
            core.addToQueue(listOf(track("temporary")))
            core.skipTo(core.queue.value.entries.single { it.track.id == "temporary" }.id)
            val retained = core.queue.value
            check(retained.current!!.isTemporary && retained.isShuffled && retained.repeatMode == RepeatMode.All)
            connected.stop()
            audio.state.first { it.status == PlayerStatus.Idle }
            check(core.queue.value == retained)
            decoder.stop()
            decoder.clearMediaItems()
            connected.awaitState { mediaItemCount == 0 }
            connected.play()
            val retainedResult = resumed.await().getOrThrow()
            yield() // Let MediaSession finish applying the callback before another command.
            check(retainedResult.mediaItems.single().mediaId == "temporary")
            check(core.queue.value == retained && core.context == retainedContext)
            check(audio.loadedTrack?.id == "temporary" && historyReads == 0)

            // Losing this retained file must request recovery, never replace it with history.
            core.refreshTracks(listOf(track("resumed")))
            connected.stop()
            audio.state.first { it.status == PlayerStatus.Idle }
            resumed = CompletableDeferred()
            connected.play()
            check(resumed.await().exceptionOrNull() is IllegalStateException)
            yield()
            check(historyReads == 0 && core.queue.value.current?.track?.id == "temporary")
            check(core.queue.value.current?.isAvailable == false && audio.loadedTrack == null)

            core.clearQueue()
            decoder.clearMediaItems()
            connected.awaitState { mediaItemCount == 0 }
            resumed = CompletableDeferred()
            connected.play()
            val coldResult = resumed.await().getOrThrow()
            yield()
            check(coldResult.mediaItems.single().mediaId == "resumed") {
                "Resumption must describe the accepted core selection before native onEvents"
            }
            check(audio.loadedTrack?.id == "resumed" && historyReads == 1)
            Log.i(TAG, "forwarder actual MediaController mode routing=true native repeat/shuffle unchanged=true retainedStopQueue=true unavailableFails=true coldHistoryOnce=true")
        } finally {
            remote?.release()
            session?.release()
            decoder.release()
        }
    }

    /** A real MediaController, the production async boundary and an explicitly held library scan. */
    private suspend fun deferredRequests(context: Context, scope: CoroutineScope) {
        val audio = FakeAudioPlayer()
        val source = ProbeSource().apply { tracks = listOf(track("a"), track("b")) }
        val graph = AppGraph(audio, source, InMemoryKeyValueStore(), scope)
        awaitBrowsableLibrary(graph)
        graph.controller.playTracks(source.tracks)
        graph.controller.stop()
        val decoder = ExoPlayer.Builder(context).build() // Empty and never prepared.
        var nativePrepareCalls = 0
        val observed = object : ForwardingPlayer(decoder) {
            override fun prepare() { nativePrepareCalls++ } // Count regressions without opening files.
        }
        val bridge = QueueForwardingPlayer(observed, graph.controller, audio)
        var submitted = CompletableDeferred<ListenableFuture<MediaSession.MediaItemsWithStartPosition>>()
        var frameworkFinished = CompletableDeferred<Unit>()
        var selections = 0
        var historyReads = 0
        var queuedTransportPair: CompletableDeferred<Unit>? = null
        var receivedTransportCommands = 0
        fun request(resumption: Boolean, select: () -> MediaSession.MediaItemsWithStartPosition): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val raw = deferredPlaybackRequest(scope, graph.controller, bridge,
                awaitReady = { awaitBrowsableLibrary(graph) }, select = { selections++; select() })
            val result = if (resumption) bridge.acknowledgeResumptionResult(raw) else raw
            submitted.complete(result)
            val finished = frameworkFinished
            // Only MediaSession receives this outer future. The latch runs after its actual
            // success/failure continuation, not merely after the library/callback result.
            return object : ListenableFuture<MediaSession.MediaItemsWithStartPosition> by result {
                override fun addListener(listener: Runnable, executor: Executor) {
                    result.addListener({ try { listener.run() } finally { finished.complete(Unit) } }, executor)
                }
            }
        }
        val session = MediaSession.Builder(context, bridge).setId("songlass-deferred-probe-${System.nanoTime()}")
            .setCallback(object : MediaSession.Callback {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onPlayerCommandRequest(
                    session: MediaSession, controller: MediaSession.ControllerInfo, playerCommand: Int,
                ): Int {
                    if (playerCommand == Player.COMMAND_PLAY_PAUSE && queuedTransportPair != null &&
                        ++receivedTransportCommands == 2) queuedTransportPair?.complete(Unit)
                    return SessionResult.RESULT_SUCCESS
                }

                override fun onPlaybackResumption(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo, isForPlayback: Boolean,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                    check(isForPlayback)
                    return request(resumption = true) {
                        graph.controller.resumeForExternalPlayback {
                            historyReads++
                            PlayRequest(source.tracks, 0, null)
                        }
                        playbackResumptionResult(graph.controller)
                    }
                }
                override fun onSetMediaItems(
                    mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
                    mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long,
                ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = request(resumption = false) {
                    val chosen = graph.library.value.tracks.single { it.id == mediaItems.single().mediaId }
                    graph.controller.playTracks(listOf(chosen))
                    playbackResumptionResult(graph.controller)
                }
            }).build()
        var remote: MediaController? = null
        try {
            val connected = MediaController.Builder(context, session.token).buildAsync().awaitResult()
            remote = connected
            suspend fun delayed(send: () -> Unit, newerIntent: () -> Unit, cancel: Boolean = false, failScan: Boolean = false) {
                submitted = CompletableDeferred()
                frameworkFinished = CompletableDeferred()
                val gate = CompletableDeferred<Unit>()
                source.gate = gate
                source.fail = failScan
                val scanning = scope.async { graph.refreshLibrary() }
                graph.isLibraryLoading.first { it }
                val beforeSelections = selections
                send()
                val pending = submitted.await()
                yield() // MediaSession has registered its completion before we release the scan.
                newerIntent()
                val state = audio.state.value
                val entries = graph.controller.queue.value.entries.map { it.id to it.track.id }
                val selected = graph.controller.queue.value.current?.id
                if (cancel) withContext(Dispatchers.Default) { pending.cancel(false) }
                gate.complete(Unit)
                scanning.await()
                check(runCatching { pending.awaitResult() }.isFailure)
                frameworkFinished.await()
                yield() // Exit the synchronous framework listener before issuing another command.
                check(selections == beforeSelections && nativePrepareCalls == 0)
                check(audio.state.value == state) { "A stale framework callback changed playback after a newer intent" }
                check(graph.controller.queue.value.entries.map { it.id to it.track.id } == entries)
                check(graph.controller.queue.value.current?.id == selected)
                source.gate = null
                source.fail = false
            }
            suspend fun freshPlay() {
                submitted = CompletableDeferred()
                frameworkFinished = CompletableDeferred()
                connected.play()
                submitted.await().awaitResult()
                frameworkFinished.await()
                yield()
                check(audio.state.value.playWhenReady && nativePrepareCalls == 0) {
                    "A new explicit Play must work after the obsolete continuation finished"
                }
            }

            delayed(connected::play, graph.controller::pause)
            check(historyReads == 0)
            freshPlay()
            graph.controller.stop()
            delayed(connected::play, graph.controller::clearQueue)
            check(historyReads == 0 && graph.controller.queue.value.current == null)
            freshPlay()
            check(historyReads == 1)
            graph.controller.stop()
            delayed({ connected.setMediaItem(MediaItem.Builder().setMediaId("b").build()) }, graph.controller::pause)
            check(graph.controller.queue.value.current?.track?.id == "a")
            delayed(connected::play, { graph.controller.beginAsyncPlaybackRequest() }) // New voice request still awaiting its own result.
            delayed(connected::play, {}, cancel = true)
            check(graph.hasCompleteLibraryScan.value) { "Cancelling one caller must not cancel the process scan" }
            delayed(connected::play, {}, failScan = true)
            check(graph.libraryRefreshFailure.value != null)
            graph.refreshLibrary()
            freshPlay()

            suspend fun queuedPlayAfterSelection(invalidateSelection: Boolean) {
                val beforeId = checkNotNull(graph.controller.queue.value.current).track.id
                val chosenId = if (beforeId == "a") "b" else "a"
                // A nonempty native timeline makes the queued Play call the forwarder directly.
                // It must not take another resumption path that would hide an over-broad scope.
                decoder.setMediaItem(MediaItem.Builder().setMediaId(beforeId).setUri("asset:///never-open-queued").build())
                connected.awaitState { mediaItemCount == 1 }
                submitted = CompletableDeferred()
                frameworkFinished = CompletableDeferred()
                val gate = CompletableDeferred<Unit>()
                source.gate = gate
                val scanning = scope.async { graph.refreshLibrary() }
                graph.isLibraryLoading.first { it }
                val beforeSelections = selections
                connected.setMediaItem(MediaItem.Builder().setMediaId(chosenId).build())
                val pending = submitted.await()
                if (invalidateSelection) graph.controller.pause()
                val received = CompletableDeferred<Unit>()
                queuedTransportPair = received
                receivedTransportCommands = 0
                connected.pause()
                connected.play()
                received.await()
                // Media3's callback runs just before addToCommandQueue. Let that call return
                // before completing the held selection and synchronously flushing both commands.
                yield()
                queuedTransportPair = null
                gate.complete(Unit)
                scanning.await()
                val outcome = runCatching { pending.awaitResult() }
                check(outcome.isFailure == invalidateSelection)
                frameworkFinished.await()
                yield()
                check(selections == beforeSelections + (if (invalidateSelection) 0 else 1))
                check(graph.controller.queue.value.current?.track?.id == (if (invalidateSelection) beforeId else chosenId))
                check(audio.state.value.playWhenReady && nativePrepareCalls == 0) {
                    "A selection completion swallowed the subsequent queued user Play"
                }
                source.gate = null
            }
            queuedPlayAfterSelection(invalidateSelection = false)
            queuedPlayAfterSelection(invalidateSelection = true)
            Log.i(TAG, "deferred real MediaController stalePause/clear/selection/newPending/cancel/scanFailure=true queuedPlayAfterSelectionSuccess/failure=true noFrameworkPrepare=true freshPlayWorks=true")
        } finally {
            source.gate?.complete(Unit)
            remote?.release()
            session.release()
            decoder.release()
        }
    }

    /** Exercise the actual future adapter's scopes; transport remains fake and no file is opened. */
    private suspend fun completionScopes(context: Context, scope: CoroutineScope) {
        val audio = FakeAudioPlayer()
        val core = PlaybackController(audio, scope)
        core.playTracks(listOf(track("scope")))
        core.seekTo(12_345)
        core.pause()
        val decoder = ExoPlayer.Builder(context).build()
        decoder.setMediaItem(MediaItem.Builder().setMediaId("scope").setUri("asset:///never-open-scope").build())
        var nativePrepareCalls = 0
        val bridge = QueueForwardingPlayer(object : ForwardingPlayer(decoder) {
            override fun prepare() { nativePrepareCalls++ }
        }, core, audio)
        try {
            check(!bridge.isCommandAvailable(Player.COMMAND_PREPARE))
            check(bridge.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION))
            fun frameworkTransport() {
                bridge.prepare()
                bridge.seekToDefaultPosition()
                bridge.play()
            }
            fun checkPreserved(revision: Long) {
                check(!audio.state.value.playWhenReady && audio.state.value.positionMs == 12_345L)
                check(core.transportIntentRevision == revision && nativePrepareCalls == 0)
            }
            val direct = MoreExecutors.directExecutor()
            fun checkResult(result: ListenableFuture<Unit>) {
                val revision = core.transportIntentRevision
                Futures.addCallback(bridge.acknowledgeResumptionResult(result), object : FutureCallback<Unit> {
                    override fun onSuccess(result: Unit?) { frameworkTransport() }
                    override fun onFailure(t: Throwable) { frameworkTransport() }
                }, direct)
                checkPreserved(revision)
            }
            checkResult(Futures.immediateFuture(Unit))
            checkResult(Futures.immediateFailedFuture(IOException("Synthetic callback failure")))
            checkResult(SettableFuture.create<Unit>().apply { cancel(false) })

            val revision = core.transportIntentRevision
            bridge.acknowledgeResumptionResult(Futures.immediateFuture(Unit)).addListener({
                frameworkTransport()
                bridge.acknowledgeResumptionResult(Futures.immediateFuture(Unit)).addListener({ frameworkTransport() }, direct)
                frameworkTransport() // Inner completion must not clear the outer scope.
            }, direct)
            checkPreserved(revision)

            var queued: Runnable? = null
            val failure = IllegalStateException("Synthetic listener failure")
            bridge.acknowledgeResumptionResult(Futures.immediateFuture(Unit))
                .addListener({ frameworkTransport(); throw failure }, Executor { queued = it })
            check(runCatching { checkNotNull(queued).run() }.exceptionOrNull() === failure)
            checkPreserved(revision)

            val pending = SettableFuture.create<Unit>()
            val finished = CompletableDeferred<Unit>()
            bridge.acknowledgeResumptionResult(pending).addListener({ frameworkTransport(); finished.complete(Unit) }, direct)
            withContext(Dispatchers.Default) { pending.cancel(false) }
            finished.await()
            yield()
            checkPreserved(revision)
            bridge.play()
            check(audio.state.value.playWhenReady) { "A completed listener scope swallowed a later explicit Play" }
            Log.i(TAG, "future scopes alreadyDone/failure/cancel/nested/queuedException/offMainCancel=true prepare/seek/playAcknowledged=true laterPlayWorks=true")
        } finally {
            decoder.release()
        }
    }

    /** A retained native URI/error is never prepared around core's current-library check. */
    private fun prepareBoundary(context: Context, scope: CoroutineScope) {
        val audio = FakeAudioPlayer()
        val core = PlaybackController(audio, scope)
        val retained = track("retained-error")
        core.playTracks(listOf(retained))
        core.pause()
        audio.fail("Synthetic retained decoder error")
        core.refreshTracks(emptyList())
        val decoder = ExoPlayer.Builder(context).build()
        decoder.setMediaItem(MediaItem.Builder().setMediaId(retained.id).setUri("asset:///never-reopen-stale-uri").build())
        var nativePrepareCalls = 0
        val bridge = QueueForwardingPlayer(object : ForwardingPlayer(decoder) {
            override fun getPlayerError() = PlaybackException(
                "Synthetic retained native error", null, PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            )
            override fun prepare() { nativePrepareCalls++ }
        }, core, audio)
        try {
            check(!bridge.isCommandAvailable(Player.COMMAND_PREPARE))
            val state = audio.state.value
            val generation = audio.eventGeneration
            bridge.prepare() // Direct invocation must remain harmless even if a caller ignores commands.
            check(nativePrepareCalls == 0 && audio.state.value == state && audio.eventGeneration == generation)
            check(decoder.currentMediaItem?.localConfiguration?.uri.toString() == "asset:///never-reopen-stale-uri")
            bridge.play() // Idle/Error must consult core and reject the unavailable retained entry.
            check(audio.loadedTrack == null && !audio.state.value.playWhenReady && nativePrepareCalls == 0)
            core.refreshTracks(listOf(retained))
            bridge.play()
            check(audio.loadedTrack == retained && audio.state.value.playWhenReady && nativePrepareCalls == 0) {
                "Removing native Prepare must preserve a later validated Play through core"
            }
            Log.i(TAG, "prepare unadvertised/directStaleErrorNoNativeCall=true unavailablePlayRejected=true validatedCoreRetry=true")
        } finally {
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
