package dev.cmdrchara.songlass.core.storage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A rejected create/save must not return an ID for data that was never written. */
class RestoreMutationRejectedException : IllegalStateException("Application data is being restored")

data class RestoreMutationState(val revision: Long = 0L, val restoring: Boolean = false)

/** Process-local witness, captured before launching asynchronous work or a document picker. */
class MutationTicket internal constructor(
    internal val owner: RestoreMutationGate,
    internal val revision: Long,
)

/**
 * The application's owner-thread boundary for replacement of persistent user data.
 *
 * begin/end and ordinary repository read/modify/write operations run on the same owner thread
 * (Main in AppGraph). A guard followed by a synchronous mutation cannot interleave with begin.
 * This is deliberately not a storage lock: no Main-thread caller waits for disk/network work,
 * and rejected mutations neither write nor publish a pretend successful state. Background
 * writers need their own suspending serialization; lyrics import already shares a Mutex with
 * restore. Immutable tickets may be read on workers, but acceptance is checked again on Main
 * before publishing a result or committing an ordinary mutation.
 */
class RestoreMutationGate {
    private val _state = MutableStateFlow(RestoreMutationState())
    val state: StateFlow<RestoreMutationState> = _state.asStateFlow()
    val canMutate: Boolean get() = !_state.value.restoring

    fun ticket(): MutationTicket? = _state.value.takeUnless { it.restoring }
        ?.let { MutationTicket(this, it.revision) }

    fun accepts(ticket: MutationTicket?): Boolean {
        val current = _state.value
        return ticket != null && ticket.owner === this && !current.restoring && ticket.revision == current.revision
    }

    /** Only ID-returning form APIs use this; toggles/Unit APIs reject without throwing. */
    fun requireMutable() {
        if (!canMutate) throw RestoreMutationRejectedException()
    }

    /**
     * Invalidates every earlier ticket before the first suspension, including on failure.
     * A second restore is rejected, never queued with a stale confirmation. The caller decides
     * whether the accepted replacement must finish despite cancellation of its UI.
     */
    suspend fun <T> restoring(block: suspend () -> T): T {
        requireMutable()
        val revision = _state.value.revision + 1L
        _state.value = RestoreMutationState(revision, restoring = true)
        return try {
            block()
        } finally {
            _state.value = RestoreMutationState(revision, restoring = false)
        }
    }
}
