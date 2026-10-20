package dev.cmdrchara.songlass

import android.content.Context
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import java.io.IOException

/** [KeyValueStore] on Android's SharedPreferences (written asynchronously with apply()). */
class SharedPreferencesStore(context: Context, name: String = "songlass") : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun getString(key: String): String? = prefs.getString(key, null)

    override fun putString(key: String, value: String?) {
        prefs.edit().apply { if (value == null) remove(key) else putString(key, value) }.apply()
    }

    /** Only string values: the app writes nothing else, and anything foreign stays out of backups. */
    override fun snapshot(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()

    /**
     * One editor, one synchronous commit: other keys (including ones this app does not know)
     * are untouched. Blocks on disk I/O, so call it off the main thread.
     */
    override fun replace(removing: Set<String>, writing: Map<String, String>) {
        val previous = (removing + writing.keys).associateWith { prefs.getString(it, null) }
        val committed = try {
            prefs.edit().apply {
                removing.forEach { remove(it) }
                writing.forEach { (key, value) -> putString(key, value) }
            }.commit()
        } catch (failure: RuntimeException) {
            restore(previous)
            throw failure
        }
        if (!committed) {
            // SharedPreferences updates its in-memory map before writing the file, so undo that too.
            restore(previous)
            throw IOException("SharedPreferences could not be written")
        }
    }

    private fun restore(previous: Map<String, String?>) {
        runCatching {
            prefs.edit().apply {
                previous.forEach { (key, value) -> if (value == null) remove(key) else putString(key, value) }
            }.commit()
        }
    }
}
