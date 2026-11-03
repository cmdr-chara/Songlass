package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.playback.HeadphoneKind
import dev.cmdrchara.songlass.core.playback.BluetoothCommandFilter

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
import dev.cmdrchara.songlass.core.library.Ratings
import dev.cmdrchara.songlass.core.library.ResumeOverrides
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    /** The phone this runs on; null switches the first-run welcome off. */
    device: dev.cmdrchara.songlass.core.onboarding.DeviceInfo? = null,
    /** Shows the chosen launcher icon. Null where the platform cannot switch icons. */
    private val applyLauncherIcon: ((dev.cmdrchara.songlass.core.settings.AppIconSelection) -> Unit)? = null,
) {
    // First, so a fresh install's style is in the store before the settings read it.
    val onboarding = dev.cmdrchara.songlass.core.onboarding.Onboarding(store, device)
    val restoreMutations = dev.cmdrchara.songlass.core.storage.RestoreMutationGate()
    val settings = AppSettings(store, restoreMutations)
    val trackMarkers = TrackMarkers(store) { Clock.System.now().toEpochMilliseconds() }
    val artworkAvailability = dev.cmdrchara.songlass.core.artwork.ArtworkAvailability()
    val controller = PlaybackController(player, scope, markers = trackMarkers,
        resumeOverrides = ResumeOverrides(store, restoreMutations),
        onUserTransport = { outputResume.cancel() }, preferences = { settings.state.value },
        playCountOf = { track -> history.state.value.playCounts[track.id] ?: 0 })
    val sleepTimer = SleepTimer(controller, scope, clock = { Clock.System.now().toEpochMilliseconds() })
    val favorites = Favorites(store, restoreMutations)
    val pins = Pins(store, restoreMutations)
    val ratings = Ratings(store, restoreMutations)
    /** The same overrides the controller applies; the folder/playlist pages edit them here. */
    val resumeOverrides: ResumeOverrides = controller.resumeOverrides
    val searchHistory = dev.cmdrchara.songlass.core.search.SearchHistory(store, restoreMutations)
    val smartPlaylists = dev.cmdrchara.songlass.core.playlists.SmartPlaylists(store, restoreMutations) { Clock.System.now().toEpochMilliseconds() }
    val userPlaylists = UserPlaylists(store, restoreMutations) { Clock.System.now().toEpochMilliseconds() }
    val homeCustomization = dev.cmdrchara.songlass.core.home.HomeCustomization(store, restoreMutations)
    private val dataBackup = dev.cmdrchara.songlass.core.backup.DataBackup(store) { Clock.System.now().toEpochMilliseconds() }
    private val backupMutex = kotlinx.coroutines.sync.Mutex()
    private val _isRestoringBackup = MutableStateFlow(false)
    val isRestoringBackup: StateFlow<Boolean> = _isRestoringBackup.asStateFlow()
    val equalizer = Equalizer(store, restoreMutations) { Clock.System.now().toEpochMilliseconds() }
    val supportsAmllLyrics = amllLyrics != null
    val supportsOnlineLyrics = remoteLyrics !== LyricsSource.Empty
    val supportsAppIconChoice = applyLauncherIcon != null
    val lyrics = LyricsRepository(
        localLyrics,
        remoteLyrics.let { remote ->
            val online = if (remote === LyricsSource.Empty) remote else EnabledLyricsSource(remote, settings.state) { it.lrclibEnabled }
            if (amllLyrics == null) online else PreferWordTimedLyricsSource(
                EnabledLyricsSource(amllLyrics, settings.state) { it.amllEnabled },
                online,
            )
        },
        store,
        cacheVersion = { settings.state.value.let { (if (it.amllEnabled) 1 else 0) + (if (it.lrclibEnabled) 0 else 2) } },
        resolveSelection = { requested ->
            if (!isLibraryReadyForBackup() || isRestoringBackup.value) null
            else allTracks.value.singleOrNull { it.id == requested.id }
                ?.takeIf { source.hasStableTrackIds || it == requested }
                ?.let { dev.cmdrchara.songlass.core.lyrics.LyricsSelection(
                    it, canonicalId = source.hasStableTrackIds, accessGeneration = libraryAccessGeneration,
                ) }
        },
    )
    val autoEq = dev.cmdrchara.songlass.ui.autoEqRepository(store, equalizer)
    val history = ListeningHistory(store, restoreMutations, clock = { Clock.System.now().toEpochMilliseconds() })

    /**
     * Last.fm scrobbling for the whole process. It only observes the playback samples below; the
     * opt-in switch and the API credentials are read from preferences on demand, so a disabled
     * feature performs no network work and writes no preference.
     */
    val lastFm = dev.cmdrchara.songlass.core.scrobble.LastFmScrobbler(
        store = store,
        scope = scope,
        transport = dev.cmdrchara.songlass.core.net.platformHttpTransport(),
        clock = { Clock.System.now().toEpochMilliseconds() },
        config = { dev.cmdrchara.songlass.core.scrobble.lastFmConfig(settings.state.value) },
    )
    private val playCounter = PlayCounter()
    private val playbackClock = TimeSource.Monotonic.markNow()
    private val bluetoothCommands = BluetoothCommandFilter { player.monotonicTimeMs }
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
    private var readerLibraryRefresh: Deferred<Unit>? = null
    private var libraryAccessGeneration = 0L

    private val _allTracks = MutableStateFlow<List<Track>>(emptyList())
    val allTracks: StateFlow<List<Track>> = _allTracks.asStateFlow()
    private val _cueSheets = MutableStateFlow(dev.cmdrchara.songlass.core.cue.CueCatalog(store).entries())
    val cueSheets: StateFlow<List<dev.cmdrchara.songlass.core.cue.ImportedCueSheet>> = _cueSheets.asStateFlow()


    /** Votes the visible library reads when a minimum rating is set; see [LibraryIndex]. */
    private val libraryIndex = LibraryIndex(
        allTracks,
        { settings.state.value.libraryFilter },
        { ratings.values.value },
    )
    val library: StateFlow<MusicLibrary> = libraryIndex.state

    init {
        applyLauncherIcon?.let { apply ->
            // Also runs once at start, so a stale alias from an earlier choice is put right.
            scope.launch {
                settings.state.map {
                    dev.cmdrchara.songlass.core.settings.AppIconSelection(it.appIcon, it.interfaceStyle, it.appearance)
                }.distinctUntilChanged().collect { apply(it) }
            }
        }
        scope.launch {
            source.hasAccess.collect { access -> if (!access) {
                libraryAccessGeneration++
                _hasCompleteLibraryScan.value = false
                _libraryRefreshFailure.value = null
                invalidateLibraryAvailability()
            } }
        }
        scope.launch {
            combine(
                allTracks,
                settings.state.map { it.libraryFilter }.distinctUntilChanged(),
                ratings.values,
            ) { _, _, _ ->
                Unit
            }.collect {
                // Also finish updates if the screen that requested a refresh was closed. A vote
                // only rebuilds the library while the filter shows a minimum rating; otherwise
                // the index sees unchanged inputs and returns without publishing.
                libraryIndex.refresh()
            }
        }
        scope.launch {
            settings.state.map { it.crossfadeDurationMs }.distinctUntilChanged().collect(player::setCrossfade)
        }
        scope.launch {
            settings.state.map { it.headphones.bluetoothCommandDelayMs }.distinctUntilChanged().collect(bluetoothCommands::configure)
        }
        scope.launch {
            settings.state.map { it.handleAudioFocus to it.pauseOnDisconnect }.distinctUntilChanged()
                .collect { (focus, noisy) -> player.setInterruptionPolicy(focus, noisy) }
        }
        scope.launch {
            settings.state.map { it.audioFocus }.distinctUntilChanged().collect(player::setAudioFocusSettings)
        }
        scope.launch {
            settings.state.map { it.manualCrossfadeDurationMs }.distinctUntilChanged().collect(player::setManualCrossfade)
        }
        scope.launch {
            settings.state.map { it.transportFades }.distinctUntilChanged().collect(player::setTransportFades)
        }
        scope.launch {
            settings.state.map { it.replayGainSettings }.distinctUntilChanged().collect(player::setReplayGainSettings)
        }
        scope.launch {
            settings.state.map { it.audioEffects }.distinctUntilChanged().collect(player::setAudioEffects)
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
            settings.state.map { it.equalizerTuning }.distinctUntilChanged().collect(equalizer::setTuning)
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
                val playing = !restoring && entry?.isAvailable == true &&
                    state.playWhenReady && state.status == PlayerStatus.Ready
                // Same sample that feeds the play counter: no second collector, no duplicate events.
                lastFm.observePlayback(
                    track = entry?.track, occurrenceId = timeline.occurrenceId,
                    discontinuityId = timeline.discontinuityId, positionMs = state.positionMs,
                    durationMs = duration, playing = playing,
                    elapsedRealtimeMs = playbackClock.elapsedNow().inWholeMilliseconds,
                )
                val counted = playCounter.update(
                    entryId = entry?.id, occurrenceId = timeline.occurrenceId,
                    discontinuityId = timeline.discontinuityId, positionMs = state.positionMs,
                    durationMs = duration, playing = playing,
                    elapsedRealtimeMs = playbackClock.elapsedNow().inWholeMilliseconds,
                    rule = settings.state.value.playCountRule,
                )
                if (counted && entry != null) Triple(entry.track.id, controller.listeningContext,
                    restoreMutations.ticket()) else null
            }.collect { counted ->
                if (counted != null && restoreMutations.accepts(counted.third)) {
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
    fun onOutputsConnected(kinds: Set<HeadphoneKind>) {
        if (kinds.isEmpty()) return
        val preferences = settings.state.value.headphones
        bluetoothCommands.configure(preferences.bluetoothCommandDelayMs)
        if (HeadphoneKind.Bluetooth in kinds) bluetoothCommands.connected()
        if (!restoreMutations.canMutate) { outputResume.cancel(); return }
        if (outputResume.onOutputConnected(kinds.any(preferences::resumes))) controller.resume()
    }

    val headsetConnectionRevision: Long get() {
        bluetoothCommands.configure(settings.state.value.headphones.bluetoothCommandDelayMs)
        return bluetoothCommands.revision
    }
    fun ignoreHeadsetCommand(eventTimeMs: Long? = null): Boolean {
        bluetoothCommands.configure(settings.state.value.headphones.bluetoothCommandDelayMs)
        return bluetoothCommands.shouldIgnore(eventTimeMs)
    }
    fun acceptsBluetoothRequest(packageName: String?, capturedRevision: Long): Boolean {
        bluetoothCommands.configure(settings.state.value.headphones.bluetoothCommandDelayMs)
        return bluetoothCommands.acceptsControllerRequest(packageName, capturedRevision)
    }
    fun ignoreBluetoothController(packageName: String?): Boolean =
        BluetoothCommandFilter.isBluetoothController(packageName) && ignoreHeadsetCommand()

    fun exportBackup(): dev.cmdrchara.songlass.core.backup.BackupExport {
        val ticket = restoreMutations.ticket()
        if (ticket == null || !isLibraryReadyForBackup()) throw LibrarySnapshotUnavailableException()
        val tracks = allTracks.value
        val accessGeneration = libraryAccessGeneration
        val archive = dataBackup.export(tracks)
        // Export runs off the UI thread. Do not return an archive based on identities from
        // an obsolete scan, including a revoke/grant cycle that finishes during encoding.
        if (!restoreMutations.accepts(ticket) || !isLibraryReadyForBackup() ||
            accessGeneration != libraryAccessGeneration || tracks !== allTracks.value) throw LibrarySnapshotUnavailableException()
        return archive
    }

    fun isLibraryReadyForBackup(): Boolean = restoreMutations.canMutate && hasCurrentLibrarySnapshot()

    private fun hasCurrentLibrarySnapshot(): Boolean = source.hasAccess.value &&
        _hasCompleteLibraryScan.value && !_isLibraryLoading.value && _libraryRefreshFailure.value == null

    fun previewBackup(text: String): dev.cmdrchara.songlass.core.backup.BackupPreview {
        val ticket = restoreMutations.ticket()
        if (ticket == null || !isLibraryReadyForBackup()) throw LibrarySnapshotUnavailableException()
        val tracks = allTracks.value
        val accessGeneration = libraryAccessGeneration
        val preview = dataBackup.preview(text, tracks)
        if (!restoreMutations.accepts(ticket) || !isLibraryReadyForBackup() ||
            accessGeneration != libraryAccessGeneration || tracks !== allTracks.value) throw LibrarySnapshotUnavailableException()
        return preview
    }

    /** The reviewed plan and observable reload finish even if the form closes. */
    suspend fun restoreBackup(
        text: String,
        reviewed: dev.cmdrchara.songlass.core.backup.BackupPreview,
    ): dev.cmdrchara.songlass.core.backup.BackupResult =
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            // Owner-thread entry closes repository writers before the first lock suspension,
            // independent of when Compose renders its modal progress surface.
            restoreMutations.restoring {
                _isRestoringBackup.value = true
                outputResume.cancel()
                trackMarkers.writesSuspended = true
                lyrics.selectionChanged()
                try {
                    // Fixed lock order. Scans never take backupMutex; lyrics imports use the
                    // same write mutex, so an already accepted import finishes before replacement.
                    backupMutex.withLock {
                        refreshMutex.withLock {
                            if (!hasCurrentLibrarySnapshot()) throw LibrarySnapshotUnavailableException()
                            libraryAccessGeneration++ // Earlier lyrics confirmations become obsolete too.
                            val tracks = allTracks.value
                            val accessGeneration = libraryAccessGeneration
                            val prepareMarkers = trackMarkers.reloadPreparation() // Capture live indexes on Main.
                            lyrics.withImportWritesPaused {
                                val (result, publications) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val current = dataBackup.preview(text, tracks)
                                    val staged = dev.cmdrchara.songlass.core.backup.PreparedBackupRestore(store, reviewed, current)
                                    // Preparation can fail without changing real data or any observable
                                    // repository. Even legacy normalizations only touch the staging map.
                                    val prepared = listOf(
                                        settings.prepareReload(staged.store),
                                        prepareMarkers(staged.store),
                                        homeCustomization.prepareReload(staged.store),
                                        favorites.prepareReload(staged.store),
                                        pins.prepareReload(staged.store),
                                        ratings.prepareReload(staged.store),
                                        resumeOverrides.prepareReload(staged.store),
                                        history.prepareReload(staged.store),
                                        userPlaylists.prepareReload(staged.store),
                                        smartPlaylists.prepareReload(staged.store),
                                        searchHistory.prepareReload(staged.store),
                                        equalizer.prepareReload(staged.store),
                                    )
                                    // Permission/file identity can change while decoding on a worker.
                                    // Recheck immediately before the sole real managed-key replacement.
                                    if (!hasCurrentLibrarySnapshot() || accessGeneration != libraryAccessGeneration ||
                                        tracks !== allTracks.value) throw LibrarySnapshotUnavailableException()
                                    staged.commit() to prepared
                                }
                                // No store reads, decoding or writes remain in these owner-thread
                                // publications. EQ takes the latest device route at this boundary.
                                publications.forEach { publish -> publish() }
                                lyrics.reload()
                                libraryIndex.refresh() // Finish the restored visible filter before releasing readers.
                                result
                            }
                        }
                    }
                } finally {
                    outputResume.cancel()
                    // A failed attempt must also select the old profile for a route that
                    // changed while writers were suspended; this reads no store data.
                    equalizer.reconcileCurrentOutput()
                    trackMarkers.writesSuspended = false
                    _isRestoringBackup.value = false
                    lyrics.selectionChanged()
                }
            }
        }

    /** Retry is process-scoped, so closing the reporting screen does not abandon a scan. */
    suspend fun removeCueSheet(id: String, ticket: dev.cmdrchara.songlass.core.storage.MutationTicket?): Boolean {
        val removed = refreshMutex.withLock {
            if (!restoreMutations.accepts(ticket)) false
            else dev.cmdrchara.songlass.core.cue.CueCatalog(store).remove(id)
        }
        if (removed) refreshLibrary()
        return removed && hasCompleteLibraryScan.value
    }

    /** The immutable library snapshot and restore ticket belong to the reviewed import. */
    suspend fun importCueSheet(name: String, text: String, snapshot: List<Track>,
        ticket: dev.cmdrchara.songlass.core.storage.MutationTicket?): Int? {
        val clips = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            dev.cmdrchara.songlass.core.cue.CueSheets.parse(text)?.let {
                dev.cmdrchara.songlass.core.cue.CueSheets.resolve(it, snapshot)
            }
        } ?: return null
        val saved = refreshMutex.withLock {
            if (!restoreMutations.accepts(ticket) || !hasCompleteLibraryScan.value ||
                !source.hasAccess.value || _allTracks.value !== snapshot) return@withLock false
            // Different sheets cannot silently redefine an already imported segment.
            val existing = snapshot.filter { it.isCueTrack }.associateBy { it.id }
            if (clips.any { existing[it.id]?.let { old -> old != it } == true }) return@withLock false
            dev.cmdrchara.songlass.core.cue.CueCatalog(store).save(name, text)
        }
        if (!saved) return null
        refreshLibrary()
        return clips.size.takeIf { hasCompleteLibraryScan.value }
    }

    fun requestLibraryRefresh() { scope.launch { refreshLibrary() } }

    /**
     * Main-thread readers join a current scan or share a process-owned cold-start scan.
     * Cancelling one browser request must not cancel the scan awaited by other readers.
     * Explicit refreshes remain separate: a rejected file write must queue a fresh scan
     * even when an older one is running with an obsolete access generation.
     */
    suspend fun awaitLibraryScan() {
        if (_isLibraryLoading.value) {
            _isLibraryLoading.first { !it }
            return
        }
        if (_hasCompleteLibraryScan.value) return
        val pending = readerLibraryRefresh?.takeIf { it.isActive }
            ?: scope.async { refreshLibrary() }.also { readerLibraryRefresh = it }
        pending.await()
    }

    /** Called on Main when a file writer rejects stale identity or cannot verify its write. */
    fun onFileWriteFailure() {
        // A scan already in flight may have observed the address before it changed. It must
        // not publish after this boundary, even if read permission was never revoked.
        libraryAccessGeneration++
        _hasCompleteLibraryScan.value = false
        _libraryRefreshFailure.value = LibraryRefreshFailure.Read
        invalidateLibraryAvailability()
        requestLibraryRefresh()
    }

    /** Keep the displayed snapshot and open decoder, but never reopen unverified file addresses. */
    private fun invalidateLibraryAvailability() {
        lyrics.selectionChanged()
        artworkAvailability.invalidate()
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
            // Keep the current decoder and displayed collection, but stop new opens and
            // prefetch while the previous scan is being replaced by verified file identities.
            invalidateLibraryAvailability()
            _libraryRefreshFailure.value = null
            try {
                val physicalTracks = source.loadTracks()
                val tracks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    dev.cmdrchara.songlass.core.cue.CueCatalog(store).expand(physicalTracks)
                }
                val artworkIndex = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    dev.cmdrchara.songlass.core.artwork.ArtworkAvailability.Index.build(tracks)
                }
                // A completed scan from before a revoke (even a revoke/grant cycle) must not
                // re-enable stale file handles or bookmark actions after access was cleared.
                if (!source.hasAccess.value || accessGeneration != libraryAccessGeneration) return@withLock
                trackMarkers.reconcileStoredIdentities(tracks, stableIds = source.hasStableTrackIds)
                _allTracks.value = tracks
                _cueSheets.value = dev.cmdrchara.songlass.core.cue.CueCatalog(store).entries()
                libraryIndex.refresh()
                // Indexing dispatches too: a revoke during its build is not a complete scan.
                if (source.hasAccess.value && accessGeneration == libraryAccessGeneration) {
                    trackMarkers.setLibrary(tracks, stableIds = source.hasStableTrackIds)
                    controller.refreshTracks(tracks, stableIds = source.hasStableTrackIds)
                    _hasCompleteLibraryScan.value = true
                    // Notify artwork observers only after direct readiness checks can succeed.
                    _isLibraryLoading.value = false
                    artworkAvailability.publish(artworkIndex)
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
                lyrics.selectionChanged()
            }
        }
    }

    companion object {
        private const val DEMO_TICK_MS = 250L

        /** Fictional library and silent player, for platforms without real playback yet. */
        fun demo(scope: CoroutineScope, store: KeyValueStore = InMemoryKeyValueStore(), device: dev.cmdrchara.songlass.core.onboarding.DeviceInfo? = null,
            applyLauncherIcon: ((dev.cmdrchara.songlass.core.settings.AppIconSelection) -> Unit)? = null) =
            AppGraph(FakeAudioPlayer(), DemoMusicSource, store, scope, device = device, applyLauncherIcon = applyLauncherIcon)
    }
}

private object DemoMusicSource : MusicSource {
    override val hasAccess: StateFlow<Boolean> = MutableStateFlow(true)
    override suspend fun loadTracks(): List<Track> = SampleLibrary.tracks
}
