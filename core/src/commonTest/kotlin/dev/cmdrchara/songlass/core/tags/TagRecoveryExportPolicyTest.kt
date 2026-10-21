package dev.cmdrchara.songlass.core.tags

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TagRecoveryExportPolicyTest {
    private val document = TagRecoveryFingerprint(2_048, "a".repeat(64))

    @Test
    fun cancelledPickerOrInterruptedCopyHasNoReceiptAndCannotArchive() {
        assertFalse(TagRecoveryExportPolicy.canArchive("pending", null, null))
        assertFalse(TagRecoveryExportPolicy.canArchive("pending", null, document))
    }

    @Test
    fun partialWriteOrChangedReadbackCannotCreateAReceipt() {
        for (readBack in listOf(document.copy(bytes = 2_047), document.copy(sha256 = "b".repeat(64)))) {
            assertFailsWith<IllegalArgumentException> { TagRecoveryExportPolicy.verify("pending", document, readBack) }
        }
        assertFailsWith<IllegalArgumentException> { TagRecoveryExportPolicy.verify("", document, document) }
        val empty = document.copy(bytes = 0)
        assertFailsWith<IllegalArgumentException> { TagRecoveryExportPolicy.verify("pending", empty, empty) }
    }

    @Test
    fun successfulExportStillRequiresCurrentRecoveryAndReadableUnchangedDocument() {
        val receipt = TagRecoveryExportPolicy.verify("pending", document, document)
        assertTrue(TagRecoveryExportPolicy.canArchive("pending", receipt, document))
        assertFalse(TagRecoveryExportPolicy.canArchive("new-transaction", receipt, document))
        assertFalse(TagRecoveryExportPolicy.canArchive("pending", receipt, null))
        assertFalse(TagRecoveryExportPolicy.canArchive("pending", receipt, document.copy(bytes = 1)))
        assertFalse(TagRecoveryExportPolicy.canArchive("pending", receipt, document.copy(sha256 = "c".repeat(64))))
    }
}
