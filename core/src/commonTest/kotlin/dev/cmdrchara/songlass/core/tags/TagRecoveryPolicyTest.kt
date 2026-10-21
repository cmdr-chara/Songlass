package dev.cmdrchara.songlass.core.tags

import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException.Reason
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class TagRecoveryPolicyTest {
    private val installation = "11111111-2222-4333-8444-555555555555"
    private val original = TagRecoveryFingerprint(20, "a".repeat(64))
    private val staged = TagRecoveryFingerprint(25, "b".repeat(64))

    @Test
    fun oldMalformedOrTransferredJournalNeverGetsInstallationAuthorization() {
        assertEquals(Reason.LegacyJournal, assertFailsWith<TagRecoveryRequiredException> {
            TagRecoveryPolicy.requireVersion(null)
        }.reason)
        for (version in listOf(-1L, 0L, 1L, 3L, Long.MAX_VALUE)) {
            assertEquals(Reason.InvalidJournal, assertFailsWith<TagRecoveryRequiredException> {
                TagRecoveryPolicy.requireVersion(version)
            }.reason)
        }
        TagRecoveryPolicy.requireVersion(2)
        for (local in listOf(null, "", "corrupt", "11111111-2222-4333-8444-666666666666")) {
            assertEquals(Reason.DifferentInstallation, assertFailsWith<TagRecoveryRequiredException> {
                TagRecoveryPolicy.requireInstallation(installation, local)
            }.reason)
        }
        // An equal malformed value is not an installation witness.
        assertFailsWith<TagRecoveryRequiredException> { TagRecoveryPolicy.requireInstallation("invalid", "invalid") }
        TagRecoveryPolicy.requireInstallation(installation, installation)
    }

    @Test
    fun intactOriginalAndCompleteCommitRequireNoPartialImageGuess() {
        var consulted = false
        fun decide(destination: TagRecoveryFingerprint) = TagRecoveryPolicy.decide(original, staged, null, destination) { _, _ ->
            consulted = true
            false
        }
        assertEquals(TagRecoveryAction.AlreadyOriginal, decide(original))
        assertEquals(TagRecoveryAction.AlreadyCommitted, decide(staged))
        assertFalse(consulted)
    }

    @Test
    fun onlyVerifiedPartialImageCanAuthorizeRestoring() {
        val partial = TagRecoveryFingerprint(22, "c".repeat(64))
        assertEquals(TagRecoveryAction.RestoreOriginal,
            TagRecoveryPolicy.decide(original, staged, null, partial) { baseline, replacement ->
                assertEquals(original, baseline)
                assertEquals(staged, replacement)
                true
            })
        assertEquals(Reason.UnexpectedContent, assertFailsWith<TagRecoveryRequiredException> {
            TagRecoveryPolicy.decide(original, staged, null, partial) { _, _ -> false }
        }.reason)
    }

    @Test
    fun repeatedInterruptionUsesPersistedRollbackBaselineAndOriginalAsReplacement() {
        val previousPartial = TagRecoveryFingerprint(22, "c".repeat(64))
        val interruptedRollback = TagRecoveryFingerprint(22, "d".repeat(64))
        assertEquals(TagRecoveryAction.RestoreOriginal,
            TagRecoveryPolicy.decide(original, staged, previousPartial, interruptedRollback) { baseline, replacement ->
                assertEquals(previousPartial, baseline)
                assertEquals(original, replacement)
                true
            })
        assertEquals(TagRecoveryAction.AlreadyOriginal,
            TagRecoveryPolicy.decide(original, staged, previousPartial, original) { _, _ -> error("No write needed") })
    }

    @Test
    fun rollbackIntentDoesNotMistakeStagedBytesForACompletedCommit() {
        assertEquals(TagRecoveryAction.RestoreOriginal,
            TagRecoveryPolicy.decide(original, staged, staged, staged) { baseline, replacement ->
                assertEquals(staged, baseline)
                assertEquals(original, replacement)
                true
            })
    }

    @Test
    fun sizeAndHashBothMatterAndUnknownBytesCannotBecomeAnExactImage() {
        for (changed in listOf(
            original.copy(bytes = 19), original.copy(sha256 = "e".repeat(64)),
            staged.copy(bytes = 20), staged.copy(sha256 = "f".repeat(64)),
            TagRecoveryFingerprint(0, "0".repeat(64)),
        )) {
            assertEquals(Reason.UnexpectedContent, assertFailsWith<TagRecoveryRequiredException> {
                TagRecoveryPolicy.decide(original, staged, null, changed) { _, _ -> false }
            }.reason)
        }
        assertFailsWith<IllegalArgumentException> { TagRecoveryFingerprint(-1, "a".repeat(64)) }
        assertFailsWith<IllegalArgumentException> { TagRecoveryFingerprint(20, "invalid") }
    }
}
