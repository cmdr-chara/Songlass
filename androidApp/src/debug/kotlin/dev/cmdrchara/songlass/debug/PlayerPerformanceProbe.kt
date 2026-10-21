package dev.cmdrchara.songlass.debug

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.artwork.ArtworkAvailability
import dev.cmdrchara.songlass.library.MediaStoreArtworkLoader
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.system.measureNanoTime

/** Debug-only measurement and explicit QA setup. Never edits music files. */
internal object PlayerPerformanceProbe {
    private const val TITLE = "A Duo of Courage"

    /** Explicit QA setup via normal settings APIs; callers restore their captured preferences. */
    fun configure(app: SonglassApplication, specification: String) {
        val fields = specification.split(',')
        require(fields.size == 3)
        val style = dev.cmdrchara.songlass.core.settings.InterfaceStyle.valueOf(fields[0])
        val appearance = dev.cmdrchara.songlass.core.settings.AppearanceMode.valueOf(fields[1])
        val artwork = dev.cmdrchara.songlass.core.settings.ArtworkStyle.valueOf(fields[2])
        app.graph.settings.setInterfaceStyle(style)
        app.graph.settings.setAppearance(appearance)
        app.graph.settings.setArtworkStyle(artwork)
        Log.i("PlayerPerf", "configured $specification")
    }

    suspend fun prepare(app: SonglassApplication) {
        app.graph.refreshLibrary()
        val track = app.graph.library.value.tracks.first { it.title == TITLE }
        app.graph.controller.playTracks(listOf(track))
        app.graph.controller.pause()
        Log.i("PlayerPerf", "prepared paused")
    }

    suspend fun artwork(app: SonglassApplication) {
        app.graph.refreshLibrary()
        val key = app.graph.library.value.tracks.first { it.title == TITLE }.artworkKey
        withContext(Dispatchers.Default) {
            fun digest(image: ImageBitmap): String {
                val bitmap = image.asAndroidBitmap()
                val bytes = ByteBuffer.allocate(bitmap.byteCount)
                bitmap.copyPixelsToBuffer(bytes)
                return MessageDigest.getInstance("SHA-256").digest(bytes.array())
                    .joinToString("") { "%02x".format(it) }
            }
            val records = JSONArray()
            for (size in listOf(256, 1024)) {
                repeat(8) { run ->
                    val loader = MediaStoreArtworkLoader(app)
                    var images: List<ImageBitmap?> = emptyList()
                    val nanos = measureNanoTime {
                        images = coroutineScope { List(4) { async { loader.load(key, size) } }.awaitAll() }
                    }
                    check(images.all { it != null })
                    val unique = images.filterNotNull().distinctBy { System.identityHashCode(it.asAndroidBitmap()) }
                    val hashes = unique.map(::digest).distinct()
                    check(hashes.size == 1)
                    val hit = loader.load(key, size)
                    check(images.any { it === hit })
                    records.put(JSONObject().apply {
                        put("size_px", size); put("run", run); put("requests", 4)
                        put("elapsed_ms", nanos / 1_000_000.0)
                        put("unique_bitmaps", unique.size)
                        put("returned_bitmap_bytes", unique.sumOf { it.asAndroidBitmap().allocationByteCount })
                        put("width", unique.first().width); put("height", unique.first().height)
                        put("pixels_sha256", hashes.single())
                    })
                }
            }
            cacheChecks(app, key)
            (0 until records.length()).chunked(8).forEach { chunk ->
                Log.i("ArtworkPerf", JSONObject().apply {
                    put("records", JSONArray().apply { chunk.forEach { put(records.getJSONObject(it)) } })
                    put("cache_checks", "PASS")
                }.toString())
            }
        }
    }

    suspend fun checks(app: SonglassApplication) {
        app.graph.refreshLibrary()
        val key = app.graph.library.value.tracks.first { it.title == TITLE }.artworkKey
        withContext(Dispatchers.Default) { cacheChecks(app, key) }
        Log.i("PlayerPerf", "cache checks PASS: real decode sizes identity availability recovery cancellation pendingKeys=0")
    }

    // Debug assertion only: verify that active-key bookkeeping retains no completed requests.
    private fun pendingCount(loader: MediaStoreArtworkLoader): Int {
        val field = loader.javaClass.getDeclaredField("pendingLoads").apply { isAccessible = true }
        return (field.get(loader) as Map<*, *>).size
    }

    private suspend fun cacheChecks(app: SonglassApplication, key: String) {
        // Decode actual resolved artwork at both sizes; this remains a real decode/cache probe.
        val loader = MediaStoreArtworkLoader(app)
        val small = loader.load(key, 256)!!
        val large = loader.load(key, 1024)!!
        check(small !== large && small.width <= 256 && large.width <= 1024)
        // A fabricated revision/key is not authority to open the same platform URI.
        check(loader.load(key + "#songlass:probe", 256) == null)
        check(loader.cached(key + "#songlass:probe", 256) == null)
        check(loader.load("content://media/external/audio/media/-1", 256) == null)
        check(loader.load("content://media/external/audio/media/-1", 256) == null)
        // Cancelling callers must leave later requests usable, without stranded key locks.
        withTimeout(5_000) {
            val fresh = MediaStoreArtworkLoader(app)
            coroutineScope {
                val jobs = List(8) { async { fresh.load(key, 1024) } }
                delay(1); jobs.take(4).forEach { it.cancel() }
                jobs.drop(4).awaitAll().forEach { check(it != null) }
            }
            check(fresh.load(key, 1024) != null)
            check(pendingCount(fresh) == 0)
        }
        check(pendingCount(loader) == 0)
        availabilityChecks(app, key)
    }

    private suspend fun availabilityChecks(app: SonglassApplication, key: String) {
        val track = app.graph.allTracks.value.single { it.artworkKey == key }
        val missing = track.copy(id = "debug-artwork-missing", uri = "content://media/external/audio/media/-1")
        val availability = ArtworkAvailability()
        val index = ArtworkAvailability.Index.build(listOf(track, missing))
        var permission = true
        // Local probe state only: never invalidate the running graph or change media files.
        val loader = MediaStoreArtworkLoader(app, availability) { permission }
        check(loader.load(key, 256) == null)
        check(loader.cached(key, 256) == null) // A blocked read must not install a negative cache entry.
        availability.publish(index)
        val small = loader.load(key, 256)!!
        check(loader.load(track.uri, 256) == null) // Raw audio URI is not a canonical cache key.
        permission = false // Direct check, before any observer/availability revision can run.
        check(loader.load(key, 1024) == null)
        check(loader.cached(key, 1024) == null)
        check(loader.load(key, 256) === small) // Verified memory needs no new source open.
        permission = true
        check(loader.load(key, 1024) != null)

        check(loader.load(missing.artworkKey, 256) == null)
        val absent = loader.cached(missing.artworkKey, 256)
        check(absent != null && absent.image == null)
        check(loader.load(missing.artworkKey, 256) == null)
        check(loader.cached(missing.artworkKey, 256) === absent)
        availability.invalidate()
        check(loader.cached(missing.artworkKey, 256) == null)
        check(loader.load(missing.artworkKey, 256) == null)
        check(loader.cached(missing.artworkKey, 256) == null)
        check(loader.load(key, 256) === small)
        availability.publish(index) // Identical metadata still permits a fresh negative-cache retry.
        check(loader.cached(missing.artworkKey, 256) == null)
        check(loader.load(missing.artworkKey, 256) == null)
        check(loader.cached(missing.artworkKey, 256) != null)
        check(pendingCount(loader) == 0)
    }
}
