package dev.cmdrchara.songlass.core.tags

import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException.Reason

/** SHA-256 of the entire exact-length file, not a tag/audio-packet fingerprint. */
data class TagRecoveryFingerprint(val bytes: Long, val sha256: String) {
    init {
        require(bytes >= 0 && sha256.matches(Regex("[0-9a-f]{64}")))
    }
}

enum class TagRecoveryAction { AlreadyOriginal, AlreadyCommitted, RestoreOriginal }

/** Pure decisions; the adapter must additionally bind the URI to the original filesystem identity. */
object TagRecoveryPolicy {
    private val installationId = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun requireVersion(version: Long?) {
        if (version == null) throw TagRecoveryRequiredException(Reason.LegacyJournal)
        if (version != 2L) throw TagRecoveryRequiredException(Reason.InvalidJournal)
    }

    fun validInstallationId(value: String): Boolean = installationId.matches(value)

    fun requireInstallation(recorded: String, local: String?) {
        if (!validInstallationId(recorded) || local == null || !validInstallationId(local) || recorded != local) {
            throw TagRecoveryRequiredException(Reason.DifferentInstallation)
        }
    }

    /**
     * Exact original/staged images need no writes. A rollback journal always intends to restore
     * the original, even when its baseline happens to equal the complete staged file. The lazy
     * proof is only consulted for an unfinished image, and a failed proof never grants a write.
     */
    fun decide(
        original: TagRecoveryFingerprint,
        staged: TagRecoveryFingerprint,
        rollbackBaseline: TagRecoveryFingerprint?,
        destination: TagRecoveryFingerprint,
        provesPrefix: (baseline: TagRecoveryFingerprint, replacement: TagRecoveryFingerprint) -> Boolean,
    ): TagRecoveryAction {
        if (destination == original) return TagRecoveryAction.AlreadyOriginal
        if (rollbackBaseline == null && destination == staged) return TagRecoveryAction.AlreadyCommitted
        val baseline = rollbackBaseline ?: original
        val replacement = if (rollbackBaseline == null) staged else original
        if (!provesPrefix(baseline, replacement)) throw TagRecoveryRequiredException(Reason.UnexpectedContent)
        return TagRecoveryAction.RestoreOriginal
    }
}
