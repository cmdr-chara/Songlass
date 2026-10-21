package dev.cmdrchara.songlass.core.tags

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagWritePrefixProofTest {
    @Test
    fun everyPossibleInterruptedPrefixIsAcceptedAcrossBufferBoundaries() {
        for (originalSize in listOf(1, 5, 17, 31)) {
            for (replacementSize in listOf(1, 7, 17, 37)) {
                val original = ByteArray(originalSize) { (it + 1).toByte() }
                val replacement = ByteArray(replacementSize) { (it + 91).toByte() }
                for (copied in 0..replacementSize) {
                    val partial = replacement.copyOfRange(0, copied) + original.drop(copied).toByteArray()
                    for (chunkSize in listOf(1, 4, 16, 64)) {
                        assertTrue(proves(original, replacement, partial, chunkSize),
                            "original=$originalSize replacement=$replacementSize prefix=$copied buffer=$chunkSize")
                    }
                }
            }
        }
    }

    @Test
    fun changingOneByteOutsideBothCopiesRejectsRecovery() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val replacement = byteArrayOf(6, 7, 8, 9, 10)
        val partial = byteArrayOf(6, 7, 3, 4, 5)
        for (index in partial.indices) {
            val changed = partial.copyOf().also { it[index] = 99 }
            assertFalse(proves(original, replacement, changed, 2))
        }
    }

    @Test
    fun alternatingOldAndNewRegionsAreNotASequentialOverwrite() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 6)
        val replacement = byteArrayOf(11, 12, 13, 14, 15, 16)
        assertFalse(proves(original, replacement, byteArrayOf(11, 2, 13, 4, 5, 6), 1))
        assertFalse(proves(original, replacement, byteArrayOf(1, 12, 3, 4, 5, 6), 2))
    }

    @Test
    fun equalRegionsDoNotInventAnIncorrectCopyBoundary() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 6)
        val replacement = byteArrayOf(1, 9, 3, 8, 5, 7)
        assertTrue(proves(original, replacement, byteArrayOf(1, 9, 3, 4, 5, 6), 2))
        assertFalse(proves(original, replacement, byteArrayOf(1, 2, 3, 8, 5, 6), 2))
    }

    @Test
    fun interruptedRollbackAlsoHasOneProvenBoundary() {
        val original = byteArrayOf(1, 2, 3, 4, 5, 6)
        val interruptedCommit = byteArrayOf(11, 12, 13, 4, 5, 6)
        for (restored in 0..original.size) {
            val partialRollback = original.take(restored).toByteArray() + interruptedCommit.drop(restored).toByteArray()
            assertTrue(proves(interruptedCommit, original, partialRollback, 2))
        }
    }

    @Test
    fun lengthMustBeExplainedByTheCopyAndNotAnArbitraryTruncate() {
        val original = byteArrayOf(1, 2, 3, 4, 5)
        val longer = byteArrayOf(11, 12, 13, 14, 15, 16, 17)
        assertTrue(proves(original, longer, longer.take(6).toByteArray()))
        assertFalse(proves(original, longer, longer.take(3).toByteArray()))
        assertFalse(proves(original, longer, longer + byteArrayOf(18)))
        // A completed shorter replacement is handled by its exact hash, not this pre-truncate proof.
        val shorter = byteArrayOf(11, 12, 13)
        assertFalse(proves(original, shorter, shorter))
        assertTrue(proves(original, shorter, shorter + byteArrayOf(4, 5)))
    }

    @Test
    fun partialOrInconsistentStreamsNeverProduceAProof() {
        val original = byteArrayOf(1, 2, 3)
        val replacement = byteArrayOf(4, 5, 6)
        val proof = TagWritePrefixProof(3, 3, 3)
        assertTrue(proof.accept(original, 1, replacement, 1, replacement, 1))
        assertFalse(proof.complete)
        assertFalse(proof.accept(original, 3, replacement, 3, replacement, 3))
        assertFalse(proof.complete)
        assertFalse(TagWritePrefixProof(3, 3, 3).accept(original, 2, replacement, 3, replacement, 3))
        assertFalse(TagWritePrefixProof(3, 3, 3).accept(original, 0, replacement, 0, replacement, 0))
    }

    @Test
    fun invalidOrHugeDeclaredLengthsDoNotWrapOrCreateACompleteProof() {
        assertFalse(TagWritePrefixProof(-1, 1, 1).complete)
        assertFalse(TagWritePrefixProof(1, -1, 1).complete)
        assertFalse(TagWritePrefixProof(1, 1, -1).complete)
        val proof = TagWritePrefixProof(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE)
        assertTrue(proof.accept(byteArrayOf(1), 1, byteArrayOf(2), 1, byteArrayOf(2), 1))
        assertFalse(proof.complete)
        assertFalse(TagWritePrefixProof(Long.MAX_VALUE, 1, 0).complete)
    }

    private fun proves(original: ByteArray, replacement: ByteArray, destination: ByteArray, chunkSize: Int = 4): Boolean {
        val proof = TagWritePrefixProof(original.size.toLong(), replacement.size.toLong(), destination.size.toLong())
        var offset = 0
        while (offset < destination.size) {
            val end = minOf(destination.size, offset + chunkSize)
            val old = original.copyOfRange(minOf(offset, original.size), minOf(end, original.size))
            val new = replacement.copyOfRange(minOf(offset, replacement.size), minOf(end, replacement.size))
            val current = destination.copyOfRange(offset, end)
            if (!proof.accept(old, old.size, new, new.size, current, current.size)) return false
            offset = end
        }
        return proof.complete
    }
}
