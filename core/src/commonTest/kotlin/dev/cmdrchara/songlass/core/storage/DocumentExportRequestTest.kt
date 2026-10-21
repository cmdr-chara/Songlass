package dev.cmdrchara.songlass.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentExportRequestTest {
    @Test
    fun aDuplicateResultCannotClaimThePayloadTwice() {
        val request = DocumentExportRequest("#EXTM3U\n/song-a.flac\n")
        assertEquals("#EXTM3U\n/song-a.flac\n", request.claim())
        assertNull(request.claim())
    }

    @Test
    fun aLateResultAfterRecreationCannotWriteANewRequestsContent() {
        val writes = mutableListOf<Pair<String, String>>()
        fun callback(request: DocumentExportRequest): (String) -> Unit = { destination ->
            request.claim()?.let { writes += destination to it }
        }
        val oldRequest = DocumentExportRequest("playlist A")
        val oldResult = callback(oldRequest)
        oldRequest.invalidate() // The old Activity/UI registration is disposed.
        val newRequest = DocumentExportRequest("playlist B")
        val newResult = callback(newRequest)
        oldResult("destination A")
        assertTrue(writes.isEmpty())
        newResult("destination B")
        oldResult("destination A")
        assertEquals(listOf("destination B" to "playlist B"), writes)
    }

    @Test
    fun cancellationDoesNotConsumeAnotherRequestsPayload() {
        val cancelled = DocumentExportRequest("cancelled")
        val retry = DocumentExportRequest("retry")
        cancelled.invalidate()
        cancelled.invalidate()
        assertNull(cancelled.claim())
        assertEquals("retry", retry.claim())
    }

    @Test
    fun aFailedProviderWriteNeedsANewExplicitRequest() {
        val failed = DocumentExportRequest("playlist")
        assertEquals("playlist", failed.claim()) // The provider can fail after accepting a result.
        failed.invalidate()
        assertNull(failed.claim())
        val explicitRetry = DocumentExportRequest("playlist")
        assertEquals("playlist", explicitRetry.claim())
    }
}
