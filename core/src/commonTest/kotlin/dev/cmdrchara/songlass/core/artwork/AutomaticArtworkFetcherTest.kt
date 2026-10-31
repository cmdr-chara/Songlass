package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class AutomaticArtworkFetcherTest {
    private val track = Track("canonical", "A Song", "content://media/41", artist = "An Artist", album = "Album")
    private val candidate = ArtworkCandidate("track:1", "An Artist", "Album", "https://example.org/cover.jpg", null, "A Song")
    private val bytes = byteArrayOf(1, 2, 3)
    private fun manager(store: FakeArtworkFileStore = FakeArtworkFileStore()) = DownloadedArtworks(store, now = { 100 })
    private fun downloader(action: suspend () -> Unit = {}): ArtworkDownloader = object : ArtworkDownloader {
        override suspend fun downloadImage(url: String): ArtworkImageResult {
            action()
            return ArtworkImageResult.Success(bytes, "image/jpeg")
        }
    }

    @Test fun disabledAndLocalArtworkNeverContactTheProvider() = runTest {
        var calls = 0
        val cache = manager()
        val fetcher = AutomaticArtworkFetcher({ calls++; ArtworkSearchResult.Found(listOf(candidate)) }, downloader(), cache, { 0 }, { true })
        assertEquals(AutomaticArtworkResult.Blocked, fetcher.fetch(track, { false }, { false }))
        assertEquals(AutomaticArtworkResult.Local, fetcher.fetch(track, { true }, { true }))
        assertEquals(0, calls)
        assertTrue(cache.state.value.isEmpty())
    }

    @Test fun oneConfidentMissingCoverIsSavedAutomaticallyAndSurvivesRestart() = runTest {
        var calls = 0
        val store = FakeArtworkFileStore()
        val cache = manager(store)
        val fetcher = AutomaticArtworkFetcher({ calls++; ArtworkSearchResult.Found(listOf(candidate)) }, downloader(), cache, { 0 }, { true })
        assertEquals(AutomaticArtworkResult.Saved, fetcher.fetch(track, { true }, { false }))
        assertEquals(AutomaticArtworkResult.Existing, fetcher.fetch(track, { true }, { false }))
        assertEquals(1, calls)
        val record = assertNotNull(ArtworkCovers.downloadedForTrack(track, cache.state.value))
        assertTrue(record.automatic)
        val restarted = manager(store)
        restarted.load()
        assertTrue(assertNotNull(restarted.get(record.key)).automatic)
        assertContentEquals(bytes, restarted.bytesOf(record.key))
    }

    @Test fun strictMatchingRejectsAmbiguityButUsesAnExactKnownAlbumOrCompleteFilenameCredit() {
        val anotherRelease = candidate.copy(providerId = "track:2", album = "Other release")
        assertNull(AutomaticArtworkChoice.forTrack(track.copy(album = null), listOf(candidate, anotherRelease)))
        assertEquals(candidate, AutomaticArtworkChoice.forTrack(track, listOf(candidate, anotherRelease)))
        assertNull(AutomaticArtworkChoice.forTrack(track.copy(artist = "Another artist"), listOf(candidate)))
        assertNull(AutomaticArtworkChoice.forTrack(track.copy(artist = null), listOf(candidate)))
        val credited = track.copy(title = "A Song - An Artist", artist = "<unknown>", album = "Downloads")
        assertEquals(candidate, AutomaticArtworkChoice.forTrack(credited, listOf(candidate)))
        assertNull(AutomaticArtworkChoice.forTrack(credited.copy(title = "A Song - Someone Else"), listOf(candidate)))
    }

    @Test fun policyOrCanonicalAvailabilityLostDuringLookupPreventsDownloadAndDoesNotCoolDown() = runTest {
        val gate = CompletableDeferred<Unit>()
        var allowed = true
        var calls = 0
        var downloads = 0
        val cache = manager()
        val fetcher = AutomaticArtworkFetcher({ calls++; if (calls == 1) gate.await(); ArtworkSearchResult.Found(listOf(candidate)) },
            downloader { downloads++ }, cache, { 0 }, { true })
        val first = async { fetcher.fetch(track, { allowed }, { false }) }
        runCurrent(); allowed = false; gate.complete(Unit)
        assertEquals(AutomaticArtworkResult.Blocked, first.await())
        assertEquals(0, downloads)
        allowed = true
        assertEquals(AutomaticArtworkResult.Saved, fetcher.fetch(track, { allowed }, { false }))
        assertEquals(2, calls)
    }

    @Test fun manualChoiceDuringDownloadIsNeverOverwritten() = runTest {
        val gate = CompletableDeferred<Unit>()
        val cache = manager()
        val fetcher = AutomaticArtworkFetcher({ ArtworkSearchResult.Found(listOf(candidate)) }, downloader { gate.await() }, cache, { 0 }, { true })
        val pending = async { fetcher.fetch(track, { true }, { false }) }
        runCurrent()
        val manual = assertNotNull(cache.save(assertNotNull(ArtworkSubject.forTrack(track)), byteArrayOf(9)))
        gate.complete(Unit)
        assertEquals(AutomaticArtworkResult.Existing, pending.await())
        assertEquals(manual, ArtworkCovers.downloadedForTrack(track, cache.state.value))
        assertFalse(manual.automatic)
        assertContentEquals(byteArrayOf(9), cache.bytesOf(manual.key))
    }

    @Test fun localCoverAppearingDuringDownloadWinsWithoutSavingAnAutomaticOverride() = runTest {
        var local = false
        val cache = manager()
        val fetcher = AutomaticArtworkFetcher({ ArtworkSearchResult.Found(listOf(candidate)) }, downloader { local = true }, cache, { 0 }, { true })
        assertEquals(AutomaticArtworkResult.Local, fetcher.fetch(track, { true }, { local }))
        assertTrue(cache.state.value.isEmpty())
    }

    @Test fun aNoMatchWaitsADayBeforeAnotherProviderRequest() = runTest {
        var now = 0L
        var calls = 0
        val fetcher = AutomaticArtworkFetcher({ calls++; ArtworkSearchResult.NoMatch }, downloader(), manager(), { now }, { true })
        assertEquals(AutomaticArtworkResult.NoMatch, fetcher.fetch(track, { true }, { false }))
        assertEquals(AutomaticArtworkResult.CoolingDown, fetcher.fetch(track, { true }, { false }))
        now = AutomaticArtworkFetcher.NO_MATCH_COOLDOWN_MS - 1
        assertEquals(AutomaticArtworkResult.CoolingDown, fetcher.fetch(track, { true }, { false }))
        now++
        assertEquals(AutomaticArtworkResult.NoMatch, fetcher.fetch(track, { true }, { false }))
        assertEquals(2, calls)
    }

    @Test fun transportFailureAndBadImageHaveBoundedRetriesAndNeverEnterTheCoverCache() = runTest {
        var now = 0L
        var calls = 0
        val cache = manager()
        val fetcher = AutomaticArtworkFetcher({ calls++; if (calls == 1) ArtworkSearchResult.Failed(ArtworkSearchFailure.Offline)
            else ArtworkSearchResult.Found(listOf(candidate)) }, downloader(), cache, { now }, { false })
        assertEquals(AutomaticArtworkResult.Failed, fetcher.fetch(track, { true }, { false }))
        assertEquals(AutomaticArtworkResult.CoolingDown, fetcher.fetch(track, { true }, { false }))
        now += AutomaticArtworkFetcher.ERROR_COOLDOWN_MS
        assertEquals(AutomaticArtworkResult.Failed, fetcher.fetch(track, { true }, { false }))
        assertTrue(cache.state.value.isEmpty())
        assertEquals(2, calls)
    }

    @Test fun simultaneousRequestsForTheSameTrackShareOneSerializedLookup() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val fetcher = AutomaticArtworkFetcher({ calls++; gate.await(); ArtworkSearchResult.Found(listOf(candidate)) }, downloader(), manager(), { 0 }, { true })
        val first = async { fetcher.fetch(track, { true }, { false }) }
        val second = async { fetcher.fetch(track, { true }, { false }) }
        runCurrent(); assertEquals(1, calls); gate.complete(Unit)
        assertEquals(AutomaticArtworkResult.Saved, first.await())
        assertEquals(AutomaticArtworkResult.Existing, second.await())
        assertEquals(1, calls)
    }

    @Test fun cancellationDuringDownloadLeavesNoChoiceAndTheNextOwnerCanRetry() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val cache = manager()
        val fetcher = AutomaticArtworkFetcher({ ArtworkSearchResult.Found(listOf(candidate)) }, downloader { if (++calls == 1) gate.await() }, cache, { 0 }, { true })
        val first = async { fetcher.fetch(track, { true }, { false }) }
        runCurrent(); first.cancelAndJoin()
        assertTrue(cache.state.value.isEmpty())
        assertEquals(AutomaticArtworkResult.Saved, fetcher.fetch(track, { true }, { false }))
        assertEquals(2, calls)
    }

    @Test fun anExistingUnloadedManualIndexIsReadBeforeAutomaticLookup() = runTest {
        val store = FakeArtworkFileStore()
        val earlier = manager(store)
        val manual = earlier.save(assertNotNull(ArtworkSubject.of(track.groupingArtist, track.album)), bytes)
        val restarted = manager(store)
        val fetcher = AutomaticArtworkFetcher({ error("Provider must not run before loading existing choices") }, downloader(), restarted, { 0 }, { true })
        assertEquals(AutomaticArtworkResult.Existing, fetcher.fetch(track, { true }, { false }))
        assertEquals(manual, ArtworkCovers.downloadedForTrack(track, restarted.state.value))
    }

    @Test fun automaticEntriesCannotEvictExplicitChoicesOrBeatALaterExplicitAlbum() = runTest {
        val store = FakeArtworkFileStore()
        val cache = DownloadedArtworks(store, now = { 1 }, maxEntries = 2)
        val trackSubject = assertNotNull(ArtworkSubject.forTrack(track))
        val automatic = assertNotNull(cache.saveAutomaticIfAbsent(trackSubject, bytes) { true })
        val album = assertNotNull(cache.save(assertNotNull(ArtworkSubject.of(track.groupingArtist, track.album)), byteArrayOf(9)))
        assertEquals(album, ArtworkCovers.downloadedForTrack(track, cache.state.value))
        val other = assertNotNull(ArtworkSubject.forTrack(track.copy(id = "other", title = "Other")))
        assertNotNull(cache.saveAutomaticIfAbsent(other, bytes) { true })
        assertNull(cache.get(automatic.key))
        assertEquals(album, cache.get(album.key))
        val full = DownloadedArtworks(FakeArtworkFileStore(), now = { 1 }, maxEntries = 1)
        val explicit = assertNotNull(full.save(album.subject, bytes))
        assertNull(full.saveAutomaticIfAbsent(trackSubject, bytes) { true })
        assertEquals(explicit, full.get(explicit.key))
    }

    @Test fun aNoMatchAfterPolicyChangesDoesNotSuppressTheNextAllowedLookup() = runTest {
        var allowed = true
        var calls = 0
        val fetcher = AutomaticArtworkFetcher({
            calls++
            if (calls == 1) { allowed = false; ArtworkSearchResult.NoMatch }
            else ArtworkSearchResult.Found(listOf(candidate))
        }, downloader(), manager(), { 0 }, { true })
        assertEquals(AutomaticArtworkResult.Blocked, fetcher.fetch(track, { allowed }, { false }))
        allowed = true
        assertEquals(AutomaticArtworkResult.Saved, fetcher.fetch(track, { allowed }, { false }))
        assertEquals(2, calls)
    }

    @Test fun policyLossWhileWritingDiscardsOnlyTheNewUnpublishedFile() = runTest {
        val backing = FakeArtworkFileStore()
        var allowed = true
        val store = object : ArtworkFileStore by backing {
            override suspend fun write(name: String, bytes: ByteArray): Boolean {
                val written = backing.write(name, bytes)
                if (name != DownloadedArtworks.INDEX_FILE) allowed = false
                return written
            }
        }
        val cache = DownloadedArtworks(store, now = { 1 })
        val subject = assertNotNull(ArtworkSubject.forTrack(track))
        assertNull(cache.saveAutomaticIfAbsent(subject, bytes) { allowed })
        assertTrue(cache.state.value.isEmpty())
        assertFalse(backing.contains(artworkFileName(subject.key)))
    }

    @Test fun aCacheFullOfExplicitCoversDoesNotStartAutomaticNetworkRequests() = runTest {
        val cache = DownloadedArtworks(FakeArtworkFileStore(), now = { 1 }, maxEntries = 1)
        val explicit = assertNotNull(cache.save(assertNotNull(ArtworkSubject.of("Other", "Album")), bytes))
        val fetcher = AutomaticArtworkFetcher({ error("Confirmed covers must not be evicted") }, downloader(), cache, { 0 }, { true })
        assertEquals(AutomaticArtworkResult.Blocked, fetcher.fetch(track, { true }, { false }))
        assertEquals(explicit, cache.get(explicit.key))
    }

}
