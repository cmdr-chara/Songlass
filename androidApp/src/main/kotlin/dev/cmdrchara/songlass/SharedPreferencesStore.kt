package dev.cmdrchara.songlass

import android.content.Context
import dev.cmdrchara.songlass.core.storage.KeyValueStore

/** [KeyValueStore] on Android's SharedPreferences (written asynchronously with apply()). */
class SharedPreferencesStore(context: Context) : KeyValueStore {
    private val prefs = context.getSharedPreferences("songlass", Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }
}
