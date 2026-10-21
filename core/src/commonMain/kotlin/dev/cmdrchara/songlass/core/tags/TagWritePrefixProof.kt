package dev.cmdrchara.songlass.core.tags

/**
 * Verifies a possible interrupted, sequential overwrite before its final truncate:
 * a prefix of [replacementSize] bytes followed by the untouched original suffix.
 *
 * The caller separately verifies both private copies' hashes and the destination's identity.
 * This proof alone does not identify a file. It rejects arbitrary damage and non-prefix writes;
 * rejecting a torn disk write is safer than guessing that another file belongs to the journal.
 * Buffers are aligned to the same file offset and need not contain the entire file.
 */
class TagWritePrefixProof(
    private val originalSize: Long,
    private val replacementSize: Long,
    private val destinationSize: Long,
) {
    private var offset = 0L
    private var valid = originalSize >= 0 && replacementSize >= 0 && destinationSize >= 0 &&
        destinationSize >= originalSize && destinationSize <= maxOf(originalSize, replacementSize)
    private var minimumPrefix = if (destinationSize > originalSize) destinationSize else 0L
    private var maximumPrefix = minOf(destinationSize, replacementSize)

    /** False also means an incomplete stream: every destination byte must be checked. */
    val complete: Boolean get() = valid && offset == destinationSize && minimumPrefix <= maximumPrefix

    fun accept(
        original: ByteArray,
        originalCount: Int,
        replacement: ByteArray,
        replacementCount: Int,
        destination: ByteArray,
        destinationCount: Int,
    ): Boolean {
        if (!valid || destinationCount <= 0 || destinationCount > destination.size ||
            destinationCount.toLong() > destinationSize - offset ||
            originalCount !in 0..original.size || replacementCount !in 0..replacement.size ||
            originalCount != minOf(destinationCount.toLong(), (originalSize - offset).coerceAtLeast(0)).toInt() ||
            replacementCount != minOf(destinationCount.toLong(), (replacementSize - offset).coerceAtLeast(0)).toInt()
        ) {
            valid = false
            return false
        }
        for (index in 0 until destinationCount) {
            val position = offset + index
            // Before the boundary only replacement bytes are possible; afterwards only original.
            if (index >= replacementCount || destination[index] != replacement[index]) {
                maximumPrefix = minOf(maximumPrefix, position)
            }
            if (index >= originalCount || destination[index] != original[index]) {
                minimumPrefix = maxOf(minimumPrefix, position + 1)
            }
            if (minimumPrefix > maximumPrefix) {
                valid = false
                return false
            }
        }
        offset += destinationCount
        return true
    }
}

/** Recovery is deliberately stopped; private originals must remain available for manual recovery. */
class TagRecoveryRequiredException(val reason: Reason) : IllegalStateException(
    "Automatic tag recovery stopped: ${reason.description}. The private recovery copies were retained.",
) {
    enum class Reason(val description: String) {
        LegacyJournal("the old journal does not identify its destination safely"),
        InvalidJournal("the recovery journal is incomplete or malformed"),
        DifferentInstallation("the journal does not belong to this app installation"),
        MissingCopy("a required recovery copy is missing"),
        DamagedCopy("a recovery copy failed its recorded size or checksum"),
        DestinationChanged("the destination identity changed or cannot be established"),
        UnexpectedContent("the destination is not a verified image of the interrupted transaction"),
        PendingOtherFile("another file has an unresolved interrupted edit"),
    }
}
