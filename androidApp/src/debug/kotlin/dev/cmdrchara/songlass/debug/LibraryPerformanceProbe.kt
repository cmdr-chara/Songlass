package dev.cmdrchara.songlass.debug

import android.util.Log
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.home.HomeFeed
import dev.cmdrchara.songlass.core.library.MusicLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.system.measureNanoTime

/** Explicit, debug-only CPU benchmark. Never changes music, history or preferences. */
internal object LibraryPerformanceProbe {
    // Consume every result so an optimizing runtime cannot discard the measured work.
    @Volatile private var sink: Any? = null

    suspend fun run(app: SonglassApplication) {
        app.graph.refreshLibrary()
        val tracks = app.graph.settings.state.value.libraryFilter.apply(app.graph.allTracks.value)
        withContext(Dispatchers.Default) {
            val history = HistorySnapshot()
            val now = 1_792_368_000_000L // Fixed day keeps the Home output reproducible.
            val library = MusicLibrary(tracks)
            fun build() { sink = MusicLibrary(tracks) }
            fun lookup() { sink = tracks.map(library::albumOf) }
            fun home() { sink = HomeFeed.build(library, history, emptyList(), now) }
            fun samples(block: () -> Unit): List<Double> {
                repeat(30) { block() }
                return List(30) { measureNanoTime(block).toDouble() / 1_000_000 }
            }
            val buildMs = samples(::build)
            val lookupMs = samples(::lookup)
            val homeMs = samples(::home)
            // Covers ordering, metadata and memberships, not just collection counts.
            val signature = listOf(library.tracks, library.albums, library.artists,
                library.playlists, library.folders, tracks.map(library::albumOf),
                HomeFeed.build(library, history, emptyList(), now)).toString()
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
            Log.i("LibraryPerf", JSONObject().apply {
                put("tracks", tracks.size)
                put("albums", library.albums.size)
                put("artists", library.artists.size)
                put("fingerprint", fingerprint)
                put("build_ms", org.json.JSONArray(buildMs))
                put("lookup_ms", org.json.JSONArray(lookupMs))
                put("home_ms", org.json.JSONArray(homeMs))
            }.toString())
            sink = null
        }
    }
}
