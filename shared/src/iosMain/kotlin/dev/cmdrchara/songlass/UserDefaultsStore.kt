package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSRecursiveLock

/** [KeyValueStore] on iOS's NSUserDefaults. */
class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = withStoreLock { defaults.stringForKey(key) }

    override fun putString(key: String, value: String?) = withStoreLock {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }

    /**
     * String values visible to the app, which can include system-wide defaults; callers pick
     * their own keys out of it and nothing is ever cleared wholesale. The shared lock keeps
     * a backup snapshot from observing half a batch written through another store instance.
     */
    override fun snapshot(): Map<String, String> = withStoreLock {
        buildMap {
            defaults.dictionaryRepresentation().forEach { (key, value) ->
                if (key is String && value is String) put(key, value)
            }
        }
    }

    // NSUserDefaults has no disk transaction. Keep the default rollback, with in-process
    // readers excluded until the whole batch finishes; do not claim crash-atomic durability.
    override fun replace(removing: Set<String>, writing: Map<String, String>) = withStoreLock {
        super<KeyValueStore>.replace(removing, writing)
    }

    private inline fun <T> withStoreLock(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    private companion object {
        val lock = NSRecursiveLock()
    }
}
