package dev.cmdrchara.songlass.debug

import android.os.Debug
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.designsystem.foundation.CachedArtworkImage
import dev.cmdrchara.songlass.designsystem.foundation.samplePixels
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.system.measureNanoTime

/** Explicit debug-only CPU probe. Never plays music or changes preferences/files. */
internal object ArtworkColorPerformanceProbe {
    @Volatile private var sink: Any? = null

    suspend fun run(app: SonglassApplication) {
        app.graph.refreshLibrary()
        val images = app.graph.library.value.tracks.asSequence().map { it.artworkKey }.distinct()
            .take(24).toList().mapNotNull { app.artworkLoader.load(it, 256) }.take(12)
        check(images.isNotEmpty())
        // This work runs synchronously in composition today. Keep both variants on that thread.
        withContext(Dispatchers.Main.immediate) {
            val entries = images.map(::CachedArtworkImage)
            val expected = images.map(::reference)
            check(entries.map(::candidate) == expected)
            entries.forEachIndexed { index, entry ->
                val raw = images[index].samplePixels().filterIndexed { i, _ -> i % 16 < 4 }
                check(entry.colors!!.header == ArtworkTint.averageOf(raw))
                check(entry.colors === entry.colors)
            }
            check(CachedArtworkImage(null).colors == null)
            val key = app.graph.library.value.tracks.first().artworkKey
            val loaded = app.artworkLoader.load(key, 256)
            if (loaded != null) {
                val first = app.artworkLoader.cached(key, 256)!!
                check(first.image === loaded)
                check(app.artworkLoader.cached(key, 256) === first)
                check(app.artworkLoader.cached(key, 256)!!.colors === first.colors)
                val large = app.artworkLoader.load(key, 1024)
                check(app.artworkLoader.cached(key, 1024)!!.image === large)
                check(app.artworkLoader.cached(key, 1024) !== first)
            }
            repeat(10) {
                images.forEach { sink = reference(it) }
                images.forEach { sink = candidate(CachedArtworkImage(it)) }
                entries.forEach { sink = candidate(it) }
            }
            val timings = List(3) { mutableListOf<Double>() }
            val allocations = List(3) { mutableListOf<Long>() }
            fun measure(variant: Int) {
                val before = Debug.getRuntimeStat("art.gc.bytes-allocated").toLong()
                timings[variant] += measureNanoTime {
                    when (variant) {
                        0 -> images.forEach { sink = reference(it) }
                        1 -> images.forEach { sink = candidate(CachedArtworkImage(it)) }
                        2 -> entries.forEach { sink = candidate(it) }
                    }
                } / 1_000_000.0
                allocations[variant] += Debug.getRuntimeStat("art.gc.bytes-allocated").toLong() - before
            }
            repeat(30) { iteration ->
                // Rotate all three orders so GC, JIT and device drift do not favor one variant.
                repeat(3) { offset -> measure((iteration + offset) % 3) }
            }
            val digest = MessageDigest.getInstance("SHA-256").digest(expected.toString().toByteArray())
                .joinToString("") { "%02x".format(it) }
            // Separate records keep each logcat entry below its truncation limit.
            listOf("reference", "cold_shared", "warm_shared").forEachIndexed { i, name ->
                Log.i("ArtworkColorPerf", JSONObject().apply {
                    put("phase", "paired"); put("variant", name); put("covers", images.size)
                    put("unique_images", images.distinct().size)
                    put("pixels_per_cover", JSONArray(images.map { it.width * it.height }))
                    put("palette_sha256", digest); put("equality_checks", "PASS")
                    put("ms", JSONArray(timings[i])); put("allocated_bytes", JSONArray(allocations[i]))
                }.toString())
            }
            sink = null
        }
    }

    private fun candidate(entry: CachedArtworkImage): List<Any> = buildList {
        for ((dark, follow) in listOf(false to false, true to false, false to true)) {
            val colors = entry.colors!!
            add(ArtworkTint.from(colors.seed, dark, colors.bottom.takeIf { follow }))
        }
        add(entry.colors!!.headerScrimAlpha)
    }

    // Three palette consumers (context/player/header) and the status-icon scrim.
    private fun reference(image: ImageBitmap): List<Any> = buildList {
        for ((dark, follow) in listOf(false to false, true to false, false to true)) {
            val pixels = image.samplePixels()
            val seed = ArtworkTint.seedOf(pixels)
            val edge = ArtworkTint.averageOf(pixels.filterIndexed { i, _ -> i % 16 >= 12 })
            add(ArtworkTint.from(seed, dark, edge.takeIf { follow }))
        }
        add(ArtworkTint.headerScrimAlpha(image.samplePixels().filterIndexed { i, _ -> i % 16 < 4 }))
    }
}
