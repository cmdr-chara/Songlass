package dev.cmdrchara.songlass.core.storage

/**
 * Small persistent key-value storage for preferences and lists of ids.
 * Platforms back it with their native store (SharedPreferences, NSUserDefaults).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)

    /**
     * A related set of ordinary preference writes. Native stores can publish the whole map
     * together while scheduling disk persistence asynchronously; this is not a durability
     * barrier. Use [replace] off the UI thread for explicit replacement (Android commits it
     * synchronously; platforms without native transactions retain their rollback contract).
     * The fallback retains [replace]'s rollback behavior for stores without a native batch.
     */
    fun putStrings(writing: Map<String, String>) = updateStrings(emptySet(), writing)

    /**
     * An ordinary preference batch that also removes obsolete values. Keys in both sets are
     * written. Like [putStrings], native implementations may persist asynchronously; accepting
     * the batch is not a disk durability guarantee. The fallback uses [replace]'s rollback.
     */
    fun updateStrings(removing: Set<String>, writing: Map<String, String>) = replace(removing, writing)

    /**
     * Every string entry currently stored, or null when this store cannot enumerate its keys.
     * Used by backup; entries of other types and platform-owned keys may be left out or ignored.
     */
    fun snapshot(): Map<String, String>? = null

    /**
     * Removes the keys in [removing], then stores every entry of [writing]. Keys in both are
     * written. Native implementations use their transaction where available.
     *
     * The default goes through [putString] and puts the previous values back on failure, which
     * suits stores without a native transaction. This rollback is best effort if the underlying
     * store keeps failing; neither the fallback nor asynchronous native preferences promise a
     * crash-atomic disk transaction.
     */
    fun replace(removing: Set<String>, writing: Map<String, String>) {
        val touched = removing + writing.keys
        val previous = touched.associateWith { getString(it) }
        try {
            removing.forEach { putString(it, null) }
            writing.forEach { (key, value) -> putString(key, value) }
        } catch (failure: Throwable) {
            // Best effort: keep restoring even if one key cannot be put back.
            previous.forEach { (key, value) -> runCatching { putString(key, value) } }
            throw failure
        }
    }
}

/** Volatile store for tests and the demo setup. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }

    override fun snapshot(): Map<String, String> = values.toMap()

    override fun replace(removing: Set<String>, writing: Map<String, String>) {
        val next = values.toMutableMap()
        removing.forEach { next.remove(it) }
        next.putAll(writing)
        values.clear()
        values.putAll(next)
    }
}

// Lists are stored as one string; the unit separator never appears in ids or user text.
private const val SEPARATOR = '\u001F'

fun KeyValueStore.getStringList(key: String): List<String> =
    getString(key)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

fun KeyValueStore.putStringList(key: String, values: List<String>) {
    putString(key, values.joinToString(SEPARATOR.toString()))
}
