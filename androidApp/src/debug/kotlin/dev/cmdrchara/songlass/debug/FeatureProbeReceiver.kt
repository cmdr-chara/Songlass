package dev.cmdrchara.songlass.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dev.cmdrchara.songlass.*
import dev.cmdrchara.songlass.core.home.HomeSection
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.playback.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
                    if (intent.getStringExtra("case") == "audio") audio(context, scope)
                    else backup(context, scope)
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
            graph.restoreBackup(archive.text)
            check(graph.favorites.ids.value == listOf("fixture-new"))
            check(graph.settings.state.value.crossfadeDurationMs == 6_000L)
            check(HomeSection.RecentlyAdded in graph.homeCustomization.state.value.hidden)
            check(graph.history.state.value.playCounts["fixture-new"] == 1)
            check(graph.userPlaylists.playlists.value.single().trackIds == listOf("fixture-new"))
            check(SharedPreferencesStore(context, "songlass-feature-probe").getString("favorites") == "fixture-new")
            Log.i("SonglassFeatures", "backup nativeCommit=true invalidPreserved=true remapped=1 liveReload=true")
        } finally { store.replace(store.snapshot().keys, emptyMap()) }
    }

    private suspend fun audio(context: Context, scope: CoroutineScope) {
        val file = File(context.cacheDir, "songlass-crossfade-probe.wav")
        withContext(Dispatchers.IO) { tone(file) }
        val voices = mutableListOf<PlayerVoice>()
        fun voice(focus: Boolean): PlayerVoice {
            val eq = EqualizerProcessor()
            val renderers = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                    DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams).setAudioProcessors(arrayOf(eq)).build()
            }
            return PlayerVoice(ExoPlayer.Builder(context, renderers).setAudioAttributes(PlaybackAudioAttributes, focus)
                .setHandleAudioBecomingNoisy(true).build(), eq, focus).also { voices += it }
        }
        val first = voice(true)
        val audio = Media3AudioPlayer(first.player, scope, first.equalizer) { voice(false) }
        val tracks = listOf(Track("tone-1", "Probe one", file.toURI().toString(), durationMs = 6_000),
            Track("tone-2", "Probe two", file.toURI().toString(), durationMs = 6_000))
        val graph = AppGraph(audio, ProbeSource(tracks), InMemoryKeyValueStore(), scope)
        val session = QueueForwardingPlayer(audio.exoPlayer, graph.controller, audio)
        val follow = scope.launch { audio.activePlayer.collect { session.switchPlayer(it) } }
        try {
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
            audio.release()
            withContext(Dispatchers.IO) { file.delete() }
        }
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
