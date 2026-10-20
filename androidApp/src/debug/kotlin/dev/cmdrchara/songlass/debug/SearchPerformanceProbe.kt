package dev.cmdrchara.songlass.debug

import android.os.Debug
import android.os.Looper
import android.util.Log
import dev.cmdrchara.songlass.AppGraph
import dev.cmdrchara.songlass.MusicSource
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.library.SearchResults
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.FakeAudioPlayer
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import org.json.JSONObject
import kotlin.system.measureNanoTime

/** Explicit debug-only CPU probe: synthetic tracks stay in memory, no playback or file writes. */
internal object SearchPerformanceProbe {
    @Volatile private var sink: Any? = null
    private val queries = listOf("h", "ha", "hayato", "jo", "jojo", "love", "zzqzx-no-match", "İ")

    suspend fun run(app: SonglassApplication, dataset: String) {
        require(dataset in listOf("real", "large"))
        app.graph.refreshLibrary()
        val real = app.graph.library.value.tracks
        check(real.isNotEmpty())
        val tracks = if (dataset == "real") real else List(10_000) { i ->
            real[i % real.size].copy(id = "probe-$i", title = "${real[i % real.size].title} $i")
        }
        withContext(Dispatchers.Default) {
            val library = MusicLibrary(tracks)
            queries.forEach { check(legacySearch(library, it) == library.search(it)) }
            repeat(8) { queries.forEach { sink = library.search(it); sink = legacySearch(library, it) } }
            val times = List(2) { mutableListOf<Double>() }
            val allocations = List(2) { mutableListOf<Long>() }
            repeat(20) { sample ->
                val order = if (sample % 2 == 0) listOf(0, 1) else listOf(1, 0)
                order.forEach { variant ->
                    val before = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                    times[variant] += measureNanoTime {
                        queries.forEach { sink = if (variant == 0) legacySearch(library, it) else library.search(it) }
                    } / 1_000_000.0
                    allocations[variant] += Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - before
                }
            }
            Log.i("SearchPerf", JSONObject().apply {
                put("phase", "paired"); put("dataset", dataset); put("tracks", tracks.size)
                put("queries_per_batch", queries.size)
                put("legacy_ms", JSONArray(times[0])); put("indexed_ms", JSONArray(times[1]))
                put("legacy_allocated_bytes", JSONArray(allocations[0]))
                put("indexed_allocated_bytes", JSONArray(allocations[1]))
            }.toString())
            sink = null
        }
        refresh(tracks, dataset)
    }

    // Exact pre-optimization search, kept only in this debug probe for paired measurements.
    private fun legacySearch(library: MusicLibrary, query: String): SearchResults {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return SearchResults()
        fun String?.matches() = this != null && lowercase().contains(needle)
        return SearchResults(
            tracks = library.tracks.filter { it.title.matches() || it.artist.matches() || it.artistNames.any { name -> name.matches() } || it.album.matches() },
            albums = library.albums.filter { it.title.matches() || it.artist.matches() },
            artists = library.artists.filter { it.name.matches() },
            playlists = library.playlists.filter { it.name.matches() },
        )
    }

    private suspend fun refresh(tracks: List<Track>, dataset: String) {
        val ownScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        try {
            var sourceTracks = tracks
            val source = object : MusicSource {
                override val hasAccess = MutableStateFlow(true)
                override suspend fun loadTracks(): List<Track> { delay(1); return sourceTracks }
            }
            val graph = AppGraph(FakeAudioPlayer(), source, InMemoryKeyValueStore(), ownScope)
            delay(10)
            var published = 0
            val observer = ownScope.launch(start = CoroutineStart.UNDISPATCHED) {
                graph.library.collect { if (it.tracks.isNotEmpty()) published++ }
            }
            val refreshMs = measureNanoTime { graph.refreshLibrary() } / 1_000_000.0
            // Allow any duplicate publication to become visible.
            delay(80)
            check(graph.library.value.tracks == withContext(Dispatchers.Default) { MusicLibrary(tracks).tracks })
            val first = graph.library.value
            val repeatedRefreshMs = measureNanoTime { graph.refreshLibrary() } / 1_000_000.0
            delay(80)
            val unchangedIdentity = graph.library.value === first
            observer.cancel()
            Log.i("SearchPerf", JSONObject().apply {
                put("phase", "refresh_indexed"); put("dataset", dataset)
                put("published_nonempty_libraries", published); put("refresh_ms", refreshMs)
                put("caller_is_main", Looper.myLooper() === Looper.getMainLooper())
                put("unchanged_refresh_ms", repeatedRefreshMs); put("unchanged_identity", unchangedIdentity)
            }.toString())
            graph.settings.setMinimumDuration(120_000)
            graph.refreshLibrary()
            check(graph.library.value.tracks == withContext(Dispatchers.Default) {
                MusicLibrary(graph.settings.state.value.libraryFilter.apply(tracks)).tracks
            })
            graph.settings.setMinimumDuration(0)
            sourceTracks = tracks.mapIndexed { i, track -> if (i == 0) track.copy(title = "Updated probe tag") else track }
            graph.refreshLibrary()
            check(graph.library.value.tracks.any { it.id == tracks.first().id && it.title == "Updated probe tag" })
            check(!graph.isLibraryLoading.value)
            sourceTracks = sourceTracks.mapIndexed { i, track -> if (i == 0) track.copy(title = "After cancelled refresh") else track }
            val interrupted = ownScope.launch { graph.refreshLibrary() }
            withTimeout(5_000) { graph.allTracks.first { it.firstOrNull()?.title == "After cancelled refresh" } }
            interrupted.cancelAndJoin()
            withTimeout(5_000) { graph.library.first { it.tracks.any { track -> track.title == "After cancelled refresh" } } }
            Log.i("SearchPerf", JSONObject().apply {
                put("phase", "refresh_checks"); put("dataset", dataset)
                put("filter_ready_on_return", true); put("updated_tags_ready_on_return", true)
                put("cancelled_caller_still_publishes", true)
            }.toString())
        } finally { ownScope.cancel() }
    }
}
