package dev.cmdrchara.songlass.core.cue

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList

data class ImportedCueSheet(val id: String, val name: String)

/** User-imported sheets; audio remains in the platform library. No file path is opened here. */
class CueCatalog(private val store: KeyValueStore) {
    private val indexKey = "library.cue.index"
    fun entries(): List<ImportedCueSheet> = store.getStringList(indexKey).distinct().take(64).mapNotNull { id ->
        if (id.isEmpty() || id.any { !it.isDigit() } || id.length > 20) return@mapNotNull null
        store.getString("library.cue.$id.name")?.let { ImportedCueSheet(id, it) }
    }

    fun remove(id: String): Boolean {
        val ids = entries().map { it.id }
        if (id !in ids) return false
        store.updateStrings(removing = setOf("library.cue.$id.name", "library.cue.$id.text"),
            writing = mapOf(indexKey to ids.filterNot { it == id }.joinToString("\u001F")))
        return true
    }

    fun expand(physical: List<Track>): List<Track> {
        val clips = store.getStringList(indexKey).distinct().take(64).flatMap { id ->
            if (id.any { !it.isDigit() } || id.length > 20) return@flatMap emptyList()
            val sheet = store.getString("library.cue.$id.text")?.let(CueSheets::parse) ?: return@flatMap emptyList()
            CueSheets.resolve(sheet, physical).orEmpty()
        }.distinctBy { it.id }
        return physical + clips
    }

    fun save(name: String, text: String): Boolean {
        if (name.isBlank() || name.length > 256 || CueSheets.parse(text) == null) return false
        val ids = store.getStringList(indexKey).distinct()
        if (ids.any { store.getString("library.cue.$it.text") == text }) return true
        if (ids.size >= 64) return false
        var id = 1L
        while (id.toString() in ids) id++
        store.putStrings(mapOf(indexKey to (ids + id.toString()).joinToString("\u001F"),
            "library.cue.$id.name" to name, "library.cue.$id.text" to text))
        return true
    }
}
