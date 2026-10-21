package dev.cmdrchara.songlass.core.playlists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class M3uExportRequestTest {
    @Test
    fun aDuplicateResultCannotClaimThePayloadTwice() {
        val request = M3uExportRequest("#EXTM3U\n/song-a.flac\n")
        assertEquals("#EXTM3U\n/song-a.flac\n", request.claim())
        assertNull(request.claim())
    }

    @Test
    fun aLateResultAfterRecreationCannotWriteANewRequestsContent() {
        val writes = mutableListOf<Pair<String, String>>()
        fun callback(request: M3uExportRequest): (String) -> Unit = { destination ->
            request.claim()?.let { writes += destination to it }
        }
        val oldRequest = M3uExportRequest("playlist A")
        val oldResult = callback(oldRequest)
        oldRequest.invalidate() // The old Activity/UI registration is disposed.
        val newRequest = M3uExportRequest("playlist B")
        val newResult = callback(newRequest)
        oldResult("destination A")
        assertTrue(writes.isEmpty())
        newResult("destination B")
        oldResult("destination A")
        assertEquals(listOf("destination B" to "playlist B"), writes)
    }

    @Test
    fun cancellationDoesNotConsumeAnotherRequestsPayload() {
        val cancelled = M3uExportRequest("cancelled")
        val retry = M3uExportRequest("retry")
        cancelled.invalidate()
        cancelled.invalidate()
        assertNull(cancelled.claim())
        assertEquals("retry", retry.claim())
    }

    @Test
    fun aFailedProviderWriteNeedsANewExplicitRequest() {
        val failed = M3uExportRequest("playlist")
        assertEquals("playlist", failed.claim()) // The provider can fail after accepting a result.
        failed.invalidate()
        assertNull(failed.claim())
        val explicitRetry = M3uExportRequest("playlist")
        assertEquals("playlist", explicitRetry.claim())
    }
}
