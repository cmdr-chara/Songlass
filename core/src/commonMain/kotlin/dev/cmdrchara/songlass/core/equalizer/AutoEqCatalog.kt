package dev.cmdrchara.songlass.core.equalizer

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A recommended measurement/target variant, pinned to the catalog's upstream revision. */
data class AutoEqEntry(val name: String, val source: String, val path: String, val revision: String) {
    val id: String get() = "$revision:$path"
    internal val searchKey = searchable(name)
    val profileUrl: String get() =
        "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/$revision/results/$path/${path.substringAfterLast('/')}%20ParametricEQ.txt"
}

class AutoEqCatalog private constructor(val entries: List<AutoEqEntry>) {
    private val byId = entries.associateBy { it.id }
    fun find(id: String): AutoEqEntry? = byId[id]

    /** Punctuation/spacing do not hide models such as WH-1000XM4 or HD 600. */
    fun search(query: String): List<AutoEqEntry> {
        val terms = query.trim().split(Regex("\\s+")).map(::searchable).filter(String::isNotEmpty)
        return if (terms.isEmpty()) entries else entries.filter { entry -> terms.all(entry.searchKey::contains) }
    }

    companion object {
        fun parse(text: String): AutoEqCatalog {
            val lines = text.lineSequence().iterator()
            require(lines.hasNext()) { "Missing AutoEQ catalog" }
            val revision = lines.next().removePrefix("# revision=")
            require(revision.matches(Regex("[a-f0-9]{40}"))) { "Invalid AutoEQ revision" }
            val entries = mutableListOf<AutoEqEntry>()
            while (lines.hasNext()) {
                val line = lines.next()
                if (line.isBlank() || line.startsWith('#')) continue
                val parts = line.split('\t')
                require(parts.size == 3 && parts[0].isNotBlank() && parts[1].isNotBlank()) { "Invalid AutoEQ catalog row" }
                // A profile URL can only address an encoded result path on the fixed upstream host.
                val segments = parts[2].split('/')
                require(segments.size == 3 && segments.all { segment ->
                    segment.isNotEmpty() && segment != "." && segment != ".." &&
                        segment.matches(Regex("(?:[A-Za-z0-9._~-]|%[0-9A-Fa-f]{2})+")) &&
                        !segment.contains(Regex("%2[fFeE]|%5[cC]", RegexOption.IGNORE_CASE))
                }) { "Invalid AutoEQ result path" }
                entries += AutoEqEntry(parts[0], parts[1], parts[2], revision)
            }
            require(entries.isNotEmpty() && entries.map { it.id }.distinct().size == entries.size) { "Invalid AutoEQ catalog entries" }
            return AutoEqCatalog(entries)
        }
    }
}

private fun searchable(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

data class AutoEqCorrection(val entry: AutoEqEntry, val filters: List<EqFilter>)

/** Offline index; only the selected profile uses the network, and valid downloads survive offline. */
class AutoEqRepository(
    private val readCatalog: suspend () -> String,
    private val download: suspend (String) -> String,
    private val store: KeyValueStore,
    private val savedCorrection: (AutoEqEntry) -> AutoEqCorrection? = { null },
) {
    private val catalogLock = Mutex()
    private val profileLock = Mutex()
    private var loaded: AutoEqCatalog? = null

    suspend fun catalog(): AutoEqCatalog = catalogLock.withLock {
        loaded ?: AutoEqCatalog.parse(readCatalog()).also { loaded = it }
    }

    suspend fun correction(entry: AutoEqEntry): AutoEqCorrection {
        // Never turn caller-supplied paths into requests, even if the object looks like an entry.
        val canonical = catalog().find(entry.id)
        require(canonical == entry) { "Unknown AutoEQ profile" }
        savedCorrection(entry)?.let { return it }
        return profileLock.withLock {
            currentCoroutineContext().ensureActive()
            val key = "autoeq.cache.${entry.id}"
            fun decode(text: String?): AutoEqCorrection? {
                if (text == null || text.length > MAX_PROFILE_BYTES) return null
                val parsed = AutoEq.parse(text, rejectOutOfRange = true)
                if (parsed.filters.isEmpty() || parsed.skipped != 0) return null
                if (parsed.filters.any { !it.frequencyHz.isFinite() || !it.gainDb.isFinite() || !it.q.isFinite() }) return null
                return AutoEqCorrection(entry, parsed.filters)
            }
            decode(store.getString(key))?.let { return@withLock it }
            val body = download(entry.profileUrl)
            currentCoroutineContext().ensureActive()
            val correction = requireNotNull(decode(body)) { "Invalid AutoEQ profile" }
            // Only complete usable profiles enter the cache; failed requests cannot replace them.
            val previous = store.getStringList(CACHE_INDEX).filter { it.startsWith("autoeq.cache.") && it != CACHE_INDEX }
            val keep = (previous.filterNot { it == key } + key).takeLast(CACHE_SIZE)
            store.putString(key, body)
            store.putStringList(CACHE_INDEX, keep)
            previous.filterNot { it in keep }.forEach { store.putString(it, null) }
            correction
        }
    }

    companion object {
        const val MAX_PROFILE_BYTES = 64 * 1024
        private const val CACHE_SIZE = 32
        private const val CACHE_INDEX = "autoeq.cache.index"
    }
}
