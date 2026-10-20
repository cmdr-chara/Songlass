package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.UserPlaylists
import dev.cmdrchara.songlass.core.search.SearchHistory
import dev.cmdrchara.songlass.core.search.SearchTarget
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What happens to references when MediaStore ids changed, got reused, or the files are gone. */
class TrackRemapTest {
    private val a = song("1", "Music/a.mp3")
    private val b = song("2", "Music/b.mp3")
    private val c = song("3", "Music/c.mp3")
    private val original = listOf(a, b, c)

    /** Favorites 1, 2; a playlist of 1, 2, 3; plays of 1 (twice) and 3; search for 2; lyrics for 1. */
    private fun world() = InMemoryKeyValueStore().also { store ->
        Favorites(store).apply { toggle("1"); toggle("2") }
        UserPlaylists(store) { NOW }.create("Mix", listOf("1", "2", "3"))
        ListeningHistory(store) { NOW }.apply {
            recordPlay("1", null)
            recordPlay("1", null)
            recordPlay("3", null)
        }
        SearchHistory(store).record(SearchTarget.Song("2"))
        assertTrue(LyricsRepository(store = store).import(a, "[00:01.00]Words"))
    }

    private fun restore(source: InMemoryKeyValueStore, sourceTracks: List<Track>, targetTracks: List<Track>): Pair<BackupPreview, InMemoryKeyValueStore> {
        val text = DataBackup(source) { NOW }.export(sourceTracks).text
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target) { NOW }
        val preview = backup.preview(text, targetTracks)
        backup.apply(preview)
        return preview to target
    }

    private fun favorites(store: InMemoryKeyValueStore) = Favorites(store).ids.value
    private fun lyricsKeys(store: InMemoryKeyValueStore) = store.snapshot().keys.filter { it.startsWith("lyrics.import.") }

    @Test
    fun referencesFollowTheirFilesToNewIdsByUniquePath() {
        val reindexed = listOf(song("11", "Music/a.mp3"), song("12", "Music/b.mp3"), song("13", "Music/c.mp3"))
        val (preview, target) = restore(world(), original, reindexed)

        assertEquals(BackupTrackSummary(unchanged = 0, remapped = 3, missing = 0, conflicting = 0), preview.tracks)
        assertEquals(emptyList(), preview.unresolved)
        assertEquals(0, preview.droppedReferences)
        assertEquals(listOf("11", "12"), favorites(target))
        assertEquals(listOf("11", "12", "13"), UserPlaylists(target) { NOW }.playlists.value.single().trackIds)
        val history = ListeningHistory(target) { NOW }.state.value
        assertEquals(mapOf("11" to 2, "13" to 1), history.playCounts)
        assertEquals(listOf("13", "11", "11"), history.recentPlays)
        assertEquals(listOf(SearchTarget.Song("12")), SearchHistory(target).targets.value)
        assertEquals(listOf("lyrics.import.${reindexed[0].uri}"), lyricsKeys(target))
        assertNotNull(target.getString("lyrics.import.${reindexed[0].uri}"))
    }

    @Test
    fun anIdThatNamesADifferentFileNowIsNeverKept() {
        val source = InMemoryKeyValueStore().also { store ->
            Favorites(store).toggle("1")
            UserPlaylists(store) { NOW }.create("Mix", listOf("1"))
            LyricsRepository(store = store).import(a, "[00:01.00]Words")
        }
        // Id 1 is now another song, and the old one is not on this device.
        val impostor = song("1", "Music/other.mp3", title = "Other", artist = "Someone")
        val (preview, target) = restore(source, original, listOf(impostor))

        assertEquals(emptyList(), favorites(target))
        assertEquals(emptyList(), UserPlaylists(target) { NOW }.playlists.value.single().trackIds)
        assertEquals(emptyList(), lyricsKeys(target))
        assertEquals(3, preview.droppedReferences)
        assertEquals(BackupTrackSummary(unchanged = 0, remapped = 0, missing = 0, conflicting = 1), preview.tracks)
        assertEquals(
            listOf(UnresolvedTrack("1", "Song 1", "Artist", "Album", "Music/a.mp3", UnresolvedKind.Conflict)),
            preview.unresolved,
        )
    }

    @Test
    fun idsThatSwappedFilesAreRewrittenTogetherNotOneAfterTheOther() {
        val swapped = listOf(
            song("1", "Music/b.mp3", title = "Song 2"),
            song("2", "Music/a.mp3", title = "Song 1"),
            song("3", "Music/c.mp3"),
        )
        val (preview, target) = restore(world(), original, swapped)

        assertEquals(BackupTrackSummary(unchanged = 1, remapped = 2, missing = 0, conflicting = 0), preview.tracks)
        assertEquals(listOf("2", "1"), favorites(target))
        assertEquals(listOf("2", "1", "3"), UserPlaylists(target) { NOW }.playlists.value.single().trackIds)
        assertEquals(mapOf("2" to 2, "3" to 1), ListeningHistory(target) { NOW }.state.value.playCounts)
        assertEquals(listOf(SearchTarget.Song("1")), SearchHistory(target).targets.value)
        assertEquals(listOf("lyrics.import.${swapped[1].uri}"), lyricsKeys(target))
    }

    @Test
    fun anAmbiguousPathIsNotGuessed() {
        val source = InMemoryKeyValueStore().also { Favorites(it).toggle("1") }
        // Two volumes can hold the same relative path.
        val twins = listOf(song("21", "Music/a.mp3"), song("22", "Music/a.mp3"))

        val (absent, afterAbsent) = restore(source, original, twins)
        assertEquals(listOf("1"), favorites(afterAbsent))
        assertEquals(BackupTrackSummary(unchanged = 0, remapped = 0, missing = 1, conflicting = 0), absent.tracks)

        val (taken, afterTaken) = restore(source, original, twins + song("1", "Music/zzz.mp3", title = "Other"))
        assertEquals(emptyList(), favorites(afterTaken))
        assertEquals(BackupTrackSummary(unchanged = 0, remapped = 0, missing = 0, conflicting = 1), taken.tracks)
    }

    @Test
    fun filesThatAreGoneKeepTheirReferencesInertAndAreReported() {
        val (preview, target) = restore(world(), original, emptyList())
        assertEquals(0, preview.libraryTrackCount)
        assertEquals(listOf("1", "2"), favorites(target))
        assertEquals(listOf("1", "2", "3"), UserPlaylists(target) { NOW }.playlists.value.single().trackIds)
        assertEquals(BackupTrackSummary(unchanged = 0, remapped = 0, missing = 3, conflicting = 0), preview.tracks)
        assertEquals(listOf("1", "2", "3"), preview.unresolved.map { it.id }.sorted())
        assertTrue(preview.unresolved.all { it.kind == UnresolvedKind.Missing })
        assertEquals(listOf("lyrics.import.${a.uri}"), lyricsKeys(target))
    }

    @Test
    fun anIdWithoutRecordedIdentityIsOnlyKeptWhenNothingHereUsesIt() {
        val text = handBuilt(listOf("favorites" to "x"))
        val used = DataBackup(InMemoryKeyValueStore()).preview(text, listOf(song("x")))
        assertEquals("", used.entries["favorites"])
        assertEquals(listOf(UnresolvedTrack("x", null, null, null, null, UnresolvedKind.Conflict)), used.unresolved)

        val unused = DataBackup(InMemoryKeyValueStore()).preview(text, listOf(song("y")))
        assertEquals("x", unused.entries["favorites"])
        assertEquals(listOf(UnresolvedTrack("x", null, null, null, null, UnresolvedKind.Missing)), unused.unresolved)
    }

    @Test
    fun theSameFileIsRecognisedAfterMovesAndTagEdits() {
        val source = InMemoryKeyValueStore().also { Favorites(it).toggle("1") }
        fun summary(vararg target: Track) = restore(source, original, target.toList()).first.tracks
        val same = BackupTrackSummary(unchanged = 1, remapped = 0, missing = 0, conflicting = 0)

        // Same path, tags edited since: the file is the same.
        assertEquals(same, summary(song("1", "Music/a.mp3", title = "Edited", artist = "Else")))
        // Renamed folder keeps the id: all the tags and the length still agree.
        assertEquals(same, summary(song("1", "Music/renamed/a.mp3")))
        assertEquals(same, summary(song("1", "Music/renamed/a.mp3", durationMs = 200_900)))
        // Moved and the length is different: not provably the same file.
        assertEquals(
            BackupTrackSummary(unchanged = 0, remapped = 0, missing = 0, conflicting = 1),
            summary(song("1", "Music/renamed/a.mp3", durationMs = 205_000)),
        )
    }

    @Test
    fun oneFileIsNeverGivenToTwoOldIds() {
        val twinsBefore = listOf(song("1", "Music/x.mp3"), song("2", "Music/x.mp3"))
        val source = InMemoryKeyValueStore().also { Favorites(it).apply { toggle("1"); toggle("2") } }

        // Two old ids with the same path both want the single new file: neither gets it.
        val (shared, afterShared) = restore(source, twinsBefore, listOf(song("9", "Music/x.mp3")))
        assertEquals(listOf("1", "2"), favorites(afterShared))
        assertEquals(BackupTrackSummary(unchanged = 0, remapped = 0, missing = 2, conflicting = 0), shared.tracks)

        // An id that is already recognised on that file keeps it; the other id cannot move onto it.
        val (claimed, afterClaimed) = restore(source, twinsBefore, listOf(song("1", "Music/x.mp3")))
        assertEquals(listOf("1", "2"), favorites(afterClaimed))
        assertEquals(BackupTrackSummary(unchanged = 1, remapped = 0, missing = 1, conflicting = 0), claimed.tracks)
    }
}
