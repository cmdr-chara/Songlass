package dev.cmdrchara.songlass.debug

import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import dev.cmdrchara.songlass.SonglassApplication
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
        Log.i("PlayerPerf", "cache checks PASS: size revision missing cancellation pendingKeys=0")
    }

    // Debug assertion only: verify that active-key bookkeeping retains no completed requests.
    private fun pendingCount(loader: MediaStoreArtworkLoader): Int {
        val field = loader.javaClass.getDeclaredField("pendingLoads").apply { isAccessible = true }
        return (field.get(loader) as Map<*, *>).size
    }

    private suspend fun cacheChecks(app: SonglassApplication, key: String) {
        // Dimensions and artwork revisions must remain independent cache entries.
        val loader = MediaStoreArtworkLoader(app)
        val small = loader.load(key, 256)!!
        val large = loader.load(key, 1024)!!
        check(small !== large && small.width <= 256 && large.width <= 1024)
        check(loader.load(key + "#songlass:probe", 256) !== small)
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
    }
}
