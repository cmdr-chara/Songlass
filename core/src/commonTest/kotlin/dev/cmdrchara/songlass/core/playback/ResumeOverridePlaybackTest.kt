package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.ResumeOverride
import dev.cmdrchara.songlass.core.library.ResumeOverrides
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.track
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A collection override replaces the global resumption setting only where it is stored. */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeOverridePlaybackTest {
    private val folder = PlayContext.Playlist("Music/Chill")
    private val otherFolder = PlayContext.Playlist("Music/Gym")
    private val savedPositionMs = 120_000L
    private val shortTrackDurationMs = 300_000L

    @Test
    fun aCollectionThresholdMakesShortTracksResumableDespiteTheGlobalThreshold() = runTest {
        val fixture = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000),
            ResumeOverride(longTrackThresholdMs = 60_000))
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        runCurrent()
        assertEquals(savedPositionMs, fixture.player.state.value.positionMs)
    }

    @Test
    fun withoutTheOverrideTheGlobalThresholdStillWins() = runTest {
        val fixture = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000))
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        runCurrent()
        assertEquals(0L, fixture.player.state.value.positionMs)
    }

    @Test
    fun anOverrideOffSuppressesResumeEvenWhenTheGlobalSettingIsOn() = runTest {
        val fixture = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 60_000),
            ResumeOverride(resumePerTrack = false))
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        runCurrent()
        assertEquals(0L, fixture.player.state.value.positionMs)
    }

    @Test
    fun anOverrideOnResumesEvenWhenTheGlobalSettingIsOff() = runTest {
        val fixture = fixture(AppPreferences(resumePerTrack = false, longTrackThresholdMs = 60_000),
            ResumeOverride(resumePerTrack = true))
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        runCurrent()
        assertEquals(savedPositionMs, fixture.player.state.value.positionMs)
    }

    @Test
    fun theCollectionPlaybackStartedFromSelectsTheOverride() = runTest {
        val fixture = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000),
            ResumeOverride(longTrackThresholdMs = 60_000))
        fixture.controller.playTracks(listOf(fixture.track), context = otherFolder)
        runCurrent()
        assertEquals(0L, fixture.player.state.value.positionMs, "another collection keeps the global policy")
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        runCurrent()
        assertEquals(savedPositionMs, fixture.player.state.value.positionMs)
    }

    @Test
    fun checkpointsUseTheCollectionThresholdWithoutWritingOutsideIt() = runTest {
        val withOverride = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000),
            ResumeOverride(longTrackThresholdMs = 60_000), initialPositionMs = null)
        withOverride.controller.playTracks(listOf(withOverride.track), context = folder)
        withOverride.controller.seekTo(savedPositionMs)
        runCurrent()
        assertEquals(savedPositionMs, withOverride.markers.state.value.progress.single().positionMs)

        // The same short track under the global 10-minute threshold is not checkpointed.
        val globalOnly = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000),
            initialPositionMs = null)
        globalOnly.controller.playTracks(listOf(globalOnly.track), context = otherFolder)
        globalOnly.controller.seekTo(savedPositionMs)
        runCurrent()
        assertTrue(globalOnly.markers.state.value.progress.isEmpty())
    }

    @Test
    fun aPointWrittenUnderTheOverrideIsReadBackOnTheNextStart() = runTest {
        val fixture = fixture(AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000),
            ResumeOverride(longTrackThresholdMs = 60_000), initialPositionMs = null)
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        fixture.controller.seekTo(savedPositionMs)
        runCurrent()
        fixture.controller.clearQueue()
        fixture.controller.playTracks(listOf(fixture.track), context = folder)
        runCurrent()
        assertEquals(savedPositionMs, fixture.player.state.value.positionMs)
    }

    private data class Fixture(
        val controller: PlaybackController,
        val player: FakeAudioPlayer,
        val markers: TrackMarkers,
        val track: dev.cmdrchara.songlass.core.model.Track,
    )

    private fun TestScope.fixture(
        preferences: AppPreferences,
        override: ResumeOverride? = null,
        initialPositionMs: Long? = savedPositionMs,
    ): Fixture {
        val track = track("a", durationMs = shortTrackDurationMs)
        val markers = TrackMarkers(InMemoryKeyValueStore()).apply { setLibrary(listOf(track)) }
        if (initialPositionMs != null) markers.checkpoint(track, initialPositionMs, thresholdMs = 60_000)
        val overrides = ResumeOverrides(InMemoryKeyValueStore()).apply {
            if (override != null) set(folder, override)
        }
        val player = FakeAudioPlayer()
        val controller = PlaybackController(player, backgroundScope, markers = markers,
            resumeOverrides = overrides, preferences = { preferences })
        return Fixture(controller, player, markers, track)
    }
}
