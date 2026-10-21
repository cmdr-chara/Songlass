package dev.cmdrchara.songlass.core.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PreferenceBatchTest {
    @Test fun failedRemovalAndWriteBatchRestoresOnlyItsOwnKeysAndCanBeRetried() {
        val store = FaultingPreferenceStore()
        store.putStrings(mapOf("obsolete" to "old", "selected" to "before", "foreign" to "keep"))
        val before = store.snapshot()
        store.failAfterChanges(2)
        assertFailsWith<IllegalStateException> {
            store.updateStrings(setOf("obsolete"), mapOf("selected" to "after", "new" to "value"))
        }
        assertEquals(2, store.changedBeforeFailure)
        assertEquals(before, store.snapshot())
        store.updateStrings(setOf("obsolete"), mapOf("selected" to "after", "new" to "value"))
        assertNull(store.getString("obsolete"))
        assertEquals(mapOf("selected" to "after", "new" to "value", "foreign" to "keep"), store.snapshot())
    }

    @Test fun existingPutStringsUsesTheOrdinaryNativeBatchWithoutSynchronousReplacement() {
        val backing = InMemoryKeyValueStore()
        var batches = 0
        val store = object : KeyValueStore {
            override fun getString(key: String) = backing.getString(key)
            override fun putString(key: String, value: String?) { error("Individual writes are not a native batch") }
            override fun replace(removing: Set<String>, writing: Map<String, String>) { error("No synchronous replacement") }
            override fun updateStrings(removing: Set<String>, writing: Map<String, String>) {
                batches++
                backing.updateStrings(removing, writing)
            }
        }
        store.putStrings(mapOf("a" to "old", "b" to "remove"))
        store.updateStrings(setOf("a", "b"), mapOf("a" to "new"))
        assertEquals(2, batches)
        assertEquals(mapOf("a" to "new"), backing.snapshot())
    }
}

/**
 * Exercises the real default batch/rollback, never interface delegation to another store.
 * The one-shot fault happens after actual values have changed, even for a last-key write.
 */
internal class FaultingPreferenceStore : KeyValueStore {
    private val backing = InMemoryKeyValueStore()
    private var remainingChanges: Int? = null
    var changedBeforeFailure: Int = 0
        private set
    var afterWrite: (() -> Unit)? = null

    fun failAfterChanges(count: Int) {
        require(count > 0)
        remainingChanges = count
        changedBeforeFailure = 0
    }

    override fun getString(key: String): String? = backing.getString(key)
    override fun snapshot(): Map<String, String> = backing.snapshot()
    override fun putString(key: String, value: String?) {
        val changed = backing.getString(key) != value
        backing.putString(key, value)
        afterWrite?.invoke()
        val remaining = remainingChanges
        if (changed && remaining != null) {
            changedBeforeFailure++
            remainingChanges = (remaining - 1).takeIf { it > 0 }
            if (remaining == 1) error("Injected failure after a preference value changed")
        }
    }
}
