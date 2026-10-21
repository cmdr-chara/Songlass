package dev.cmdrchara.songlass.core.lyrics

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Coordinates this process's temporary document-read grants across picker/Activity instances.
 * Platform permission calls are supplied by the caller; this class never opens a document.
 * A grant that already belonged to another flow is never released by this coordinator.
 */
class LyricsReadGrants {
    private class Lease(var owned: Boolean, val tokens: MutableSet<String>)
    private val mutex = Mutex()
    private val leases = mutableMapOf<String, Lease>()

    /**
     * [acquire] returns true only when this flow acquired a previously absent persistent grant.
     * A saved ownership flag allows a pending request to finish cleanup after process recreation.
     * A repeated token is the same logical pending request, not an additional owner.
     */
    suspend fun retain(
        reference: String,
        token: String,
        restoredOwnership: Boolean,
        acquire: suspend () -> Boolean,
    ): Boolean {
        require(reference.isNotBlank() && reference.length <= 16 * 1024)
        require(token.isNotBlank() && token.length <= 128)
        // Finish local permission bookkeeping even if the old Activity is disposed mid-call.
        // Its saved URI/token lets the replacement host adopt or release this same lease.
        return withContext(NonCancellable) {
            mutex.withLock {
                val existing = leases[reference]
                if (existing != null) {
                    existing.tokens += token
                    existing.owned = existing.owned || restoredOwnership
                    existing.owned
                } else {
                    val acquired = acquire()
                    val owned = acquired || restoredOwnership
                    leases[reference] = Lease(owned, mutableSetOf(token))
                    owned
                }
            }
        }
    }

    /**
     * An obsolete token cannot release a newer request's grant. The last active owner performs
     * platform cleanup. Foreign grants and a request cancelled before retain are left alone.
     */
    suspend fun release(
        reference: String,
        token: String,
        restoredOwnership: Boolean,
        release: suspend () -> Unit,
    ) {
        if (reference.isBlank() || reference.length > 16 * 1024 || token.isBlank() || token.length > 128) return
        withContext(NonCancellable) {
            mutex.withLock {
                val existing = leases[reference]
                if (existing == null) {
                    // No in-memory lease after process recreation. Only an explicitly saved
                    // ownership flag is sufficient to release a persistent platform grant.
                    if (restoredOwnership) release()
                } else if (existing.tokens.remove(token) && existing.tokens.isEmpty()) {
                    leases.remove(reference)
                    if (existing.owned) release()
                }
            }
        }
    }
}
