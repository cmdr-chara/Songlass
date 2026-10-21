package dev.cmdrchara.songlass

import kotlinx.coroutines.flow.distinctUntilChanged

import kotlinx.coroutines.flow.map

import dev.cmdrchara.songlass.core.player.ReplayGain

import dev.cmdrchara.songlass.core.playback.SleepTimer
import dev.cmdrchara.songlass.core.playback.OutputResume
import dev.cmdrchara.songlass.core.player.PlayerEvent
import dev.cmdrchara.songlass.core.player.PlayerStatus
import dev.cmdrchara.songlass.core.equalizer.Equalizer

import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.history.PlayCounter
import dev.cmdrchara.songlass.core.lyrics.EnabledLyricsSource
import dev.cmdrchara.songlass.core.lyrics.PreferWordTimedLyricsSource
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.library.Pins
import dev.cmdrchara.songlass.core.playlists.UserPlaylists
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.LibraryIndex
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playback.PlaybackController
import dev.cmdrchara.songlass.core.playback.TrackMarkers
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
import kotlin.time.TimeSource
import kotlinx.coroutines.sync.withLock

/** Where the music comes from (MediaStore on Android, the demo list elsewhere). */
interface MusicSource {
    /** Whether the app is allowed to read the user's music. */
    val hasAccess: StateFlow<Boolean>

    /** True only when the source durably resolves application IDs before returning a scan. */
    val hasStableTrackIds: Boolean get() = false

    suspend fun loadTracks(): List<Track>
}

/** Recoverable scan failures leave saved data and the last displayed library intact. */
enum class LibraryRefreshFailure { Read, Identity }

class LibrarySnapshotUnavailableException : IllegalStateException("A successful current library scan is required")

/**
 * Objects that live as long as the process, not the UI: playback must keep going (and the
 * system controls must keep working) after the activity is gone.
 */
