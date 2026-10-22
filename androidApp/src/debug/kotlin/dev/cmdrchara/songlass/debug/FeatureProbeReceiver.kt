package dev.cmdrchara.songlass.debug

import dev.cmdrchara.songlass.core.player.FocusDucking
import dev.cmdrchara.songlass.core.player.FocusChange

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import dev.cmdrchara.songlass.core.equalizer.EqualizerState
import dev.cmdrchara.songlass.core.equalizer.EqualizerTuning
import dev.cmdrchara.songlass.core.equalizer.GraphicEqLayout
import dev.cmdrchara.songlass.core.equalizer.EqChain
import dev.cmdrchara.songlass.core.equalizer.EqPreset
import dev.cmdrchara.songlass.core.settings.SettingsArea
import kotlin.math.log10
import dev.cmdrchara.songlass.core.player.AudioEffect
import dev.cmdrchara.songlass.core.player.AudioEffectsSettings
import dev.cmdrchara.songlass.core.player.ReplayGainPlan
import dev.cmdrchara.songlass.core.player.ReplayGainSettings
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.player.ReplayGainTags
import kotlin.math.abs
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
                        "headphones" -> HeadphoneProbe.run(context, scope)
                        "headphone-state" -> HeadphoneProbe.observe(context)
                        "audio" -> audio(context, scope)
                        "audio-focus" -> audio(context, scope, focusOnly = true)
                        "audio-equalizer" -> audio(context, scope, equalizerOnly = true)
                        "audio-effects" -> audio(context, scope, effectsOnly = true)
                        "audio-replaygain" -> audio(context, scope, replayGainOnly = true)
                        "audio-transport" -> audio(context, scope, transportOnly = true)
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
        manualFocusOnly: Boolean = false, manualResumeOnly: Boolean = false, overlapFocusKind: CrossfadeKind? = null, transportOnly: Boolean = false, replayGainOnly: Boolean = false, effectsOnly: Boolean = false, equalizerOnly: Boolean = false, focusOnly: Boolean = false) {
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
            val builder = ExoPlayer.Builder(context, renderers).setAudioAttributes(PlaybackAudioAttributes, false)
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
        val audio = Media3AudioPlayer(first.player, scope, first.equalizer, context) { voice(false) }
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
            if (focusOnly) {
                focusAudio(context, graph, audio, session, voices, tracks)
                return
            }
            if (equalizerOnly) {
                equalizerTuningAudio(graph, audio, session, voices, tracks)
                return
            }
            if (effectsOnly) {
                effectsAudio(graph, audio, session, voices, tracks)
                return
            }
            if (replayGainOnly) {
                replayGainAudio(graph, audio, session, voices, tracks)
                return
            }
            if (transportOnly) {
                transportAudio(context, graph, audio, session, voices, tracks)
                return
            }
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

    private suspend fun focusAudio(context: Context, graph: AppGraph, audio: Media3AudioPlayer,
        session: QueueForwardingPlayer, voices: List<PlayerVoice>, tracks: List<Track>) {
        val manager = context.getSystemService(AudioManager::class.java)
        var held: AudioFocusRequest? = null
        suspend fun interrupt(kind: Int) {
            val before = audio.focusChangeSerial
            val request = AudioFocusRequest.Builder(kind)
                .setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener { }.build()
            check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            held = request
            await { audio.focusChangeSerial > before }
        }
        suspend fun regain() {
            val before = audio.focusChangeSerial
            manager.abandonAudioFocusRequest(checkNotNull(held)); held = null
            await { audio.focusChangeSerial > before && audio.lastFocusChange == FocusChange.Gain }
        }
        suspend fun start() {
            graph.controller.playTracks(tracks)
            await { audio.exoPlayer.isPlaying }
        }
        try {
            audio.setVolume(.05f)
            start()
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            check(audio.lastFocusChange == FocusChange.Duck)
            check(audio.exoPlayer.isPlaying && kotlin.math.abs(audio.exoPlayer.volume - .01f) < .0001f)
            for (mode in listOf(FocusDucking.Quiet, FocusDucking.Soft)) {
                graph.settings.setFocusDucking(mode)
                await { audio.appliedFocusSettings.ducking == mode }
                check(audio.exoPlayer.isPlaying && kotlin.math.abs(audio.exoPlayer.volume - .05f * mode.volume) < .0001f)
            }
            regain()
            check(audio.exoPlayer.isPlaying && kotlin.math.abs(audio.exoPlayer.volume - .05f) < .0001f)
            graph.settings.setFocusDucking(FocusDucking.Pause)
            await { audio.appliedFocusSettings.ducking == FocusDucking.Pause }
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            await { !audio.exoPlayer.playWhenReady }
            check(!session.playWhenReady)
            regain(); await { audio.exoPlayer.isPlaying }
            graph.settings.setResumeOnFocusGain(false)
            await { !audio.appliedFocusSettings.resumeOnFocusGain }
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            await { !audio.exoPlayer.playWhenReady }
            regain(); delay(150)
            check(!audio.exoPlayer.playWhenReady && !session.playWhenReady)
            session.play(); await { audio.exoPlayer.isPlaying }
            graph.settings.setResumeOnFocusGain(true)
            await { audio.appliedFocusSettings.resumeOnFocusGain }
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            await { !audio.exoPlayer.playWhenReady }
            session.pause()
            regain(); delay(150)
            check(!audio.exoPlayer.playWhenReady)
            session.play(); await { audio.exoPlayer.isPlaying }
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            await { !audio.exoPlayer.playWhenReady }
            session.stop()
            manager.abandonAudioFocusRequest(checkNotNull(held)); held = null
            delay(150)
            check(audio.exoPlayer.mediaItemCount == 0 && !session.playWhenReady)
            Log.i("SonglassFeatures", "focus nativeDucking10_20_50=true liveLevels=true pauseMode=true singleResume=true resumeDisabled=true userPauseCancels=true stopCancels=true")

            graph.settings.setFocusDucking(FocusDucking.Normal)
            graph.settings.setManualCrossfadeDuration(2_000)
            await { audio.appliedFocusSettings.ducking == FocusDucking.Normal && audio.diagnostics().manualConfiguredMs == 2_000L }
            start(); graph.controller.skipToNext()
            await { audio.diagnostics().stage == CrossfadeStage.Fading && voices.count { it.player.isPlaying } == 2 }
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            check(voices.count { it.player.isPlaying } == 2)
            delay(300)
            val ducked = audio.diagnostics()
            val expected = dev.cmdrchara.songlass.core.player.Crossfade.gains(ducked.progress)
            check(kotlin.math.abs(ducked.incomingVolume - .01f * expected.incoming) < .0004f)
            check(kotlin.math.abs(ducked.outgoingVolume - .01f * expected.outgoing) < .0004f)
            check(kotlin.math.abs(ducked.incomingVolume + ducked.outgoingVolume - .01f) < .0001f)
            regain()
            check(kotlin.math.abs(audio.diagnostics().incomingVolume + audio.diagnostics().outgoingVolume - .05f) < .0001f)
            interrupt(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            await { voices.none { it.player.playWhenReady } && audio.diagnostics().stage != CrossfadeStage.Fading }
            regain(); delay(150)
            check(voices.none { it.player.playWhenReady })
            check(graph.controller.queue.value.currentIndex == 1)
            session.play(); await { audio.exoPlayer.isPlaying }
            check(voices.count { it.player.isPlaying } == 1)
            Log.i("SonglassFeatures", "focus bothVoicesDuckOnce=true overlapTransientCancels=true noOverlapAutoResume=true queueStable=true explicitPlayWorks=true callHardwareNotTested=true")
            interrupt(AudioManager.AUDIOFOCUS_GAIN)
            check(audio.lastFocusChange == FocusChange.PermanentLoss)
            await { !audio.exoPlayer.playWhenReady }
            manager.abandonAudioFocusRequest(checkNotNull(held)); held = null
            delay(150)
            check(!audio.exoPlayer.playWhenReady)
            session.play(); await { audio.exoPlayer.isPlaying }
            Log.i("SonglassFeatures", "focus nativePermanentLossPauses=true noPermanentAutoResume=true freshExplicitRequestWorks=true")
        } finally { held?.let(manager::abandonAudioFocusRequest) }
    }

    /** Transient focus interruption after both real decoders started, not while preparing. */
    private suspend fun replayGainAudio(
        graph: AppGraph,
        audio: Media3AudioPlayer,
        session: QueueForwardingPlayer,
        voices: List<PlayerVoice>,
        tracks: List<Track>,
    ) {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val processor = EqualizerProcessor()
            val format = AudioProcessor.AudioFormat(48_000, 2, encoding)
            processor.configure(format)
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            fun frame(left: Double, right: Double): DoubleArray {
                val floating = encoding == C.ENCODING_PCM_FLOAT
                val input = ByteBuffer.allocateDirect(if (floating) 8 else 4).order(ByteOrder.nativeOrder())
                for (sample in listOf(left, right)) {
                    if (floating) input.putFloat(sample.toFloat()) else input.putShort((sample * 32768).toInt().toShort())
                }
                input.flip()
                processor.queueInput(input)
                val output = processor.output.order(ByteOrder.nativeOrder())
                check(output.remaining() == if (floating) 8 else 4)
                return DoubleArray(2) { if (floating) output.float.toDouble() else output.short / 32768.0 }
            }
            val plan = ReplayGainPlan(ReplayGainMode.Track, 2.0, limiter = true)
            processor.setReplayGain(plan)
            check(processor.observedReplayGain(plan) == null)
            val limited = frame(.9, .45)
            check(abs(limited[0] - 1.0) < .0001 && abs(limited[1] - .5) < .0001) { "PCM limiter did not link channels: $encoding" }
            check(processor.observedReplayGain(plan)?.limiterEnabled == true)
            check(frame(.1, .05)[0] < .15) { "Limiter did not retain release across buffers" }
            processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
            check(abs(frame(.1, .05)[0] - .2) < .0001) { "Flush retained old limiter memory" }
            val attenuated = ReplayGainPlan(ReplayGainMode.Track, .5)
            processor.setReplayGain(attenuated)
            check(processor.observedReplayGain(attenuated) == null)
            val result = frame(.5, -.25)
            check(abs(result[0] - .25) < .0001 && abs(result[1] + .125) < .0001)
            processor.reset()
            Log.i("SonglassFeatures", "replaygain pcm=$encoding linkedLimiter=true releaseAcrossBuffers=true flush=true observedAfterBuffer=true attenuation=true")
        }

        audio.setVolume(.05f)
        graph.settings.apply {
            setReplayGain(ReplayGainMode.Track)
            setReplayGainProcessing(true)
            setReplayGainPreamp(6)
            setReplayGainMissingPreamp(12)
        }
        delay(50)
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying && audio.state.value.replayGainApplication?.preampDb == 12 }
        check(audio.state.value.replayGainApplication!!.linearGain > 3.9f)
        check(abs(audio.exoPlayer.volume - .05f) < .0001f) { "PCM ReplayGain was also applied to player volume" }
        graph.settings.setReplayGainMissingPreamp(-6)
        await { audio.state.value.replayGainApplication?.preampDb == -6 }
        check(abs(audio.state.value.replayGainApplication!!.linearGain - .5011872f) < .0001f)
        check(abs(audio.exoPlayer.volume - .05f) < .0001f)
        graph.settings.setManualCrossfadeDuration(1_250)
        delay(50)
        graph.controller.skipToNext()
        await { voices.count { it.player.isPlaying } == 2 }
        val plan = ReplayGainPlan.from(null, ReplayGainMode.Track, ReplayGainSettings(true, 6, -6))
        await { voices.filter { it.player.isPlaying }.all { it.equalizer?.observedReplayGain(plan) != null } }
        check(voices.count { it.player.isPlaying } == 2)
        await { audio.diagnostics().stage != CrossfadeStage.Fading }
        val current = voices.first { it.player === audio.exoPlayer }
        // The fixture has no tags: inject one only for the native volume/PCM hand-off check.
        current.replayGain = ReplayGainTags(trackGainDb = -6.0206f)
        audio.setReplayGainMode(ReplayGainMode.Track)
        await { audio.state.value.replayGainApplication?.preampDb == 6 }
        graph.settings.setReplayGainProcessing(false)
        await { abs(audio.exoPlayer.volume - .025f) < .0001f }
        check(audio.state.value.replayGainApplication?.limiterEnabled == false)
        session.stop()
        check(voices.none { it.player.playWhenReady })
        Log.i("SonglassFeatures", "replaygain nativePcmBoost=true missingTagPreamp=true noDoubleGain=true bothOverlapVoices=true legacyPathRestored=true")
    }

    private suspend fun equalizerTuningAudio(
        graph: AppGraph, audio: Media3AudioPlayer, session: QueueForwardingPlayer,
        voices: List<PlayerVoice>, tracks: List<Track>,
    ) {
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            val processor=EqualizerProcessor()
            processor.configure(AudioProcessor.AudioFormat(48_000,1,encoding))
            fun measured(chain: EqChain, frequency: Double): Double {
                processor.setChain(chain);processor.flush(AudioProcessor.StreamMetadata.DEFAULT)
                val frames=24_000;val bytes=if(encoding==C.ENCODING_PCM_FLOAT) 4 else 2
                val input=ByteBuffer.allocateDirect(frames*bytes).order(ByteOrder.nativeOrder())
                var inputPower=0.0
                repeat(frames) { i ->
                    val value=sin(2*PI*frequency*i/48_000)*.1
                    val sample=if(bytes==4) value.toFloat().toDouble() else (value*32768).toInt()/32768.0
                    if(bytes==4) input.putFloat(sample.toFloat()) else input.putShort((sample*32768).toInt().toShort())
                    if(i>=16_000) inputPower+=sample*sample
                }
                input.flip();processor.queueInput(input)
                val output=processor.output.order(ByteOrder.nativeOrder());var outputPower=0.0
                repeat(frames) { i ->
                    val sample=if(bytes==4) output.float.toDouble() else output.short/32768.0
                    check(sample.isFinite())
                    if(i>=16_000) outputPower+=sample*sample
                }
                check(processor.processingInfo.value?.filterCount==chain.filters.size)
                val observed=10*log10(outputPower/inputPower)
                val expected=chain.responseDb(frequency,48_000)
                check(abs(observed-expected)<.5) { "EQ PCM mismatch encoding=$encoding hz=$frequency actual=$observed expected=$expected" }
                return observed
            }
            for(layout in GraphicEqLayout.entries) {
                val state=EqualizerState(enabled=true,gains=List(layout.count) { if(it%2==0) 3.0 else -3.0 },
                    tuning=EqualizerTuning(6.0,-4.0,layout))
                measured(state.chain,1000.0)
                check(processor.processingInfo.value?.filterCount==layout.count+2)
            }
            val tone=EqualizerState(enabled=true,tuning=EqualizerTuning(6.0,-4.0)).chain
            val low=measured(tone,50.0);val high=measured(tone,16_000.0)
            check(low-high>7.0)
            processor.reset()
            Log.i("SonglassFeatures","equalizer pcm=$encoding layouts=5,10,15,31 upTo33Filters=true toneShelfResponse=true lowDb=$low highDb=$high")
            yield()
        }
        audio.setVolume(.05f)
        graph.settings.apply { setEqualizerBass(6.0);setEqualizerTreble(-4.0);setEqualizerLayout(GraphicEqLayout.ThirtyOne);setManualCrossfadeDuration(2_000) }
        await { graph.equalizer.state.value.tuning.layout==GraphicEqLayout.ThirtyOne }
        graph.equalizer.applyPreset(EqPreset.Flat,enable=true)
        repeat(31) { graph.equalizer.setGain(it,3.0) }
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying && audio.state.value.processing?.filterCount==33 }
        graph.controller.skipToNext()
        await { voices.count { it.player.isPlaying }==2 }
        await { voices.all { it.equalizer?.processingInfo?.value?.filterCount==33 } }
        graph.settings.setEqualizerBass(-3.0);graph.settings.setEqualizerLayout(GraphicEqLayout.Five)
        await { graph.equalizer.state.value.tuning.layout==GraphicEqLayout.Five }
        val expected=graph.equalizer.state.value.chain
        await { voices.all { it.equalizer?.processingInfo?.value?.let { info ->
            info.filterCount==expected.filters.size && abs(info.headroomDb-expected.preampDb)<.0001
        }==true } }
        check(voices.count { it.player.isPlaying }==2)
        graph.equalizer.setEnabled(false)
        await { voices.all { it.equalizer?.processingInfo?.value?.filterCount==0 } }
        graph.equalizer.setEnabled(true)
        await { audio.state.value.processing?.filterCount==expected.filters.size }
        await { audio.diagnostics().stage!=CrossfadeStage.Fading }
        val saved=graph.equalizer.state.value.manualCurve
        graph.settings.resetToDefaults(SettingsArea.Sound)
        await { graph.equalizer.state.value.tuning==EqualizerTuning() && audio.state.value.processing?.filterCount==0 }
        check(graph.equalizer.state.value.manualCurve==saved)
        graph.settings.setEqualizerLayout(GraphicEqLayout.ThirtyOne)
        await { audio.state.value.processing?.filterCount==31 }
        check(graph.equalizer.state.value.gains.all { it==3.0 })
        check(graph.controller.queue.value.currentIndex==1)
        session.stop()
        Log.i("SonglassFeatures","equalizer native31Bands=true bothOverlapVoices=true liveLayoutAndTone=true masterBypass=true soundReset=true savedBanksPreserved=true queueAdvancedOnce=true")
    }

    private suspend fun effectsAudio(
        graph: AppGraph, audio: Media3AudioPlayer, session: QueueForwardingPlayer,
        voices: List<PlayerVoice>, tracks: List<Track>,
    ) {
        suspend fun waitFor(label: String, predicate: () -> Boolean) {
            try { await(predicate) } catch (failure: Throwable) {
                Log.e("SonglassFeatures", "effects stage=$label diagnostics=${audio.diagnostics()} status=${audio.state.value.crossfadeStatus} voices=" +
                    voices.joinToString { "playing=${it.player.isPlaying} ready=${it.player.playWhenReady} state=${it.player.playbackState} pos=${it.player.currentPosition} speed=${it.player.playbackParameters.speed} effects=${it.equalizer?.processingInfo?.value?.effects}" })
                throw failure
            }
        }
        for (encoding in listOf(C.ENCODING_PCM_16BIT, C.ENCODING_PCM_FLOAT)) {
            fun processor(settings: AudioEffectsSettings): EqualizerProcessor = EqualizerProcessor().also {
                it.setAudioEffects(settings)
                it.configure(AudioProcessor.AudioFormat(48_000, 2, encoding))
                it.flush(AudioProcessor.StreamMetadata.DEFAULT)
            }
            fun buffer(processor: EqualizerProcessor, frames: Int, impulse: Boolean = false, left: Double = .5, right: Double = .25): DoubleArray {
                val bytes = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
                val input = ByteBuffer.allocateDirect(frames * 2 * bytes).order(ByteOrder.nativeOrder())
                repeat(frames) { i ->
                    for (sample in listOf(left, right)) {
                        val value = if (impulse && i > 0) 0.0 else sample
                        if (bytes == 4) input.putFloat(value.toFloat()) else input.putShort((value * 32768).toInt().toShort())
                    }
                }
                input.flip(); processor.queueInput(input)
                val output = processor.output.order(ByteOrder.nativeOrder())
                return DoubleArray(output.remaining() / bytes) {
                    if (bytes == 4) output.float.toDouble() else output.short / 32768.0
                }
            }
            val pan = processor(AudioEffectsSettings(balancePercent = 100))
            check(pan.processingInfo.value?.effects == null)
            val muted = buffer(pan, 1)
            check(muted[0] == 0.0 && abs(muted[1] - .25) < .0001)
            check(pan.processingInfo.value?.effects?.balancePercent == 100)
            pan.reset()
            val width = processor(AudioEffectsSettings(stereoExpandPercent = 100))
            val expanded = buffer(width, 1, right = 0.0)
            check(abs(expanded[0] - .375) < .0001 && abs(expanded[1] + .125) < .0001)
            width.reset()
            val reverb = processor(AudioEffectsSettings(reverbPercent = 100))
            val tail = buffer(reverb, 16_384, impulse = true)
            check(tail.drop(2_000).any { abs(it) > .00001 })
            reverb.flush(AudioProcessor.StreamMetadata.DEFAULT)
            check(buffer(reverb, 16_384, left = 0.0, right = 0.0).all { it == 0.0 })
            reverb.reset()
            Log.i("SonglassFeatures", "effects pcm=$encoding balance=true stereoMatrix=true roomTail=true flushClearsTail=true observedAfterBuffer=true")
        }
        audio.setVolume(.05f)
        graph.settings.apply {
            setAudioEffect(AudioEffect.Balance, 25); setAudioEffect(AudioEffect.StereoExpand, 40)
            setAudioEffect(AudioEffect.Reverb, 30); setAudioEffect(AudioEffect.Tempo, 150)
            setManualCrossfadeDuration(1_000)
        }
        delay(50)
        graph.controller.playTracks(tracks)
        waitFor("ready-room") { audio.exoPlayer.isPlaying && audio.state.value.processing?.effects?.reverbPercent == 30 }
        check(audio.exoPlayer.playbackParameters.speed == 1.5f && audio.exoPlayer.playbackParameters.pitch == 1f)
        // isPlaying can precede AudioTrack clock synchronisation. Capture that first interval,
        // then measure an independent, steady interval instead of treating startup as speed.
        val startupTime = android.os.SystemClock.elapsedRealtime()
        val startupPosition = audio.exoPlayer.currentPosition
        delay(600)
        val startupWall = android.os.SystemClock.elapsedRealtime() - startupTime
        val startupMedia = audio.exoPlayer.currentPosition - startupPosition
        val startTime = android.os.SystemClock.elapsedRealtime()
        val startPosition = audio.exoPlayer.currentPosition
        delay(600)
        val wall = android.os.SystemClock.elapsedRealtime() - startTime
        val media = audio.exoPlayer.currentPosition - startPosition
        Log.i("SonglassFeatures", "effects clock startupMedia=$startupMedia startupWall=$startupWall steadyMedia=$media steadyWall=$wall")
        check(media.toDouble() / wall in 1.25..1.75) { "Unexpected steady tempo progression media=$media wall=$wall" }
        graph.controller.skipToNext()
        waitFor("manual-two-voices") { voices.count { it.player.isPlaying } == 2 }
        check(voices.all { it.player.playbackParameters.speed == 1.5f && it.player.playbackParameters.pitch == 1f })
        waitFor("mono-room-both-voices") { voices.all {
            it.equalizer?.processingInfo?.value?.effects?.let { effects ->
                effects.balancePercent == 0 && effects.stereoExpandPercent == 0 && effects.reverbPercent == 30
            } == true
        } }
        check(audio.state.value.crossfadeStatus?.effectiveDurationMs == 1_000L)
        graph.settings.setAudioEffect(AudioEffect.Tempo, 75)
        waitFor("live-tempo") { voices.all { it.player.playbackParameters.speed == .75f } }
        waitFor("manual-complete") { audio.diagnostics().stage != CrossfadeStage.Fading }
        session.stop()
        graph.settings.apply {
            setManualCrossfadeDuration(0); setCrossfadeDuration(750); setAudioEffect(AudioEffect.Tempo, 200)
        }
        delay(50)
        graph.controller.playTracks(tracks)
        waitFor("automatic-ready") { audio.exoPlayer.isPlaying }
        val started = android.os.SystemClock.elapsedRealtime()
        waitFor("automatic-start") { audio.diagnostics().stage == CrossfadeStage.Fading }
        val triggerAt = android.os.SystemClock.elapsedRealtime() - started
        check(triggerAt in 1_500..3_200) { "Automatic overlap scheduled in source time: $triggerAt" }
        val appliedOverlap = audio.state.value.crossfadeStatus
        val outgoing = voices.single { it.player !== audio.exoPlayer }
        val outgoingPosition = outgoing.player.currentPosition
        val outgoingDuration = outgoing.player.duration
        // A late trigger shrinks the plan. Compare it with the real remaining playhead audio,
        // and prove it started before the unscaled 750-source-ms window (the old 2x bug).
        check(outgoingPosition < outgoingDuration - 750L) { "Overlap waited for the unscaled source window" }
        val remainingWall = ((outgoingDuration - outgoingPosition).coerceAtLeast(0) / 2.0).toLong()
        check(appliedOverlap?.requestedDurationMs == 750L && appliedOverlap.effectiveDurationMs in 250L..750L &&
            abs(appliedOverlap.effectiveDurationMs - minOf(750L, remainingWall)) < 50L) {
            "Unexpected automatic plan at 2x: $appliedOverlap remainingWall=$remainingWall"
        }
        waitFor("automatic-complete") { audio.diagnostics().stage != CrossfadeStage.Fading }
        check(graph.controller.queue.value.currentIndex == 1)
        check(audio.exoPlayer.playbackParameters.speed == 2f)
        session.stop()
        Log.i("SonglassFeatures", "effects nativeTempoMedia=$media wall=$wall pitchParameter=1 bothVoices=true liveTempo=true automaticTriggerWallMs=$triggerAt overlapAppliedMs=${appliedOverlap?.effectiveDurationMs} monoLayoutHonored=true queueAdvancedOnce=true")
    }


    private suspend fun transportAudio(
        context: Context,
        graph: AppGraph,
        audio: Media3AudioPlayer,
        session: QueueForwardingPlayer,
        voices: List<PlayerVoice>,
        tracks: List<Track>,
    ) {
        // A low-level generated tone; the user's player, preferences and music stay untouched.
        audio.setVolume(.05f)
        graph.settings.apply { setPauseFadeDuration(400); setStopFadeDuration(650); setSeekFadeDuration(500) }
        delay(50)
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        session.pause()
        check(!audio.state.value.playWhenReady && !session.playWhenReady) { "Pause intent was delayed" }
        await { audio.transportFadeGain in .01f.. .95f }
        check(audio.exoPlayer.playWhenReady) { "Decoder paused before fading" }
        check(audio.exoPlayer.volume < .05f)
        await { !audio.isTransportFading && !audio.exoPlayer.playWhenReady }
        check(audio.transportFadeGain == 0f)
        check(graph.controller.queue.value.currentIndex == 0)
        session.play()
        await { audio.transportFadeGain in .01f.. .95f && audio.exoPlayer.isPlaying }
        await { !audio.isTransportFading }
        check(audio.transportFadeGain == 1f)
        Log.i("SonglassFeatures", "transport pauseRamp=true immediateSessionIntent=true resumeRamp=true")

        session.pause()
        await { audio.transportFadeGain in .1f.. .9f }
        session.play()
        delay(650)
        check(audio.exoPlayer.isPlaying && audio.state.value.playWhenReady) { "Obsolete pause fired after Play" }
        session.seekTo(2_500)
        check(audio.state.value.positionMs == 2_500L) { "Seek intent was not published" }
        await { audio.transportFadeGain < .9f }
        await { !audio.isTransportFading && audio.exoPlayer.currentPosition >= 2_500 }
        check(graph.controller.queue.value.currentIndex == 0)
        session.seekTo(1_000)
        delay(60)
        session.seekTo(2_000)
        await { !audio.isTransportFading }
        check(audio.exoPlayer.currentPosition in 2_000..3_000) { "Obsolete seek replaced the last target" }
        Log.i("SonglassFeatures", "transport pauseReversed=true seekRamp=true lastSeekWins=true queueStable=true")

        session.stop()
        check(!audio.state.value.playWhenReady && audio.state.value.status == PlayerStatus.Idle)
        check(!session.playWhenReady && session.playbackState == Player.STATE_IDLE)
        await { audio.transportFadeGain in .01f.. .95f }
        check(audio.exoPlayer.playWhenReady) { "Decoder stopped before fading" }
        await { !audio.isTransportFading && audio.exoPlayer.mediaItemCount == 0 }
        check(graph.controller.queue.value.currentIndex == 0)
        session.play()
        await { audio.exoPlayer.isPlaying }
        session.stop()
        delay(80)
        session.play()
        await { audio.exoPlayer.isPlaying }
        delay(750)
        check(audio.exoPlayer.isPlaying) { "Obsolete Stop unloaded the new load" }
        Log.i("SonglassFeatures", "transport stopRamp=true stopUnloads=true stopKeepsQueue=true staleStopCancelled=true")

        // Transient native focus suppression must cancel an in-flight user pause immediately.
        val manager = context.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { }.build()
        session.pause()
        await { audio.transportFadeGain in .1f.. .9f }
        try {
            check(manager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            await { !audio.isTransportFading && !audio.exoPlayer.isPlaying }
            check(!audio.exoPlayer.playWhenReady) { "Pending pause retained resume intent after focus loss" }
        } finally { manager.abandonAudioFocusRequest(request) }
        delay(500)
        check(!audio.exoPlayer.playWhenReady && !audio.state.value.playWhenReady)
        Log.i("SonglassFeatures", "transport nativeFocusCancelsPause=true noResumeAfterCancelledPause=true")

        // A transport gain applies to both voices, independently of the overlap envelopes.
        graph.settings.setManualCrossfadeDuration(1_250)
        delay(50)
        graph.controller.playTracks(tracks)
        await { audio.exoPlayer.isPlaying }
        graph.controller.skipToNext()
        await { audio.diagnostics().stage == CrossfadeStage.Fading && voices.count { it.player.isPlaying } == 2 }
        session.pause()
        await { audio.transportFadeGain in .1f.. .9f }
        check(voices.filter { it.track != null }.all { it.transportGain == audio.transportFadeGain })
        await { !audio.isTransportFading && voices.none { it.player.playWhenReady } }
        graph.settings.apply { setPauseFadeDuration(0); setStopFadeDuration(0); setSeekFadeDuration(0) }
        delay(50)
        session.play()
        check(!audio.isTransportFading)
        session.pause()
        check(!audio.exoPlayer.playWhenReady && !audio.isTransportFading)
        session.seekTo(123)
        check(!audio.exoPlayer.playWhenReady && !audio.isTransportFading)
        session.stop()
        check(audio.exoPlayer.mediaItemCount == 0 && !audio.isTransportFading)
        Log.i("SonglassFeatures", "transport bothOverlapVoices=true zeroImmediate=true pausedSeekStaysPaused=true")
    }

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
        val beforeFocus = audio.focusChangeSerial
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
                audio.focusChangeSerial > beforeFocus && audio.lastFocusChange == FocusChange.TransientLoss && audio.diagnostics().stage != CrossfadeStage.Fading &&
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
        val beforeFocus = audio.focusChangeSerial
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
            check(audio.focusChangeSerial > beforeFocus && audio.lastFocusChange == FocusChange.TransientLoss) { "Probe did not observe a native transient focus callback" }
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
