package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Per-collection resumption overrides: inheritance, persistence, canonical folder identity. */
class ResumeOverridesTest {
    private val folder = PlayContext.Playlist("Music/Chill")
    private val userPlaylist = PlayContext.UserPlaylist("playlist-1")
    private val smartPlaylist = PlayContext.SmartPlaylist("smart-1")

    @Test
    fun anEmptyStoreLeavesTheGlobalPolicyInCharge() {
        val overrides = ResumeOverrides()
        assertTrue(overrides.entries.value.isEmpty())
        val preferences = AppPreferences(resumePerTrack = true, longTrackThresholdMs = 300_000)
        assertEquals(ResumePolicy(true, 300_000), preferences.resumePolicy(overrides.overrideFor(folder)))
        assertNull(overrides.overrideFor(null))
    }

    @Test
    fun foldersAndPlaylistsRoundTripThroughStorage() {
        val store = InMemoryKeyValueStore()
        ResumeOverrides(store).apply {
            set(folder, ResumeOverride(resumePerTrack = true, longTrackThresholdMs = 60_000))
            set(userPlaylist, ResumeOverride(longTrackThresholdMs = 1_800_000))
            set(smartPlaylist, ResumeOverride(resumePerTrack = false))
        }
        val restored = ResumeOverrides(store)
        assertEquals(ResumeOverride(true, 60_000), restored.overrideFor(folder))
        assertEquals(ResumeOverride(null, 1_800_000), restored.overrideFor(userPlaylist))
        assertEquals(ResumeOverride(false, null), restored.overrideFor(smartPlaylist))
        assertEquals(listOf(folder, userPlaylist, smartPlaylist), restored.entries.value.map { it.context })
        assertEquals(true, store.getString("library.resume_overrides")!!.isNotEmpty())
    }

    @Test
    fun aPartialOverrideInheritsTheFieldsItDoesNotSet() {
        val overrides = ResumeOverrides()
        overrides.set(folder, ResumeOverride(longTrackThresholdMs = 300_000))
        val preferences = AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000)
        assertEquals(ResumePolicy(true, 300_000), preferences.resumePolicy(overrides.overrideFor(folder)))

        overrides.set(folder, ResumeOverride(resumePerTrack = false))
        assertEquals(ResumePolicy(false, 600_000), preferences.resumePolicy(overrides.overrideFor(folder)))

