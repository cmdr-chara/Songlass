package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.backup.BackupKeys
import dev.cmdrchara.songlass.core.backup.ValueKind
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class RatingsTest {

    @Test
    fun starsReplaceThePreviousValueAndZeroRemovesTheRating() {
        val ratings = Ratings()
        assertEquals(3, ratings.set("a", 3))
        assertEquals(3, ratings.rating("a"))
        assertEquals(5, ratings.set("a", 5))
        assertEquals(5, ratings.rating("a"))
        assertEquals(0, ratings.set("a", 0))
        assertEquals(0, ratings.rating("a"))
        assertEquals(emptyMap(), ratings.values.value)
        // Clearing a rating that was never stored is harmless.
        assertEquals(0, ratings.set("a", 0))
    }

    @Test
    fun onlyWholeStarsFromOneToFiveAreStored() {
        val store = InMemoryKeyValueStore()
        val ratings = Ratings(store)
        assertEquals(0, ratings.set("a", -1))
        assertEquals(0, ratings.set("a", 6))
        assertEquals(0, ratings.set("", 4))
        assertEquals(0, ratings.set("bad\u001Fid", 4))
        assertEquals(0, ratings.set("bad\u001Eid", 4))
        assertEquals(null, store.getString(Ratings.KEY))
        assertEquals(4, ratings.set("a", 4))
    }

    @Test
    fun ratingsSurviveANewInstanceOnTheSameStoreAndKeepTheirOrder() {
        val store = InMemoryKeyValueStore()
        Ratings(store).apply { set("b", 2); set("a", 5) }
        val reopened = Ratings(store)
        assertEquals(mapOf("b" to 2, "a" to 5), reopened.values.value)
        assertEquals(5, reopened.rating("a"))
    }

    @Test
    fun malformedStoredRowsAreIgnoredAndTheFirstValidRowWins() {
        val store = InMemoryKeyValueStore().apply {
            putString(
                Ratings.KEY,
                "\u001Fa\u001E4\u001Fb\u001E9\u001Fc\u001E0\u001Fd\u001Ex\u001Ff\u001E2\u001E3\u001Fa\u001E1\u001Fe\u001E2",
            )
        }
        assertEquals(mapOf("a" to 4, "e" to 2), Ratings(store).values.value)
    }

    @Test
    fun writesAreRejectedWhileABackupRestoreOwnsTheKeys() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val ratings = Ratings(store, gate)
        assertEquals(3, ratings.set("a", 3))
        val persisted = store.snapshot()
        gate.restoring {
            assertEquals(3, ratings.set("a", 5))
            assertEquals(0, ratings.set("b", 4))
            assertEquals(3, ratings.rating("a"))
            assertEquals(0, ratings.rating("b"))
            assertEquals(persisted, store.snapshot())
        }
        assertEquals(5, ratings.set("a", 5))
    }

    @Test
    fun prepareReloadDecodesBeforeCommitAndPublishesOnlyWhenInvoked() {
        val ratings = Ratings().apply { set("a", 2) }
        val staged = InMemoryKeyValueStore().apply { putString(Ratings.KEY, "b\u001E5") }
        val publish = ratings.prepareReload(staged)
        // Nothing changes until the owner thread publishes the staged state.
        assertEquals(mapOf("a" to 2), ratings.values.value)
        publish()
        assertEquals(mapOf("b" to 5), ratings.values.value)
        assertEquals(0, ratings.rating("a"))
        assertEquals(5, ratings.rating("b"))
    }

    @Test
    fun ratingsAreRestoredWithTheOtherUserDataAsPlainValues() {
        // The key is exported and restored verbatim: it is not one of the backup's remapped
        // track-id formats (see the design document's backup note).
        assertEquals(ValueKind.Plain, BackupKeys.kindOf(Ratings.KEY))
    }

    @Test
    fun aRecycledPlatformRowCannotInheritTheRatingOfTheFileItReplaced() = runTest {
        val store = InMemoryKeyValueStore()
        val registry = TrackIdentityRegistry(store)
        val ratings = Ratings(store)
        // The first scan retains legacy ids; later unknown files get fresh opaque ids.
        val original = registry.canonicalize(listOf(raw("7"))).single()
        assertEquals("7", original.id)
        assertEquals(5, ratings.set(original.id, 5))

        // The same platform row now names an unrelated file: it must not inherit the rating.
        val replacement = registry.canonicalize(listOf(raw("7").copy(dateAddedMs = 2_000))).single()
        assertNotEquals(original.id, replacement.id)
        assertEquals(0, ratings.rating(replacement.id))
        assertNull(MusicLibrary(listOf(replacement)).track(original.id))

        // The original file comes back with its known source identity and keeps its rating.
        val returned = registry.canonicalize(listOf(raw("7"))).single()
        assertEquals(original.id, returned.id)
        assertEquals(5, ratings.rating(returned.id))
    }

    private fun raw(id: String) = Track(
        id = id,
        title = "Episode",
        uri = "content://media/external/audio/media/$id",
        artist = "Narrator",
        album = "Series",
        durationMs = 1_200_000,
        folder = "Podcasts",
        fileName = "episode.flac",
        dateAddedMs = 1_000,
    )
}
