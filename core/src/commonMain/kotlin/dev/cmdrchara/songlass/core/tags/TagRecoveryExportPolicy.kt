package dev.cmdrchara.songlass.core.tags

/** A receipt is issued only after rereading the new document, never on picker selection alone. */
data class TagRecoveryExportReceipt(val recoveryId: String, val document: TagRecoveryFingerprint)

object TagRecoveryExportPolicy {
    fun verify(
        recoveryId: String,
        written: TagRecoveryFingerprint,
        readBack: TagRecoveryFingerprint,
    ): TagRecoveryExportReceipt {
        require(recoveryId.isNotBlank() && written.bytes > 0 && written == readBack) { "Recovery export was not verified" }
        return TagRecoveryExportReceipt(recoveryId, written)
    }

    fun canArchive(
        recoveryId: String,
        receipt: TagRecoveryExportReceipt?,
        currentDocument: TagRecoveryFingerprint?,
    ): Boolean = receipt != null && recoveryId == receipt.recoveryId &&
        receipt.document.bytes > 0 && currentDocument == receipt.document
}