        overrides.set(folder, ResumeOverride(resumePerTrack = true))
        assertEquals(ResumePolicy(true, 600_000), preferences.resumePolicy(overrides.overrideFor(folder)))
    }

    @Test
    fun anOverrideReplacesBothDirectionsWithoutTouchingTheGlobalPreference() {
        val overrides = ResumeOverrides()
        overrides.set(folder, ResumeOverride(resumePerTrack = false))
        val global = AppPreferences(resumePerTrack = true, longTrackThresholdMs = 600_000)
        assertEquals(ResumePolicy(false, 600_000), global.resumePolicy(overrides.overrideFor(folder)))
        // The global value itself is not part of the store and stays untouched.
        assertEquals(true, global.resumePerTrack)
        assertEquals(600_000, global.longTrackThresholdMs)
    }

    @Test
    fun foldersAreIdentifiedByTheirCanonicalPathNeverByATrackId() {
        val overrides = ResumeOverrides()
        overrides.set(PlayContext.Playlist("Music/Chill/"), ResumeOverride(resumePerTrack = true))
        assertEquals(ResumeOverride(true), overrides.overrideFor(PlayContext.Playlist("Music/Chill")))
        assertEquals(listOf(PlayContext.Playlist("Music/Chill")), overrides.entries.value.map { it.context })
        assertNull(overrides.overrideFor(PlayContext.Playlist("")))
    }

    @Test
    fun removingOneOverrideAndClearingAllWriteThroughStorage() {
        val store = InMemoryKeyValueStore()
        val overrides = ResumeOverrides(store)
        overrides.set(folder, ResumeOverride(resumePerTrack = true))
        overrides.set(userPlaylist, ResumeOverride(longTrackThresholdMs = 60_000))
        overrides.remove(folder)
        assertNull(overrides.overrideFor(folder))
        assertEquals(ResumeOverride(null, 60_000), overrides.overrideFor(userPlaylist))
        assertEquals(1, ResumeOverrides(store).entries.value.size)
        overrides.clearAll()
        assertTrue(overrides.entries.value.isEmpty())
        assertNull(store.getString("library.resume_overrides"))
        assertTrue(ResumeOverrides(store).entries.value.isEmpty())
    }

    @Test
    fun settingAnEmptyOverrideRemovesIt() {
        val overrides = ResumeOverrides()
        overrides.set(folder, ResumeOverride(resumePerTrack = true))
        overrides.set(folder, ResumeOverride())
        assertTrue(overrides.entries.value.isEmpty())
    }

    @Test
    fun unsupportedCollectionsAndThresholdsAreRejectedBeforeWriting() {
        val store = InMemoryKeyValueStore()
        val overrides = ResumeOverrides(store)
        assertFailsWith<IllegalArgumentException> {
            overrides.set(PlayContext.Album("Album", "Artist"), ResumeOverride(resumePerTrack = true))
        }
        assertFailsWith<IllegalArgumentException> {
            overrides.set(PlayContext.Playlist(""), ResumeOverride(resumePerTrack = true))
        }
        for (invalid in listOf(0L, 120_000L, 600_001L)) {
            assertFailsWith<IllegalArgumentException> {
                overrides.set(folder, ResumeOverride(longTrackThresholdMs = invalid))
            }
        }
        assertTrue(overrides.entries.value.isEmpty())
        assertNull(store.getString("library.resume_overrides"))
        for (valid in AppSettings.LONG_TRACK_CHOICES_MS) {
            overrides.set(folder, ResumeOverride(longTrackThresholdMs = valid))
            assertEquals(valid, overrides.overrideFor(folder)?.longTrackThresholdMs)
        }
    }

    @Test
    fun malformedRecordsAreIgnoredWithoutRewritingAndDuplicatesKeepTheLastValue() {
        val store = InMemoryKeyValueStore()
        val unit = '\u001F'
        val recordSeparator = '\u001E'
        fun record(context: PlayContext, resume: String, threshold: String) =
            listOf(context.key, resume, threshold).joinToString(recordSeparator.toString())
        val corrupted = listOf(
            "not-a-context${recordSeparator}1${recordSeparator}60000",
            record(folder, "1", "60000"),
            record(folder, "1", "300000"),
            record(PlayContext.Playlist("Music/Gone"), "nonsense", "60000"),
            record(PlayContext.Playlist("Music/Future"), "1", "120000"),
            record(PlayContext.Album("A", "B"), "1", "60000"),
        ).joinToString(unit.toString())
        store.putString("library.resume_overrides", corrupted)
        val overrides = ResumeOverrides(store)
        // Duplicate folder records collapse to the last one; the other invalid rows are ignored.
        assertEquals(listOf(ResumeOverrideEntry(folder, ResumeOverride(true, 300_000))), overrides.entries.value)
        assertEquals(corrupted, store.getString("library.resume_overrides"), "reading never rewrites the value")
    }

    @Test
    fun summariesReportCollectionsMissingFromTheCurrentData() {
        val overrides = ResumeOverrides()
        overrides.set(folder, ResumeOverride(resumePerTrack = true))
        overrides.set(userPlaylist, ResumeOverride(longTrackThresholdMs = 60_000))
        val summaries = overrides.summaries { context -> if (context == userPlaylist) "Focus" else null }
        assertEquals(listOf(false, true), summaries.map { it.available })
        assertEquals(listOf(null, "Focus"), summaries.map { it.name })
        assertEquals(ResumeOverride(true), summaries.first().override)
    }

    @Test
    fun reloadPublishesAReplacedValue() {
        val store = InMemoryKeyValueStore()
        val overrides = ResumeOverrides(store)
        overrides.set(folder, ResumeOverride(resumePerTrack = true))
        val staging = InMemoryKeyValueStore().apply {
            putString("library.resume_overrides", listOf(userPlaylist.key, "0", "").joinToString("\u001E"))
        }
        val prepare = overrides.prepareReload(staging)
        assertEquals(ResumeOverride(true), overrides.overrideFor(folder), "nothing publishes before the commit")
        prepare()
        assertNull(overrides.overrideFor(folder))
        assertEquals(ResumeOverride(false), overrides.overrideFor(userPlaylist))
    }

    @Test
    fun aBackupRestoreBlocksEditsAndTheKeyTravelsWithTheOtherLibraryPreferences() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val overrides = ResumeOverrides(store, gate)
        overrides.set(folder, ResumeOverride(resumePerTrack = true))
        gate.restoring {
            overrides.set(folder, ResumeOverride(resumePerTrack = false))
            overrides.clearAll()
            overrides.remove(userPlaylist)
        }
        assertEquals(ResumeOverride(true), overrides.overrideFor(folder))
        assertEquals(1, ResumeOverrides(store).entries.value.size)
        assertTrue("library.resume_overrides".startsWith("library."), "the standard backup carries the namespace")
    }
}
