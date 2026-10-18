package dev.cmdrchara.songlass

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.flow.map

import dev.cmdrchara.songlass.core.player.ReplayGain

import dev.cmdrchara.songlass.core.playback.SleepTimer
import dev.cmdrchara.songlass.core.equalizer.Equalizer

import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.history.PlayCounter
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.library.Pins
import dev.cmdrchara.songlass.core.playlists.UserPlaylists
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.demo.SampleLibrary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Where the music comes from (MediaStore on Android, the demo list elsewhere). */
interface MusicSource {
    /** Whether the app is allowed to read the user's music. */
    val hasAccess: StateFlow<Boolean>

    suspend fun loadTracks(): List<Track>
}

/**
 * Objects that live as long as the process, not the UI: playback must keep going (and the
 * system controls must keep working) after the activity is gone.
 */
class AppGraph(
    val player: AudioPlayer,
    val source: MusicSource,
    store: KeyValueStore,
    private val scope: CoroutineScope,
    localLyrics: LyricsSource = LyricsSource.Empty,
    remoteLyrics: LyricsSource = LyricsSource.Empty,
) {
    val controller = PlaybackController(player, scope)
    val sleepTimer = SleepTimer(controller, scope, clock = { Clock.System.now().toEpochMilliseconds() })
    val favorites = Favorites(store)
    val pins = Pins(store)
    val userPlaylists = UserPlaylists(store) { Clock.System.now().toEpochMilliseconds() }
    val settings = AppSettings(store)
    val equalizer = Equalizer(store) { Clock.System.now().toEpochMilliseconds() }
    val lyrics = LyricsRepository(localLyrics, remoteLyrics, store)
    val history = ListeningHistory(store, clock = { Clock.System.now().toEpochMilliseconds() })
    private val playCounter = PlayCounter()

    private val _library = MutableStateFlow(MusicLibrary(emptyList()))
    val library: StateFlow<MusicLibrary> = _library.asStateFlow()

    init {
        // ReplayGain: the player's own volume evens out loudness, on top of the device volume.
        scope.launch {
            combine(player.state.map { it.replayGain }.distinctUntilChanged(), settings.state.map { it.replayGain }.distinctUntilChanged()) { tags, mode ->
                ReplayGain.volumeFor(tags, mode)
            }.distinctUntilChanged().collect(player::setVolume)
        }
        scope.launch {
            equalizer.state.map { it.chain }.distinctUntilChanged().collect(player::setEqualizer)
        }
        // Count a play once enough of a track has been heard, and record where it started from.
        scope.launch {
            combine(controller.queue, player.state) { queue, state -> queue.current to state }.collect { (entry, state) ->
                val duration = state.durationMs.takeIf { it > 0 } ?: entry?.track?.durationMs ?: 0
                if (entry != null && playCounter.update(entry.id, state.positionMs, duration)) {
                    history.recordPlay(entry.track.id, controller.context)
                }
            }
        }
        // The silent demo player has no clock of its own.
        if (player is FakeAudioPlayer) {
            scope.launch {
                while (true) {
                    delay(DEMO_TICK_MS)
                    if (player.state.value.isPlaying) player.advanceBy(DEMO_TICK_MS)
                }
            }
        }
    }

    /** Re-reads the music from [source]; does nothing without access. */
    suspend fun refreshLibrary() {
        if (!source.hasAccess.value) return
        _library.value = MusicLibrary(source.loadTracks())
    }

    companion object {
        private const val DEMO_TICK_MS = 250L

        /** Fictional library and silent player, for platforms without real playback yet. */
        fun demo(scope: CoroutineScope, store: KeyValueStore = InMemoryKeyValueStore()) =
            AppGraph(FakeAudioPlayer(), DemoMusicSource, store, scope)
    }
}

private object DemoMusicSource : MusicSource {
    override val hasAccess: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun loadTracks(): List<Track> = SampleLibrary.tracks
}
