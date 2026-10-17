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
}
