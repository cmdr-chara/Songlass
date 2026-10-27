package dev.cmdrchara.songlass.core.artwork

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadedArtworksTest {
    private val discovery = ArtworkSubject(artist = "Daft Punk", album = "Discovery")
    private val bytes = ByteArray(64) { it.toByte() }

    private fun manager(
        store: FakeArtworkFileStore,
        ticks: LongArray = longArrayOf(1, 2, 3, 4, 5),
        maxBytes: Long = DownloadedArtworks.DEFAULT_MAX_BYTES,
        maxEntries: Int = DownloadedArtworks.DEFAULT_MAX_ENTRIES,
    ): DownloadedArtworks {
        var index = 0
        return DownloadedArtworks(
            files = store,
            now = { ticks.getOrElse(index++) { ticks.last() + index } },
            maxBytes = maxBytes,
            maxEntries = maxEntries,
        )
    }

    @Test
    fun aSavedCoverIsStoredUnderAStableNameAndReadBackByteForByte() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store)
        val saved = artworks.save(discovery, bytes)
        assertNotNull(saved)
        assertEquals(artworkFileName(discovery.key), saved.fileName)
        assertTrue(store.contains(saved.fileName))
        assertContentEquals(bytes, artworks.bytesOf(discovery.key))
        assertEquals(saved, artworks.get(discovery.key))
        assertEquals(1, artworks.state.value.size)
    }

    @Test
    fun theIndexSurvivesARestartAndPrunesVanishedFiles() = runTest {
        val store = FakeArtworkFileStore()
        manager(store).save(discovery, bytes)
        val restarted = manager(store)
        restarted.load()
        assertEquals(1, restarted.state.value.size)
        assertContentEquals(bytes, restarted.bytesOf(discovery.key))

        // A file deleted outside the app (cleared cache, user removing app data) is not advertised.
        store.removeRaw(artworkFileName(discovery.key))
        val repaired = manager(store)
        repaired.load()
        assertTrue(repaired.state.value.isEmpty())
    }

    @Test
    fun removingACoverDeletesItAndTheIndexEntry() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store)
        val saved = artworks.save(discovery, bytes)!!
        assertTrue(artworks.remove(discovery.key))
        assertFalse(store.contains(saved.fileName))
        assertTrue(artworks.state.value.isEmpty())
        assertNull(artworks.bytesOf(discovery.key))
        assertFalse(artworks.remove(discovery.key))
        // The index is rewritten without the entry, so a restart cannot resurrect the cover.
        val restarted = manager(store)
        restarted.load()
        assertTrue(restarted.state.value.isEmpty())
    }

    @Test
    fun clearingTheCacheRemovesEveryCover() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store)
        artworks.save(discovery, bytes)
        artworks.save(ArtworkSubject("Air", "Moon Safari"), bytes)
        assertEquals(2, artworks.clear())
        assertTrue(artworks.state.value.isEmpty())
        assertTrue(store.names().none { it.startsWith("cover-") })
    }

    @Test
    fun anUnknownOrCorruptedIndexStartsEmptyInsteadOfFailing() = runTest {
        for (broken in listOf("", "not json", "{}", """{"version":9,"entries":[]}""", """{"version":1,"entries":"x"}""")) {
            val store = FakeArtworkFileStore()
            store.putRaw(DownloadedArtworks.INDEX_FILE, broken.encodeToByteArray())
            val artworks = manager(store)
            artworks.load()
            assertTrue(artworks.state.value.isEmpty(), "index: $broken")
        }
    }

    @Test
    fun indexEntriesThatDoNotBelongToTheirSubjectAreIgnored() = runTest {
        val store = FakeArtworkFileStore()
        store.putRaw(artworkFileName(discovery.key), bytes)
        // A hand-edited index naming someone else's file must not become a delete primitive.
        store.putRaw(
            DownloadedArtworks.INDEX_FILE,
            """{"version":1,"entries":[{"artist":"Daft Punk","album":"Discovery",
               "file":"../escape.img","size":64,"saved":1}]}""".encodeToByteArray(),
        )
        val artworks = manager(store)
        artworks.load()
        assertTrue(artworks.state.value.isEmpty())
        assertFalse(store.contains("cover-" + "../escape.img"))
    }

    @Test
    fun theOldestCoversAreEvictedWhenTheEntryLimitIsReached() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store, maxEntries = 2, ticks = longArrayOf(10, 20, 30))
        val first = artworks.save(ArtworkSubject("A", "One"), bytes)!!
        val second = artworks.save(ArtworkSubject("B", "Two"), bytes)!!
        val third = artworks.save(ArtworkSubject("C", "Three"), bytes)!!
        assertEquals(2, artworks.state.value.size)
        assertNull(artworks.get(first.key))
        assertNotNull(artworks.get(second.key))
        assertNotNull(artworks.get(third.key))
        assertFalse(store.contains(first.fileName))
    }

    @Test
    fun theByteBudgetEvictsCoversUntilItFits() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store, maxBytes = 100, ticks = longArrayOf(1, 2, 3))
        artworks.save(ArtworkSubject("A", "One"), ByteArray(60))
        artworks.save(ArtworkSubject("B", "Two"), ByteArray(60))
        assertTrue(artworks.state.value.values.sumOf { it.sizeBytes } <= 100)
        assertEquals(1, artworks.state.value.size)
    }

    @Test
    fun aCoverLargerThanTheWholeBudgetIsStillKept() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store, maxBytes = 10)
        val saved = artworks.save(discovery, ByteArray(500))
        assertNotNull(saved)
        assertEquals(500, saved.sizeBytes)
        assertEquals(1, artworks.state.value.size)
    }

    @Test
    fun aRefusedWriteLeavesTheCacheUntouched() = runTest {
        val store = FakeArtworkFileStore(failWrites = true)
        val artworks = manager(store)
        assertNull(artworks.save(discovery, bytes))
        assertTrue(artworks.state.value.isEmpty())
        assertNull(artworks.save(discovery, ByteArray(0)))
    }

    @Test
    fun changesBumpTheRevisionSoImageCachesRefresh() = runTest {
        val store = FakeArtworkFileStore()
        val artworks = manager(store)
        val before = artworks.revision.value
        artworks.save(discovery, bytes)
        val afterSave = artworks.revision.value
        assertTrue(afterSave > before)
        artworks.remove(discovery.key)
        assertTrue(artworks.revision.value > afterSave)
    }
}
