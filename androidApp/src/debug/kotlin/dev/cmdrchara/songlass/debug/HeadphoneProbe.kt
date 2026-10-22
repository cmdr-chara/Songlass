package dev.cmdrchara.songlass.debug

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.MusicSource
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.HeadphoneKind
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.headphoneConnection
import dev.cmdrchara.songlass.playback.HeadsetButtons
import dev.cmdrchara.songlass.playback.HeadsetButtonScheduler
import dev.cmdrchara.songlass.playback.QueueForwardingPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.yield
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Actual Android key/session adapters with an isolated graph. No decoder preparation or user data. */
@androidx.annotation.OptIn(UnstableApi::class)
internal object HeadphoneProbe {
    suspend fun run(context: Context, scope: CoroutineScope) {
        var now = 1000L
        val audio = FakeAudioPlayer { now }
        val tracks = listOf("a", "b", "c").map { Track(it, "Headphone probe $it", "file:///headphone-probe/$it.wav", durationMs = 60000) }
        val source = object : MusicSource {
            override val hasAccess = MutableStateFlow(true)
            override suspend fun loadTracks() = tracks
        }
        val graph = AppGraph(audio, source, InMemoryKeyValueStore(), scope)
        graph.refreshLibrary()
        graph.settings.setResumeWired(true)
        graph.settings.setBluetoothCommandDelay(1000)
        yield()
        graph.controller.playTracks(tracks); yield()
        audio.disconnectOutput()
        graph.onOutputsConnected(setOf(HeadphoneKind.Bluetooth))
        check(!audio.state.value.playWhenReady)
        graph.onOutputsConnected(setOf(HeadphoneKind.Wired))
        check(!audio.state.value.playWhenReady) { "Disabled BT connection did not consume its disconnect" }
        graph.controller.resume(); yield()
        audio.disconnectOutput()
        graph.onOutputsConnected(setOf(HeadphoneKind.Wired))
        check(audio.state.value.playWhenReady)
        yield()
        graph.controller.pause()
        graph.onOutputsConnected(setOf(HeadphoneKind.Bluetooth))
        check(!audio.state.value.playWhenReady) { "A manual pause resumed on connection" }

        val scheduler = object : HeadsetButtonScheduler {
            var pending: Runnable? = null
            override fun post(callback: Runnable, delayMs: Long) { check(delayMs == 400L); pending = callback }
            override fun remove(callback: Runnable) { if (pending === callback) pending = null }
            fun fire() { val job = pending; pending = null; job?.run() }
        }
        val buttons = HeadsetButtons(graph.controller, scheduler, ignoreCommand = graph::ignoreHeadsetCommand,
            connectionRevision = { graph.headsetConnectionRevision })
        fun event(code: Int, at: Long = now, widget: Boolean = false) = Intent(Intent.ACTION_MEDIA_BUTTON)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(at, at, KeyEvent.ACTION_DOWN, code, 0))
            .putExtra(HeadsetButtons.LOCAL_WIDGET, widget)
        val before = graph.controller.queue.value
        val revision = graph.controller.transportIntentRevision
        for (key in listOf(KeyEvent.KEYCODE_HEADSETHOOK, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY,
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP, KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_REWIND)) {
            check(buttons.handle(event(key))) { "Key $key escaped the filter" }
        }
        check(scheduler.pending == null && !audio.state.value.playWhenReady)
        check(graph.controller.queue.value == before && graph.controller.transportIntentRevision == revision)
        check(!buttons.handle(event(KeyEvent.KEYCODE_VOLUME_UP)))
        check(!buttons.handle(event(KeyEvent.KEYCODE_MEDIA_PLAY, widget = true))) { "Widget must reach Media3's ordinary Play path" }
        val delayed = event(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        now += 1100
        check(buttons.handle(delayed) && scheduler.pending == null)
        check(buttons.handle(event(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
        scheduler.fire(); check(audio.state.value.playWhenReady)
        check(buttons.handle(event(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)))
        val stale = checkNotNull(scheduler.pending)
        graph.onOutputsConnected(setOf(HeadphoneKind.Bluetooth))
        now += 1100
        stale.run(); check(audio.state.value.playWhenReady) { "An old gesture survived a new connection" }

        val decoder = ExoPlayer.Builder(context).build()
        decoder.setMediaItem(MediaItem.fromUri("asset:///headphone-probe-never-opened.wav"))
        val facade = object : ForwardingPlayer(decoder) {
            override fun getPlaybackState() = Player.STATE_READY
            override fun getPlayWhenReady() = audio.state.value.playWhenReady
        }
        var origin: String? = "com.android.bluetooth"
        var session: MediaSession? = null
        var remote: MediaController? = null
        var ownControllerObserved = false
        val bridge = QueueForwardingPlayer(facade, graph.controller, audio, ignoreTransport = {
            val actual = session?.controllerForCurrentRequest?.packageName
            if (actual == context.packageName) ownControllerObserved = true
            graph.ignoreBluetoothController(actual ?: origin)
        })
        try {
            graph.onOutputsConnected(setOf(HeadphoneKind.Bluetooth))
            val queue = graph.controller.queue.value
            val generation = audio.eventGeneration
            bridge.pause(); bridge.stop(); bridge.seekToNext(); bridge.seekToPrevious(); bridge.seekTo(500)
            bridge.repeatMode = Player.REPEAT_MODE_ONE; bridge.shuffleModeEnabled = true
            check(audio.state.value.playWhenReady && graph.controller.queue.value == queue && audio.eventGeneration == generation)
            graph.controller.pause(); bridge.refreshPlaybackIntent()
            bridge.play(); check(!audio.state.value.playWhenReady)
            origin = context.packageName
            bridge.play(); check(audio.state.value.playWhenReady)
            bridge.pause(); check(!audio.state.value.playWhenReady)
            session = MediaSession.Builder(context, bridge).setId("headphone-probe-${System.nanoTime()}").build()
            remote = MediaController.Builder(context, checkNotNull(session).token).buildAsync().awaitResult()
            origin = "com.android.bluetooth"
            checkNotNull(remote).play()
            audio.state.first { it.playWhenReady }
            check(ownControllerObserved) { "Real controller origin was not observed" }
            check(decoder.playbackState == Player.STATE_IDLE && !decoder.playWhenReady)
            Log.i("SonglassFeatures", "headphones isolatedGraphReconnect=true independentKinds=true manualPausePreserved=true syntheticKeyEventsFiltered=true lateKeysFiltered=true staleGestureCancelled=true widgetMarkerBypass=true injectedBluetoothControllerFiltered=true actualOwnControllerBypass=true noDecoderPrepared=true")
        } finally {
            remote?.release(); session?.release(); decoder.release(); buttons.release(); audio.release()
        }
        val outputs = context.getSystemService(AudioManager::class.java).getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val personal = outputs.mapNotNull(::headphoneConnection)
        Log.i("SonglassFeatures", "headphones outputSnapshotCount=${outputs.size} personalOutputCount=${personal.size} physicalReconnectNotTested=true")
    }

    fun observe(context: Context) {
        val graph = (context.applicationContext as dev.cmdrchara.songlass.SonglassApplication).graph
        val preferences = graph.settings.state.value.headphones
        val state = graph.player.state.value
        Log.i("SonglassFeatures", "headphone-state playing=${state.playWhenReady} wiredResume=${preferences.resumeWired} bluetoothResume=${preferences.resumeBluetooth} delayMs=${preferences.bluetoothCommandDelayMs} filtering=${graph.ignoreHeadsetCommand()} connectionRevision=${graph.headsetConnectionRevision} disconnectOccurrence=${graph.player.latestDisconnect?.occurrenceId} routeKinds=${state.outputRoute?.devices?.map { it.kind }}")
    }

    private suspend fun <T> ListenableFuture<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addListener({
            try { if (continuation.isActive) continuation.resume(get()) }
            catch (failure: ExecutionException) { if (continuation.isActive) continuation.resumeWithException(failure.cause ?: failure) }
        }, MoreExecutors.directExecutor())
        continuation.invokeOnCancellation { cancel(false) }
    }
}
