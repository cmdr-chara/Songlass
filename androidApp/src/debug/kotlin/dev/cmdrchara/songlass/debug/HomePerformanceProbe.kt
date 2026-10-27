package dev.cmdrchara.songlass.debug

import android.util.Log
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.home.HomeFeed
import dev.cmdrchara.songlass.core.home.HomeItem
import dev.cmdrchara.songlass.core.library.MusicLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.system.measureNanoTime

/** Explicit debug-only Home CPU probe. Reads snapshots; never plays or writes user data. */
internal object HomePerformanceProbe {
    @Volatile private var sink: Any? = null

    suspend fun run(app: SonglassApplication) {
        app.graph.refreshLibrary()
        val graph = app.graph
        val history = graph.history.state.value
        val favorites = graph.favorites.ids.value
        val ratings = graph.ratings.values.value
        val shortcuts = graph.homeCustomization.state.value.shortcuts
        val personal = graph.userPlaylists.playlists.value
        val smart = graph.smartPlaylists.playlists.value
        val library = graph.library.value
        val now = 1_792_368_000_000L
        withContext(Dispatchers.Default) {
            fun legacyAdded(library: MusicLibrary): List<HomeItem> = library.tracks
                .filter { it.dateAddedMs != null }.sortedByDescending { it.dateAddedMs }
                .map { track -> library.albumOf(track)?.let(HomeItem::AlbumItem) ?: HomeItem.TrackItem(track) }
                .distinct().take(HomeFeed.ROW_SIZE)
            fun keyedAdded(library: MusicLibrary): List<HomeItem> = library.tracks.asSequence()
                .filter { it.dateAddedMs != null }.sortedByDescending { it.dateAddedMs }
                .map { track -> library.albumOf(track)?.let(HomeItem::AlbumItem) ?: HomeItem.TrackItem(track) }
                .distinctBy { if (it is HomeItem.AlbumItem) it.album.title to it.album.artist else it }
                .take(HomeFeed.ROW_SIZE).toList()
            fun home() = HomeFeed.build(library, history, favorites, now, ratings, shortcuts, personal, smart)
            fun samples(block: () -> Any): List<Double> {
                repeat(30) { sink = block() }
                return List(40) { measureNanoTime { sink = block() }.toDouble() / 1_000_000 }
            }
            check(legacyAdded(library) == keyedAdded(library))
            // Alternate the isolated variants to reduce runtime/thermal-order bias.
            val legacy = mutableListOf<Double>()
            val keyed = mutableListOf<Double>()
            repeat(30) { sink = legacyAdded(library); sink = keyedAdded(library) }
            repeat(40) { index ->
                fun old() { legacy += measureNanoTime { sink = legacyAdded(library) }.toDouble() / 1_000_000 }
                fun new() { keyed += measureNanoTime { sink = keyedAdded(library) }.toDouble() / 1_000_000 }
                if (index % 2 == 0) { old(); new() } else { new(); old() }
            }
            val homeMs = samples(::home)
            val signature = home().toString()
            val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
                .joinToString("") { "%02x".format(it) }
            Log.i("HomePerf", JSONObject().apply {
                put("tracks", library.tracks.size)
                put("albums", library.albums.size)
                put("plays", history.totalPlays)
                put("fingerprint", digest)
                put("recent_reference_ms", JSONArray(legacy))
                put("recent_keyed_ms", JSONArray(keyed))
                put("home_ms", JSONArray(homeMs))
            }.toString())
            sink = null
        }
    }
}
