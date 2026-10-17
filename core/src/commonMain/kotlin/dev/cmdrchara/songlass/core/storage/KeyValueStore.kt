package dev.cmdrchara.songlass.core.storage

/**
 * Small persistent key-value storage for preferences and lists of ids.
 * Platforms back it with their native store (SharedPreferences, NSUserDefaults).
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String?)
}

/** Volatile store for tests and the demo setup. */
class InMemoryKeyValueStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}

// Lists are stored as one string; the unit separator never appears in ids or user text.
private const val SEPARATOR = '\u001F'

fun KeyValueStore.getStringList(key: String): List<String> =
    getString(key)?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

fun KeyValueStore.putStringList(key: String, values: List<String>) {
    putString(key, values.joinToString(SEPARATOR.toString()))
}
