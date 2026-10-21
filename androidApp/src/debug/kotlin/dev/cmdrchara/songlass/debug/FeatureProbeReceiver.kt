package dev.cmdrchara.songlass.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import dev.cmdrchara.songlass.*
import dev.cmdrchara.songlass.core.home.HomeSection
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.player.CrossfadeKind
import dev.cmdrchara.songlass.core.player.CrossfadeSkipReason
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

/** Debug-only, isolated audio/storage checks. Never changes the user's graph or preferences. */
@androidx.annotation.OptIn(UnstableApi::class)
class FeatureProbeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch {
            try {
                withTimeout(35_000) {
                    when (val probe = intent.getStringExtra("case")) {
                        "audio" -> audio(context, scope)
                        "audio-manual" -> audio(context, scope, manualOnly = true)
                        "audio-manual-resume" -> audio(context, scope, manualResumeOnly = true)
                        "audio-manual-focus" -> audio(context, scope, manualFocusOnly = true)
                        "audio-overlap-focus" -> audio(context, scope, overlapFocusKind = CrossfadeKind.Automatic)
                        "audio-manual-overlap-focus" -> audio(context, scope, overlapFocusKind = CrossfadeKind.Manual)
                        null, "backup" -> backup(context, scope)
                        else -> error("Unknown feature probe: $probe")
                    }
                }
                Log.i("SonglassFeatures", "PASS " + (intent.getStringExtra("case") ?: "backup"))
            } catch (failure: Throwable) {
                Log.e("SonglassFeatures", "FAIL " + (intent.getStringExtra("case") ?: "backup"), failure)
            } finally { pending.finish(); scope.cancel() }
        }
    }

    private suspend fun backup(context: Context, scope: CoroutineScope) {
        val store = SharedPreferencesStore(context, "songlass-feature-probe")
        store.replace(store.snapshot().keys, emptyMap())
        val old = Track("fixture-old", "Fixture", "content://fixture/old", artist = "Probe", album = "Probe", durationMs = 6_000,
            folder = "Probe", fileName = "fixture.wav")
        val source = ProbeSource(listOf(old))
        val graph = AppGraph(FakeAudioPlayer(), source, store, scope)
        try {
            graph.refreshLibrary()
            graph.favorites.toggle(old.id)
            graph.history.recordPlay(old.id, null)
            graph.userPlaylists.create("Probe playlist", listOf(old.id))
            graph.settings.setCrossfadeDuration(6_000)
            graph.homeCustomization.setSectionVisible(HomeSection.RecentlyAdded, false)
            val archive = graph.exportBackup()
            val unchanged = store.snapshot()
            check(runCatching { graph.previewBackup(archive.text.dropLast(4)) }.isFailure)
            check(store.snapshot() == unchanged) { "Invalid backup mutated the store" }
            graph.favorites.toggle(old.id)
            graph.settings.setCrossfadeDuration(0)
            graph.homeCustomization.reset()
            source.tracks = listOf(old.copy(id = "fixture-new", uri = "content://fixture/new"))
            graph.refreshLibrary()
            check(graph.previewBackup(archive.text).tracks.remapped == 1)
            graph.restoreBackup(archive.text, graph.previewBackup(archive.text))
            check(graph.favorites.ids.value == listOf("fixture-new"))
            check(graph.settings.state.value.crossfadeDurationMs == 6_000L)
            check(HomeSection.RecentlyAdded in graph.homeCustomization.state.value.hidden)
            check(graph.history.state.value.playCounts["fixture-new"] == 1)
            check(graph.userPlaylists.playlists.value.single().trackIds == listOf("fixture-new"))
            check(SharedPreferencesStore(context, "songlass-feature-probe").getString("favorites") == "fixture-new")
            Log.i("SonglassFeatures", "backup nativeCommit=true invalidPreserved=true remapped=1 liveReload=true")
        } finally { store.replace(store.snapshot().keys, emptyMap()) }
    }

    private suspend fun audio(context: Context, scope: CoroutineScope, manualOnly: Boolean = false,
        manualFocusOnly: Boolean = false, manualResumeOnly: Boolean = false, overlapFocusKind: CrossfadeKind? = null) {
        val file = File(context.cacheDir, "songlass-crossfade-probe.wav")
        withContext(Dispatchers.IO) { tone(file) }
        val voices = mutableListOf<PlayerVoice>()
        val prepareGate = if (manualFocusOnly) CountDownLatch(1) else null
        val prepareWaiting = AtomicBoolean(false)
        fun voice(focus: Boolean): PlayerVoice {
            val eq = EqualizerProcessor()
            val renderers = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                    DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).setAudioProcessors(arrayOf(eq)).build()
            }
            val builder = ExoPlayer.Builder(context, renderers).setAudioAttributes(PlaybackAudioAttributes, focus)
                .setHandleAudioBecomingNoisy(true)
            if (!focus && prepareGate != null) {
                val upstream = DefaultDataSource.Factory(context)
                val gated = DataSource.Factory {
                    val source = upstream.createDataSource()
                    object : DataSource by source {
                        override fun open(dataSpec: DataSpec): Long {
                            // Only the loader thread waits. The app loop can receive real focus loss
                            // while the incoming player is deterministically still preparing.
                            prepareWaiting.set(true)
                            try {
                                if (!prepareGate.await(10, TimeUnit.SECONDS)) throw IOException("Probe preparation gate timed out")
                            } catch (interrupted: InterruptedException) {
                                Thread.currentThread().interrupt()
                                throw IOException("Probe preparation interrupted", interrupted)
                            }
                            return source.open(dataSpec)
                        }
                    }
                }
                builder.setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(gated))
            }
            return PlayerVoice(builder.build(), eq, focus).also { voices += it }
        }
        val first = voice(true)
        val audio = Media3AudioPlayer(first.player, scope, first.equalizer) { voice(false) }
        val tracks = listOf(Track("tone-1", "Probe one", file.toURI().toString(), durationMs = 6_000),
            Track("tone-2", "Probe two", file.toURI().toString(), durationMs = 6_000))
        val graph = AppGraph(audio, ProbeSource(tracks), InMemoryKeyValueStore(), scope)
        val session = QueueForwardingPlayer(audio.exoPlayer, graph.controller, audio)
        val follow = scope.launch { audio.activePlayer.collect { session.switchPlayer(it) } }
        val intent = scope.launch {
            audio.state.map { it.status to it.playWhenReady }.distinctUntilChanged()
                .collect { session.refreshPlaybackIntent() }
        }
        try {
            if (overlapFocusKind != null) {
                overlapFocusAudio(context, graph, audio, session, voices, tracks, overlapFocusKind)
                return
            }
            if (manualFocusOnly) {
                manualFocusAudio(context, graph, audio, session, voices, tracks, checkNotNull(prepareGate), prepareWaiting)
                return
            }
            if (manualOnly) {
                manualAudio(graph, audio, session, voices, tracks + tracks[0].copy(id = "tone-3", title = "Probe three"))
                return
            }
            if (manualResumeOnly) {
                manualResumeAudio(graph, audio, session, voices, tracks)
                return
            }
            graph.controller.playTracks(tracks)
            await { audio.state.value.isPlaying }
            check(!audio.diagnostics().secondPlayerCreated)
            graph.controller.seekTo(5_650)
            await { graph.controller.queue.value.currentIndex == 1 }
            check(audio.diagnostics().overlapsStarted == 0)
            Log.i("SonglassFeatures", "audio offGapless=true oneDecoder=true")
            graph.settings.setCrossfadeDuration(2_000)
            delay(60)
            graph.controller.playTracks(tracks)
            await { audio.diagnostics().standbyReady && audio.state.value.isPlaying }
            graph.controller.seekTo(3_750)
            await { audio.diagnostics().stage == CrossfadeStage.Fading }
            delay(400)
            val held = audio.diagnostics()
            check(voices.count { it.player.isPlaying } == 2) { "Both decoders must play during overlap" }
            check(held.outgoingVolume > 0 && held.incomingVolume > 0)
            check(held.outgoingVolume + held.incomingVolume <= 1.01f)
            check(graph.controller.queue.value.currentIndex == 1)
            session.pause()
            delay(100)
            check(voices.all { !it.player.playWhenReady }) { "System pause left a decoder playing" }
            val paused = audio.diagnostics().progress
            delay(100)
            check(kotlin.math.abs(audio.diagnostics().progress - paused) < 0.03f)
            session.play()
            await { audio.diagnostics().overlapsCompleted == 1 }
            check(graph.controller.queue.value.currentIndex == 1)
            check(audio.diagnostics().overlapsStarted == 1)
            check(session.currentMediaItem?.mediaId == "tone-2")
            Log.i("SonglassFeatures", "audio overlap=true boundedGains=true systemPause=true resumed=true queueAdvanceOnce=true sessionFollows=true")
            graph.controller.playTracks(tracks)
            await { audio.diagnostics().standbyReady && audio.state.value.isPlaying }
            graph.controller.seekTo(3_750)
            await { audio.diagnostics().stage == CrossfadeStage.Fading }
            session.seekTo(1_000)
            delay(150)
            check(audio.diagnostics().stage != CrossfadeStage.Fading)
            check(voices.count { it.player.isPlaying } <= 1)
            session.stop()
            delay(100)
            check(voices.none { it.player.isPlaying || it.player.playWhenReady })
            check(graph.controller.queue.value.entries.size == 2)
            Log.i("SonglassFeatures", "audio seekCancelsTail=true stopBoth=true queuePreserved=true")
        } finally {
            follow.cancel()
            intent.cancel()
            prepareGate?.countDown()
            audio.release()
            withContext(Dispatchers.IO) { file.delete() }
        }
    }

    /** Transient focus suppression after both real decoders started, not while preparing. */
    private suspend fun overlapFocusAudio(
        context: Context,
        graph: AppGraph,
        audio: Media3AudioPlayer,
        session: QueueForwardingPlayer,
        voices: List<PlayerVoice>,
        tracks: List<Track>,
        kind: CrossfadeKind,
    ) {
        val automatic = kind == CrossfadeKind.Automatic
        graph.settings.setCrossfadeDuration(if (automatic) 2_000 else 0)
        graph.settings.setManualCrossfadeDuration(if (automatic) 0 else 2_000)
        await {
            audio.diagnostics().configuredMs == (if (automatic) 2_000L else 0L) &&
                audio.diagnostics().manualConfiguredMs == (if (automatic) 0L else 2_000L)
        }
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying && (!automatic || audio.diagnostics().standbyReady) }
        val outgoing = audio.exoPlayer
        var transientSuppressionObserved = false
        val listener = object : Player.Listener {
            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                    transientSuppressionObserved = true
                }
            }
        }
        outgoing.addListener(listener)
        val focusChanges = mutableListOf<Int>()
        val manager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { focusChanges += it }
            .build()
        var focusHeld = false
        try {
            if (automatic) graph.controller.seekTo(4_050) else graph.controller.skipToNext()
            await { audio.diagnostics().stage == CrossfadeStage.Fading && voices.count { it.player.isPlaying } == 2 }
            check(!audio.isPreparingManualTransition && audio.exoPlayer !== outgoing)
            check(audio.state.value.crossfadeStatus?.kind == kind)
            val incoming = audio.exoPlayer
            val entry = checkNotNull(graph.controller.queue.value.current)
            check(entry.track.id == tracks[1].id)
            check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Probe transient focus was denied" }
            focusHeld = true
            await {
                transientSuppressionObserved && audio.diagnostics().stage != CrossfadeStage.Fading &&
                    voices.none { it.player.playWhenReady || it.player.isPlaying }
            }
            check(audio.state.value.crossfadeStatus?.skipReason == CrossfadeSkipReason.Interrupted)
            check(audio.exoPlayer === incoming && graph.controller.queue.value.current?.id == entry.id)
            check(!session.playWhenReady)
            delay(100)
            check(focusChanges.none { it < 0 }) { "Interrupted overlap reclaimed focus from the transient owner" }
            manager.abandonAudioFocusRequest(request)
            focusHeld = false
            // Beyond the old preparation timeout: no focus-return, tick or posted READY work
            // may revive either voice or advance the now-paused current queue occurrence.
            delay(2_100)
            check(voices.none { it.player.playWhenReady || it.player.isPlaying })
            check(audio.exoPlayer === incoming && graph.controller.queue.value.current?.id == entry.id)
            session.play()
            await { incoming.isPlaying }
            check(voices.count { it.player.isPlaying } == 1)
            check(graph.controller.queue.value.current?.id == entry.id)
            Log.i("SonglassFeatures", "overlap kind=$kind transientFocusObserved=true bothPaused=true focusNotReclaimed=true noAutomaticResume=true explicitPlayWorks=true")
        } finally {
            if (focusHeld) manager.abandonAudioFocusRequest(request)
            outgoing.removeListener(listener)
        }
    }

    private suspend fun manualFocusAudio(
        context: Context,
        graph: AppGraph,
        audio: Media3AudioPlayer,
        session: QueueForwardingPlayer,
        voices: List<PlayerVoice>,
        tracks: List<Track>,
        prepareGate: CountDownLatch,
        prepareWaiting: AtomicBoolean,
    ) {
        graph.settings.setManualCrossfadeDuration(1_250)
        await { audio.diagnostics().manualConfiguredMs == 1_250L }
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        val outgoing = audio.exoPlayer
        var transientSuppressionObserved = false
        val listener = object : Player.Listener {
            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                if (playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS) {
                    transientSuppressionObserved = true
                }
            }
        }
        outgoing.addListener(listener)
        val focusChanges = mutableListOf<Int>()
        val manager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { focusChanges += it }
            .build()
        var focusHeld = false
        try {
            graph.controller.skipToNext()
            await { prepareWaiting.get() }
            check(audio.isPreparingManualTransition && session.playWhenReady)
            check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { "Probe transient focus was denied" }
            focusHeld = true
            await { !audio.isPreparingManualTransition }
            check(transientSuppressionObserved) { "Probe did not observe real transient focus suppression" }
            check(voices.none { it.player.playWhenReady || it.player.isPlaying })
            check(!session.playWhenReady && graph.controller.queue.value.currentIndex == 1)
            check(audio.state.value.crossfadeStatus?.skipReason == CrossfadeSkipReason.Interrupted)
            check(audio.diagnostics().overlapsStarted == 0)
            prepareGate.countDown()
            await { audio.exoPlayer.playbackState == Player.STATE_READY }
            delay(100)
            check(focusChanges.none { it < 0 }) { "Preparing target reclaimed focus from the transient owner" }
            manager.abandonAudioFocusRequest(request)
            focusHeld = false
            delay(2_100)
            check(voices.none { it.player.playWhenReady || it.player.isPlaying }) { "Focus return or stale timeout resumed the target" }
            check(audio.exoPlayer.currentPosition == 0L && !session.playWhenReady)
            session.play()
            await { audio.exoPlayer.isPlaying }
            check(graph.controller.queue.value.currentIndex == 1 && voices.count { it.player.isPlaying } == 1)
            Log.i("SonglassFeatures", "manual transientFocusObserved=true preparationCancelledInPause=true focusNotReclaimed=true noAutomaticResume=true explicitPlayWorks=true")
        } finally {
            prepareGate.countDown()
            if (focusHeld) manager.abandonAudioFocusRequest(request)
            outgoing.removeListener(listener)
        }
    }

    /** A prepared automatic standby must remain a manual fade when opened at a bookmark. */
    private suspend fun manualResumeAudio(
        graph: AppGraph,
        audio: Media3AudioPlayer,
        session: QueueForwardingPlayer,
        voices: List<PlayerVoice>,
        tracks: List<Track>,
    ) {
        graph.settings.setCrossfadeDuration(2_000)
        graph.settings.setManualCrossfadeDuration(1_250)
        await { audio.diagnostics().configuredMs == 2_000L && audio.diagnostics().manualConfiguredMs == 1_250L }
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying && audio.diagnostics().standbyReady }
        val outgoing = audio.exoPlayer
        val prepared = voices.single { it.player !== outgoing }.player
        val before = graph.controller.queue.value.entries.map { it.id }
        val bookmarkPosition = 1_500L

        graph.controller.playBookmark(tracks[1], bookmarkPosition)
        check(audio.exoPlayer === prepared && voices.size == 2) { "Bookmark did not reuse the prepared voice" }
        check(graph.controller.queue.value.currentIndex == 1 && graph.controller.queue.value.entries.map { it.id } == before)
        // ExoPlayer delivers SEEK synchronously. A faulty initial seek cancels manualPreparing,
        // drops the outgoing tail and is then overwritten by the preparation's paused intent.
        check(audio.isPreparingManualTransition && audio.state.value.playWhenReady) { "Initial bookmark seek cancelled the transition" }
        check(!prepared.playWhenReady && prepared.currentPosition == bookmarkPosition) { "Prepared bookmark consumed audio before the fade" }
        check(session.playWhenReady && session.playbackState == Player.STATE_BUFFERING)
        await { audio.diagnostics().stage == CrossfadeStage.Fading && prepared.isPlaying }
        check(voices.count { it.player.isPlaying } == 2)
        check(audio.state.value.crossfadeStatus?.kind == CrossfadeKind.Manual)
        check(audio.state.value.crossfadeStatus?.effectiveDurationMs == 1_250L)
        check(prepared.currentPosition >= bookmarkPosition)

        // A subsequent user seek must still cancel the tail; the preparation fix is narrowly scoped.
        session.seekTo(2_000)
        await { prepared.isPlaying && audio.diagnostics().stage != CrossfadeStage.Fading }
        check(!audio.isPreparingManualTransition && voices.count { it.player.isPlaying } == 1)
        check(graph.controller.queue.value.currentIndex == 1 && prepared.currentPosition >= 2_000)
        Log.i("SonglassFeatures", "manual reusedStandby=true bookmarkOffset=1500 pendingIntent=true noSilentPreroll=true overlap=true userSeekCancelsTail=true queueUnchanged=true")
    }

    private suspend fun manualAudio(
        graph: AppGraph,
        audio: Media3AudioPlayer,
        session: QueueForwardingPlayer,
        voices: List<PlayerVoice>,
        tracks: List<Track>,
    ) {
        val controller = graph.controller
        graph.settings.setManualCrossfadeDuration(1_250)
        await { audio.diagnostics().manualConfiguredMs == 1_250L }
        controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        check(!audio.diagnostics().secondPlayerCreated) { "Manual-only setting eagerly allocated a standby" }
        session.seekToNextMediaItem()
        check(controller.queue.value.currentIndex == 1)
        check(audio.isPreparingManualTransition)
        check(!audio.exoPlayer.playWhenReady && audio.exoPlayer.currentPosition == 0L) { "Incoming intro advanced during preparation" }
        check(session.playWhenReady && session.playbackState == Player.STATE_BUFFERING) { "Session lost pending playback intent" }
        await { audio.diagnostics().stage == CrossfadeStage.Fading }
        val applied = audio.state.value.crossfadeStatus!!
        check(applied.kind == CrossfadeKind.Manual && applied.requestedDurationMs == 1_250L && applied.effectiveDurationMs == 1_250L)
        delay(250)
        check(voices.count { it.player.isPlaying } == 2)
        val gains = audio.diagnostics()
        check(gains.outgoingVolume > 0 && gains.incomingVolume > 0 && gains.outgoingVolume + gains.incomingVolume <= 1.01f)
        session.pause()
        check(voices.none { it.player.playWhenReady })
        val held = audio.diagnostics().progress
        delay(100)
        check(kotlin.math.abs(audio.diagnostics().progress - held) < .03f)
        session.play()
        await { audio.diagnostics().overlapsCompleted == 1 }
        check(controller.queue.value.currentIndex == 1 && session.currentMediaItem?.mediaId == tracks[1].id)
        Log.i("SonglassFeatures", "manual lazyDecoder=true noSilentPreroll=true pendingSessionIntent=true overlap=true pauseResume=true queueAdvanceOnce=true")

        // Manual duration must not turn a natural end into a crossfade.
        session.seekTo(5_650)
        await { controller.queue.value.currentIndex == 2 }
        check(audio.diagnostics().overlapsStarted == 1)
        check(audio.state.value.crossfadeStatus?.kind == CrossfadeKind.Automatic)
        check(audio.state.value.crossfadeStatus?.skipReason == CrossfadeSkipReason.Disabled)

        session.stop()
        graph.settings.setCrossfadeDuration(2_000)
        graph.settings.setManualCrossfadeDuration(750)
        await { audio.diagnostics().configuredMs == 2_000L && audio.diagnostics().manualConfiguredMs == 750L }
        controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying && audio.diagnostics().standbyReady }
        controller.skipToNext()
        await { audio.diagnostics().stage == CrossfadeStage.Fading }
        check(audio.state.value.crossfadeStatus?.requestedDurationMs == 750L)
        check(audio.state.value.crossfadeStatus?.effectiveDurationMs == 750L)
        graph.settings.setCrossfadeDuration(0)
        graph.settings.setManualCrossfadeDuration(1_250)
        await { audio.diagnostics().configuredMs == 0L && audio.diagnostics().manualConfiguredMs == 1_250L }
        check(audio.state.value.crossfadeStatus?.requestedDurationMs == 750L) { "Changing duration rewrote an in-flight fade" }
        await { audio.diagnostics().stage != CrossfadeStage.Fading }
        check(controller.queue.value.currentIndex == 1)
        check(voices.count { it.player.isPlaying } <= 1)
        Log.i("SonglassFeatures", "manual independentDurations=true naturalGapless=true runningPlanPreserved=true")

        // Interrupt synchronously before the posted READY handler can start the prepared target.
        session.stop()
        controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        controller.skipToNext()
        check(audio.isPreparingManualTransition)
        session.pause()
        check(!audio.isPreparingManualTransition && !session.playWhenReady)
        check(voices.none { it.player.playWhenReady })
        check(audio.state.value.crossfadeStatus?.skipReason == CrossfadeSkipReason.Interrupted)
        session.play()
        await { audio.exoPlayer.isPlaying }
        check(controller.queue.value.currentIndex == 1 && voices.count { it.player.isPlaying } == 1)

        session.stop()
        controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        controller.skipToNext()
        check(audio.isPreparingManualTransition)
        session.seekTo(1_000)
        await { audio.exoPlayer.isPlaying }
        check(audio.exoPlayer.currentPosition >= 1_000)
        check(!audio.isPreparingManualTransition && audio.diagnostics().stage != CrossfadeStage.Fading)
        check(controller.queue.value.currentIndex == 1 && voices.count { it.player.isPlaying } == 1)

        session.stop()
        controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        controller.skipToNext()
        check(audio.isPreparingManualTransition)
        controller.skipToNext()
        await { audio.exoPlayer.isPlaying }
        delay(2_100)
        check(controller.queue.value.currentIndex == 2 && session.currentMediaItem?.mediaId == tracks[2].id)
        check(voices.count { it.player.isPlaying } == 1 && !audio.isPreparingManualTransition)
        Log.i("SonglassFeatures", "manual preparationPause=true preparationSeekPreservesPlay=true rapidSkipLatestWins=true staleTimeoutCancelled=true")

        session.stop()
        val missing = tracks[1].copy(uri = tracks[1].uri + ".missing", id = "missing-probe")
        controller.playTracks(listOf(tracks[0], missing, tracks[2]))
        await { audio.exoPlayer.isPlaying }
        controller.skipToNext()
        await { audio.state.value.status == PlayerStatus.Error && !audio.isPreparingManualTransition }
        check(audio.state.value.crossfadeStatus?.skipReason == CrossfadeSkipReason.PrepareFailed)
        check(voices.none { it.player.isPlaying }) { "Failed target left its predecessor audible" }
        graph.settings.setManualCrossfadeDuration(0)
        await { audio.diagnostics().manualConfiguredMs == 0L }
        session.stop()
        check(!audio.diagnostics().secondPlayerCreated)
        check(voices.none { it.player.isPlaying || it.player.playWhenReady })
        Log.i("SonglassFeatures", "manual preparationFailureStopsTail=true disabledReleasesIdleDecoder=true stopBoth=true")
    }

    private suspend fun await(condition: () -> Boolean) = withTimeout(6_000) {
        while (!condition()) delay(20)
    }

    private fun tone(file: File) {
        val rate = 44_100
        val samples = rate * 6
        val bytes = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
        bytes.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVEfmt ".toByteArray()).putInt(16)
        bytes.putShort(1).putShort(1).putInt(rate).putInt(rate * 2).putShort(2).putShort(16)
        bytes.put("data".toByteArray()).putInt(samples * 2)
        repeat(samples) { bytes.putShort((sin(2 * PI * 440 * it / rate) * 2_000).toInt().toShort()) }
        file.writeBytes(bytes.array())
    }
}

private class ProbeSource(var tracks: List<Track>) : MusicSource {
    override val hasAccess = MutableStateFlow(true)
    override suspend fun loadTracks(): List<Track> = tracks
}
