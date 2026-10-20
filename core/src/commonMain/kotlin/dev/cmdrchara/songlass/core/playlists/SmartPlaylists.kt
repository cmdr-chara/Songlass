package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SmartPlayStatus { Any, Never, Played }
enum class SmartSort { Title, Newest, MostPlayed, LeastPlayed }

enum class SmartMatch { All, Any }

/** Enabled inclusion rules combine with All/Any; exclusions always veto a match. */
data class SmartRules(
    val favoritesOnly: Boolean = false,
    val playStatus: SmartPlayStatus = SmartPlayStatus.Any,
    val addedWithinDays: Int? = null,
    val artist: String = "",
    val folder: String = "",
    val sort: SmartSort = SmartSort.Title,
    val limit: Int? = null,
    val match: SmartMatch = SmartMatch.All,
    val genre: String = "",
    val yearFrom: Int? = null,
    val yearTo: Int? = null,
    val durationFromSeconds: Int? = null,
    val durationToSeconds: Int? = null,
    val excludeArtist: String = "",
    val excludeFolder: String = "",
    val excludeGenre: String = "",
) {
    val isValid: Boolean get() =
        (addedWithinDays == null || addedWithinDays in 1..3650) &&
        (limit == null || limit in 1..10000) &&
        validRange(yearFrom, yearTo, 1..9999) &&
        validRange(durationFromSeconds, durationToSeconds, 0..MAX_DURATION_SECONDS)

    fun normalized(): SmartRules = copy(artist = artist.trim(), folder = folder.trim().trim('/'),
        genre = genre.trim(), excludeArtist = excludeArtist.trim(),
        excludeFolder = excludeFolder.trim().trim('/'), excludeGenre = excludeGenre.trim())

    fun resolve(library: MusicLibrary, history: HistorySnapshot, favorites: Set<String>, nowMs: Long): List<Track> {
        if (!isValid) return emptyList()
        val rules = normalized()
        val cutoff = addedWithinDays?.let { nowMs - it * 86_400_000L }
        fun inFolder(track: Track, path: String): Boolean =
            track.folder?.let { it == path || it.startsWith("$path/") } == true
        fun artistMatches(track: Track, query: String): Boolean = track.artistNames.any { it.contains(query, ignoreCase = true) }
        val matches = library.tracks.filter { track ->
            val excluded = (rules.excludeArtist.isNotEmpty() && artistMatches(track, rules.excludeArtist)) ||
                (rules.excludeFolder.isNotEmpty() && inFolder(track, rules.excludeFolder)) ||
                (rules.excludeGenre.isNotEmpty() && track.genre?.contains(rules.excludeGenre, ignoreCase = true) == true)
            if (excluded) return@filter false
            val count = history.playCounts[track.id] ?: 0
            val checks = buildList {
                if (favoritesOnly) add(track.id in favorites)
                if (playStatus != SmartPlayStatus.Any) add(if (playStatus == SmartPlayStatus.Never) count == 0 else count > 0)
                if (cutoff != null) add(track.dateAddedMs?.let { it in cutoff..nowMs } == true)
                if (rules.artist.isNotEmpty()) add(artistMatches(track, rules.artist))
                if (rules.folder.isNotEmpty()) add(inFolder(track, rules.folder))
                if (rules.genre.isNotEmpty()) add(track.genre?.contains(rules.genre, ignoreCase = true) == true)
                if (yearFrom != null || yearTo != null) add(track.releaseDate?.year?.let {
                    (yearFrom == null || it >= yearFrom) && (yearTo == null || it <= yearTo)
                } == true)
                if (durationFromSeconds != null || durationToSeconds != null) add(track.durationMs > 0 &&
                    (durationFromSeconds == null || track.durationMs >= durationFromSeconds * 1000L) &&
                    (durationToSeconds == null || track.durationMs <= durationToSeconds * 1000L))
            }
            checks.isEmpty() || if (match == SmartMatch.All) checks.all { it } else checks.any { it }
        }
        val titleOrder = compareBy<Track> { it.title.lowercase() }.thenBy { it.id }
        val order = when (sort) {
            SmartSort.Title -> titleOrder
            SmartSort.Newest -> compareByDescending<Track> { it.dateAddedMs ?: Long.MIN_VALUE }.then(titleOrder)
            SmartSort.MostPlayed -> compareByDescending<Track> { history.playCounts[it.id] ?: 0 }.then(titleOrder)
            SmartSort.LeastPlayed -> compareBy<Track> { history.playCounts[it.id] ?: 0 }.then(titleOrder)
        }
        return matches.sortedWith(order).let { tracks -> limit?.let(tracks::take) ?: tracks }
    }

    companion object {
        const val MAX_DURATION_SECONDS = 604800
        private fun validRange(from: Int?, to: Int?, range: IntRange): Boolean =
            (from == null || from in range) && (to == null || to in range) && (from == null || to == null || from <= to)
    }
}

data class SmartPlaylist(val id: String, val name: String, val rules: SmartRules)

/** Only rules persist: membership is always resolved against the current visible library. */
class SmartPlaylists(private val store: KeyValueStore, private val clock: () -> Long) {
    private val _playlists = MutableStateFlow(load())
    val playlists = _playlists.asStateFlow()
    fun get(id: String) = _playlists.value.firstOrNull { it.id == id }

    /** Re-reads the rules from [store], after something else (a backup restore) replaced them. */
    fun reload() {
        _playlists.value = load()
    }
    fun save(id: String?, name: String, rules: SmartRules): String {
        require(name.isNotBlank())
        require(rules.isValid)
        var key = id?.takeIf { get(it) != null } ?: "s${clock()}"
        if (id == null || get(id) == null) {
            var suffix = 1
            val base = key
            while (get(key) != null) key = "$base-${suffix++}"
        }
        val playlist = SmartPlaylist(key, name.trim(), rules.normalized())
        persist(_playlists.value.filterNot { it.id == key } + playlist)
        return key
    }
    fun delete(id: String) {
        store.putString("smart.$id.name", null)
        store.putString("smart.$id.rules", null)
        persist(_playlists.value.filterNot { it.id == id })
    }
    private fun persist(playlists: List<SmartPlaylist>) {
        playlists.forEach { p ->
            val key = "smart.${p.id}."
            store.putString(key + "name", p.name)
            store.putString(key + "rules", SmartRulesCodec.encode(p.rules))
        }
        store.putStringList("smart.index", playlists.map { it.id })
        _playlists.value = playlists
    }
    private fun load() = store.getStringList("smart.index").distinct().mapNotNull { id ->
        val name = store.getString("smart.$id.name") ?: return@mapNotNull null
        val rules = SmartRulesCodec.decode(store.getString("smart.$id.rules")) ?: return@mapNotNull null
        SmartPlaylist(id, name, rules)
    }
}
