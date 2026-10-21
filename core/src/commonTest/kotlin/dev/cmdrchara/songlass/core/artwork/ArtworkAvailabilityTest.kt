package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ArtworkAvailabilityTest {
    private val a = Track("canonical-a", "A", "content://media/external/audio/media/7", artworkRevision = 19)

    @Test
    fun reusedPlatformUriCannotAliasAnotherCanonicalFilesCachedCover() {
        val replacement = a.copy(id = "canonical-b", title = "B")
        assertNotEquals(a.artworkKey, replacement.artworkKey)
        assertEquals(a.uri, replacement.uri, "the actual playable URI is not rewritten")
        assertNotEquals(a.artworkKey, a.copy(artworkRevision = 20).artworkKey)
        assertEquals(a.artworkKey, a.copy(title = "Changed display only").artworkKey)
        assertTrue(ArtworkKeys.isTrackKey(a.artworkKey))
        assertFalse(ArtworkKeys.isTrackKey(a.uri))
    }

    @Test
    fun opaqueIdsCannotInjectFragmentsOrCollideWithRevisionSyntax() {
        val ids = listOf("a:b", "a#b", "a/b", "a%2Fb", "01", "1", "è🎶", "e🎶")
        val keys = ids.map { a.copy(id = it).artworkKey }
        assertEquals(ids.size, keys.distinct().size)
        keys.forEach { assertEquals(1, it.count { char -> char == '#' }) }
    }

    @Test
    fun incompleteOrAmbiguousLibraryNeverAuthorizesAnOpen() {
        val availability = ArtworkAvailability()
        assertNull(availability.permitForId(a.id))
        availability.publish(ArtworkAvailability.Index.build(listOf(a, a.copy(uri = "content://other"))))
        assertNull(availability.permitForKey(a.artworkKey), "duplicate canonical ID")
        availability.publish(ArtworkAvailability.Index.build(listOf(a, a.copy(id = "another"))))
        assertNull(availability.permitForId(a.id), "ambiguous shared source URI")
        availability.publish(ArtworkAvailability.Index.build(listOf(a.copy(id = ""))))
        assertNull(availability.permitForId(""))
    }

    @Test
    fun recoveryWithUnchangedMetadataRevokesOldPermitsAndPublishesRetryRevision() {
        val availability = ArtworkAvailability()
        val index = ArtworkAvailability.Index.build(listOf(a))
        availability.publish(index)
        val first = assertNotNull(availability.permitForKey(a.artworkKey))
        val initialRevision = availability.revision.value
        availability.invalidate()
        assertFalse(availability.isCurrent(first))
        assertNull(availability.permitForKey(a.artworkKey))
        availability.publish(index)
        assertTrue(availability.revision.value > initialRevision)
        assertFalse(availability.isCurrent(first), "even an identical scan is a new read boundary")
        assertEquals(a, availability.permitForId(a.id)?.track)
    }

    @Test
    fun revokedReadinessPreventsIoBeforeObserversProcessTheChange() = runTest {
        val availability = ArtworkAvailability()
        availability.publish(ArtworkAvailability.Index.build(listOf(a)))
        val permit = assertNotNull(availability.permitForId(a.id))
        var reads = 0
        assertNull(availability.readIfCurrent(permit, canRead = { false }) { reads++; "cover" })
        assertEquals(0, reads)
    }

    @Test
    fun permissionLossWhileDecodingDiscardsTheResultWithoutAFlowDelivery() = runTest {
        val availability = ArtworkAvailability()
        availability.publish(ArtworkAvailability.Index.build(listOf(a)))
        val permit = assertNotNull(availability.permitForId(a.id))
        var permitted = true
        val decode = CompletableDeferred<String>()
        val result = async { availability.readIfCurrent(permit, { permitted }) { decode.await() } }
        runCurrent()
        permitted = false
        decode.complete("must not be cached")
        assertNull(result.await())
    }

    @Test
    fun replacedSnapshotDiscardsAnInFlightCoverEvenWhenPermissionIsStillGranted() = runTest {
        val availability = ArtworkAvailability()
        availability.publish(ArtworkAvailability.Index.build(listOf(a)))
        val permit = assertNotNull(availability.permitForId(a.id))
        val decode = CompletableDeferred<String>()
        val result = async { availability.readIfCurrent(permit, { true }) { decode.await() } }
        runCurrent()
        val b = a.copy(id = "canonical-b", title = "B")
        availability.publish(ArtworkAvailability.Index.build(listOf(b)))
        decode.complete("A's decode finished late")
        assertNull(result.await())
        assertNull(availability.permitForKey(a.artworkKey))
        val replacement = assertNotNull(availability.permitForKey(b.artworkKey))
        assertEquals("B", availability.readIfCurrent(replacement, { true }) { it.title })
    }
}
