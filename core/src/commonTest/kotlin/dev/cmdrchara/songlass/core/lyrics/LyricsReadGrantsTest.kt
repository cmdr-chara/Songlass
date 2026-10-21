package dev.cmdrchara.songlass.core.lyrics

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pure lease-ordering tests. These callbacks do not emulate Android permission/provider behavior. */
class LyricsReadGrantsTest {
    private val document = "content://documents/lyrics"

    @Test fun freshOwnedGrantIsReleasedAfterItsOnlyRequestFinishes() = runTest {
        val grants = LyricsReadGrants()
        var acquisitions = 0
        var releases = 0
        assertTrue(grants.retain(document, "first", false) { acquisitions++; true })
        grants.release(document, "first", true) { releases++ }
        assertEquals(1, acquisitions)
        assertEquals(1, releases)
    }

    @Test fun alreadyPresentForeignGrantIsNeverReleased() = runTest {
        val grants = LyricsReadGrants()
        var releases = 0
        assertFalse(grants.retain(document, "first", false) { false })
        assertFalse(grants.retain(document, "second", false) { error("Already retained") })
        grants.release(document, "first", false) { releases++ }
        grants.release(document, "second", false) { releases++ }
        assertEquals(0, releases)
    }

    @Test fun newerRequestRetainsSameOwnedDocumentBeforeOlderCleanup() = runTest {
        val grants = LyricsReadGrants()
        var releases = 0
        assertTrue(grants.retain(document, "old", false) { true })
        assertTrue(grants.retain(document, "new", false) { error("The existing owned lease is shared") })
        grants.release(document, "old", true) { releases++ }
        assertEquals(0, releases, "Old cleanup must not revoke the new request's permission")
        grants.release(document, "new", true) { releases++ }
        assertEquals(1, releases)
    }

    @Test fun newAcquisitionWaitsUntilAnOlderPlatformReleaseCompletes() = runTest {
        val grants = LyricsReadGrants()
        val releaseStarted = CompletableDeferred<Unit>()
        val finishRelease = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        grants.retain(document, "old", false) { true }
        val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
            grants.release(document, "old", true) {
                events += "release-start"
                releaseStarted.complete(Unit)
                finishRelease.await()
                events += "release-end"
            }
        }
        assertTrue(releaseStarted.isCompleted)
        val incoming = async(start = CoroutineStart.UNDISPATCHED) {
            grants.retain(document, "new", false) { events += "new-acquire"; true }
        }
        assertFalse(incoming.isCompleted)
        assertEquals(listOf("release-start"), events)
        finishRelease.complete(Unit)
        cleanup.await()
        assertTrue(incoming.await())
        assertEquals(listOf("release-start", "release-end", "new-acquire"), events)
    }

    @Test fun cancelledOldActivityAcquisitionStillHasAnOwnerForQueuedCleanup() = runTest {
        val grants = LyricsReadGrants()
        val acquisitionStarted = CompletableDeferred<Unit>()
        val finishAcquisition = CompletableDeferred<Unit>()
        var releases = 0
        val retaining = async(start = CoroutineStart.UNDISPATCHED) {
            grants.retain(document, "saved-token", false) {
                acquisitionStarted.complete(Unit)
                finishAcquisition.await()
                true
            }
        }
        assertTrue(acquisitionStarted.isCompleted)
        retaining.cancel()
        val cleanup = async(start = CoroutineStart.UNDISPATCHED) {
            // The disposed host never received/saved the returned ownership flag.
            grants.release(document, "saved-token", false) { releases++ }
        }
        assertFalse(cleanup.isCompleted)
        finishAcquisition.complete(Unit)
        cleanup.await()
        retaining.join()
        assertTrue(retaining.isCancelled)
        assertEquals(1, releases)
    }

    @Test fun recreatedHostUsesTheSameTokenWithoutInventingASecondOwner() = runTest {
        val grants = LyricsReadGrants()
        var acquisitions = 0
        var releases = 0
        assertTrue(grants.retain(document, "saved-token", false) { acquisitions++; true })
        assertTrue(grants.retain(document, "saved-token", false) { error("Saved logical owner already exists") })
        grants.release(document, "saved-token", true) { releases++ }
        assertEquals(1, acquisitions)
        assertEquals(1, releases)
    }

    @Test fun obsoleteRepeatedCleanupCannotReleaseAReplacementToken() = runTest {
        val grants = LyricsReadGrants()
        var releases = 0
        grants.retain(document, "old", false) { true }
        grants.release(document, "old", true) { releases++ }
        grants.retain(document, "new", false) { true }
        grants.release(document, "old", true) { releases++ }
        assertEquals(1, releases, "A saved old cleanup cannot release a newer active lease")
        grants.release(document, "new", true) { releases++ }
        assertEquals(2, releases)
    }

    @Test fun restoredCleanupRequiresSavedOwnershipWhenNoProcessLeaseExists() = runTest {
        var releases = 0
        LyricsReadGrants().release(document, "restored", false) { releases++ }
        assertEquals(0, releases)
        LyricsReadGrants().release(document, "restored", true) { releases++ }
        assertEquals(1, releases)
    }

    @Test fun differentDocumentsNeverSharePermissionOwnership() = runTest {
        val grants = LyricsReadGrants()
        val released = mutableListOf<String>()
        grants.retain(document, "owned", false) { true }
        grants.retain("content://documents/other", "foreign", false) { false }
        grants.release("content://documents/other", "foreign", false) { released += "foreign" }
        grants.release(document, "owned", true) { released += "owned" }
        assertEquals(listOf("owned"), released)
    }
}