class AppGraph(
    val player: AudioPlayer,
    val source: MusicSource,
    private val store: KeyValueStore,
    private val scope: CoroutineScope,
    localLyrics: LyricsSource = LyricsSource.Empty,
    remoteLyrics: LyricsSource = LyricsSource.Empty,
    val tagEditor: dev.cmdrchara.songlass.core.model.FileTagEditor? = null,
    amllLyrics: LyricsSource? = null,
) {
    val settings = AppSettings(store)
    val trackMarkers = TrackMarkers(store) { Clock.System.now().toEpochMilliseconds() }
    val controller = PlaybackController(player, scope, markers = trackMarkers,
        onUserTransport = { outputResume.cancel() }, preferences = { settings.state.value })
    val sleepTimer = SleepTimer(controller, scope, clock = { Clock.System.now().toEpochMilliseconds() })
    val favorites = Favorites(store)
    val pins = Pins(store)
    val searchHistory = dev.cmdrchara.songlass.core.search.SearchHistory(store)
    val smartPlaylists = dev.cmdrchara.songlass.core.playlists.SmartPlaylists(store) { Clock.System.now().toEpochMilliseconds() }
    val userPlaylists = UserPlaylists(store) { Clock.System.now().toEpochMilliseconds() }
    val homeCustomization = dev.cmdrchara.songlass.core.home.HomeCustomization(store)
    private val dataBackup = dev.cmdrchara.songlass.core.backup.DataBackup(store) { Clock.System.now().toEpochMilliseconds() }
    private val backupMutex = kotlinx.coroutines.sync.Mutex()
    private val _isRestoringBackup = MutableStateFlow(false)
    val isRestoringBackup: StateFlow<Boolean> = _isRestoringBackup.asStateFlow()
    val equalizer = Equalizer(store) { Clock.System.now().toEpochMilliseconds() }
    val supportsAmllLyrics = amllLyrics != null
    val lyrics = LyricsRepository(
        localLyrics,
        if (amllLyrics == null) remoteLyrics else PreferWordTimedLyricsSource(
            EnabledLyricsSource(amllLyrics, settings.state) { it.amllEnabled },
            remoteLyrics,
        ),
        store,
        cacheVersion = { if (settings.state.value.amllEnabled) 1 else 0 },
    )
    val autoEq = dev.cmdrchara.songlass.ui.autoEqRepository(store, equalizer)
    val history = ListeningHistory(store, clock = { Clock.System.now().toEpochMilliseconds() })
    private val playCounter = PlayCounter()
    private val playbackClock = TimeSource.Monotonic.markNow()
    private val outputResume = OutputResume(
        clock = { player.monotonicTimeMs }, latestDisconnect = { player.latestDisconnect },
    )

    private val _isLibraryLoading = MutableStateFlow(false)
    val isLibraryLoading: StateFlow<Boolean> = _isLibraryLoading.asStateFlow()
    private val _hasCompleteLibraryScan = MutableStateFlow(false)
    val hasCompleteLibraryScan: StateFlow<Boolean> = _hasCompleteLibraryScan.asStateFlow()
    private val _libraryRefreshFailure = MutableStateFlow<LibraryRefreshFailure?>(null)
    val libraryRefreshFailure: StateFlow<LibraryRefreshFailure?> = _libraryRefreshFailure.asStateFlow()
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    private var libraryAccessGeneration = 0L

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()
    private val libraryIndex = LibraryIndex(allTracks, { settings.state.value.libraryFilter })
    val library: StateFlow<MusicLibrary> = libraryIndex.state

    init {
        scope.launch {
            source.hasAccess.collect { access -> if (!access) {
                libraryAccessGeneration++
                _hasCompleteLibraryScan.value = false
                _libraryRefreshFailure.value = null
                invalidateLibraryAvailability()
            } }
        }
        scope.launch {
            combine(allTracks, settings.state.map { it.libraryFilter }.distinctUntilChanged()) { _, _ ->
                Unit
            }.collect {
                // Also finish updates if the screen that requested a refresh was closed.
                libraryIndex.refresh()
            }
        }
        scope.launch {
            settings.state.map { it.crossfadeDurationMs }.distinctUntilChanged().collect(player::setCrossfade)
        }
        scope.launch {
            settings.state.map { it.manualCrossfadeDurationMs }.distinctUntilChanged().collect(player::setManualCrossfade)
        }
        // ReplayGain: the player's own volume evens out loudness, on top of the device volume.
        scope.launch {
            if (player.handlesReplayGain) {
                settings.state.map { it.replayGain }.distinctUntilChanged().collect(player::setReplayGainMode)
            } else {
                combine(player.state.map { it.replayGain }.distinctUntilChanged(), settings.state.map { it.replayGain }.distinctUntilChanged()) { tags, mode ->
                    ReplayGain.volumeFor(tags, mode)
                }.distinctUntilChanged().collect(player::setVolume)
            }
        }
        scope.launch {
            equalizer.state.map { it.chain }.distinctUntilChanged().collect(player::setEqualizer)
        }
        // Remember a pause caused by the output going away; any other start or stop forgets it.
        scope.launch {
            player.events.collect { if (it == PlayerEvent.PausedByDisconnect) outputResume.onPausedByDisconnect() }
        }
        scope.launch {
            player.state.map { it.playWhenReady }.distinctUntilChanged().collect { playing -> if (playing) outputResume.cancel() }
        }
        // Count a play once enough of a track has been heard, and record where it started from.
        scope.launch {
            combine(controller.queue, player.state, controller.timeline, isRestoringBackup) { queue, state, timeline, restoring ->
                val entry = queue.current
                val duration = entry?.track?.durationMs ?: 0L
                val counted = playCounter.update(
                    entryId = entry?.id, occurrenceId = timeline.occurrenceId,
                    discontinuityId = timeline.discontinuityId, positionMs = state.positionMs,
                    durationMs = duration, playing = !restoring && entry?.isAvailable == true &&
                        state.playWhenReady && state.status == PlayerStatus.Ready,
                    elapsedRealtimeMs = playbackClock.elapsedNow().inWholeMilliseconds,
                    rule = settings.state.value.playCountRule,
                )
                if (counted && entry != null) entry.track.id to controller.listeningContext else null
            }.collect { counted ->
                if (counted != null) {
                    history.recordPlay(counted.first, counted.second)
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

    /**
     * Headphones, a headset or a car's audio connected (reported by the platform). Resumes the
     * music if it stopped because they had disconnected and the setting is on.
     */
    fun onOutputConnected() {
        if (outputResume.onOutputConnected(settings.state.value.resumeOnReconnect)) controller.resume()
    }

    fun exportBackup(): dev.cmdrchara.songlass.core.backup.BackupExport {
        if (!isLibraryReadyForBackup()) throw LibrarySnapshotUnavailableException()
        val tracks = allTracks.value
        val accessGeneration = libraryAccessGeneration
        val archive = dataBackup.export(tracks)
        // Export runs off the UI thread. Do not return an archive based on identities from
        // an obsolete scan, including a revoke/grant cycle that finishes during encoding.
        if (!isLibraryReadyForBackup() || accessGeneration != libraryAccessGeneration ||
            tracks !== allTracks.value) throw LibrarySnapshotUnavailableException()
        return archive
    }

    fun isLibraryReadyForBackup(): Boolean = source.hasAccess.value &&
        _hasCompleteLibraryScan.value && !_isLibraryLoading.value

    fun previewBackup(text: String): dev.cmdrchara.songlass.core.backup.BackupPreview {
        if (!isLibraryReadyForBackup()) throw LibrarySnapshotUnavailableException()
        val tracks = allTracks.value
        val accessGeneration = libraryAccessGeneration
        val preview = dataBackup.preview(text, tracks)
        if (!isLibraryReadyForBackup() || accessGeneration != libraryAccessGeneration ||
            tracks !== allTracks.value) throw LibrarySnapshotUnavailableException()
        return preview
    }

    /** The reviewed plan and observable reload finish even if the form closes. */
    suspend fun restoreBackup(
        text: String,
        reviewed: dev.cmdrchara.songlass.core.backup.BackupPreview,
    ): dev.cmdrchara.songlass.core.backup.BackupResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            // Fixed lock order: a scan cannot change the identity mapping between review
            // comparison and mutation. The scan path never takes backupMutex.
            backupMutex.withLock {
                refreshMutex.withLock {
                    if (!isLibraryReadyForBackup()) throw LibrarySnapshotUnavailableException()
                    _isRestoringBackup.value = true
                    trackMarkers.writesSuspended = true
                    try {
                        val tracks = allTracks.value
                        val accessGeneration = libraryAccessGeneration
                        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                            val current = dataBackup.preview(text, tracks)
                            if (!source.hasAccess.value || accessGeneration != libraryAccessGeneration) {
                                throw LibrarySnapshotUnavailableException()
                            }
                            dataBackup.applyConfirmed(reviewed, current)
                        }
                        settings.reload()
                        trackMarkers.reload()
                        homeCustomization.reload()
                        favorites.reload()
                        pins.reload()
                        history.reload()
                        userPlaylists.reload()
                        smartPlaylists.reload()
                        searchHistory.reload()
                        equalizer.reload()
                        lyrics.reload()
                        result
                    } finally {
                        _isRestoringBackup.value = false
                        trackMarkers.writesSuspended = false
                    }
                }
            }
        }

    /** Retry is process-scoped, so closing the reporting screen does not abandon a scan. */
    fun requestLibraryRefresh() { scope.launch { refreshLibrary() } }

    /** Keep the displayed snapshot and open decoder, but never reopen unverified file addresses. */
    private fun invalidateLibraryAvailability() {
        trackMarkers.setLibrary(emptyList(), stableIds = source.hasStableTrackIds)
        controller.refreshTracks(emptyList(), stableIds = source.hasStableTrackIds)
    }

    /** Re-reads the music from [source]; does nothing without access. */
    suspend fun refreshLibrary() {
        if (!source.hasAccess.value) return
        refreshMutex.withLock {
            if (!source.hasAccess.value) return@withLock
            val accessGeneration = libraryAccessGeneration
            _isLibraryLoading.value = true
            _hasCompleteLibraryScan.value = false
            _libraryRefreshFailure.value = null
            try {
                val tracks = source.loadTracks()
                // A completed scan from before a revoke (even a revoke/grant cycle) must not
                // re-enable stale file handles or bookmark actions after access was cleared.
                if (!source.hasAccess.value || accessGeneration != libraryAccessGeneration) return@withLock
                trackMarkers.setLibrary(tracks, stableIds = source.hasStableTrackIds)
                _allTracks.value = tracks
                controller.refreshTracks(tracks, stableIds = source.hasStableTrackIds)
                libraryIndex.refresh()
                // Indexing dispatches too: a revoke during its build is not a complete scan.
                if (source.hasAccess.value && accessGeneration == libraryAccessGeneration) {
                    _hasCompleteLibraryScan.value = true
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                invalidateLibraryAvailability()
                throw cancelled
            } catch (_: dev.cmdrchara.songlass.core.library.TrackIdentityRegistryException) {
                invalidateLibraryAvailability()
                _libraryRefreshFailure.value = LibraryRefreshFailure.Identity
            } catch (_: Exception) {
                invalidateLibraryAvailability()
                _libraryRefreshFailure.value = LibraryRefreshFailure.Read
            } finally {
                _isLibraryLoading.value = false
            }
        }
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
