package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore

/**
 * Stages the exact reviewed replacement and repository decoding before touching real data.
 * Must be created, decoded and committed off Main, while the caller excludes managed writers.
 * Unmanaged keys can keep changing: commit never writes or removes them.
 */
class PreparedBackupRestore(
    private val target: KeyValueStore,
    reviewed: BackupPreview,
    current: BackupPreview,
) {
    init {
        // Preserve applyConfirmed's boundary: a changed confirmation must not even read
        // the destination snapshot, let alone prepare or write replacement data.
        if (!reviewed.sameRestorePlanAs(current)) throw BackupPreviewChangedException(current)
    }

    private val original = (target.snapshot() ?: throw BackupException(BackupError.StoreUnsupported))
        .filterKeys(BackupKeys::isManaged)
    val store: KeyValueStore = InMemoryKeyValueStore().also { staged ->
        staged.putStrings(original)
        // Reuse the canonical confirmation/allowlist behavior; this only changes the staging
        // map. Repository prepareReload calls may normalize their own managed keys here.
        DataBackup(staged).applyConfirmed(reviewed, current)
    }
    private var committed = false

    /** Publish the prepared repository states on their owner thread only after this succeeds. */
    fun commit(): BackupResult {
        check(!committed) { "Prepared restore already committed" }
        val writing = requireNotNull(store.snapshot()).filterKeys(BackupKeys::isManaged)
        val removing = original.keys.filterTo(HashSet()) { BackupKeys.isManaged(it) && it !in writing }
        try {
            // Android is one synchronous commit; other stores retain their documented
            // replacement/rollback behavior. This does not promise disk atomicity on iOS.
            target.replace(removing, writing)
        } catch (failure: Exception) {
            throw BackupException(BackupError.WriteFailed, failure)
        }
        committed = true
        return BackupResult(written = writing.size, removed = removing.size)
    }
}
