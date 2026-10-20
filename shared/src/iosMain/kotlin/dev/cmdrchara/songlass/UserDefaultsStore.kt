package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import platform.Foundation.NSUserDefaults

/** [KeyValueStore] on iOS's NSUserDefaults. */
class UserDefaultsStore : KeyValueStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun getString(key: String): String? = defaults.stringForKey(key)

    override fun putString(key: String, value: String?) {
        if (value == null) defaults.removeObjectForKey(key) else defaults.setObject(value, forKey = key)
    }

    /**
     * String values visible to the app, which can include system-wide defaults; callers pick
     * their own keys out of it and nothing is ever cleared wholesale. [replace] keeps the default
     * put-and-roll-back behavior because NSUserDefaults has no transaction.
     */
    override fun snapshot(): Map<String, String> = buildMap {
        defaults.dictionaryRepresentation().forEach { (key, value) ->
            if (key is String && value is String) put(key, value)
        }
    }
}
