@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.cmdrchara.songlass.library

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaMetadata
import androidx.media3.common.DataReader
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.*
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dev.cmdrchara.songlass.core.model.ReleaseDate
import dev.cmdrchara.songlass.core.model.ComposerTags
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

internal data class ExtraTags(val artists: List<String> = emptyList(), val date: ReleaseDate? = null, val composers: List<String> = emptyList())

/** Local header metadata through Media3's existing extractors: no decoder, player or network.
 * Cached by MediaStore id/size/modification time; the OS may safely evict this cache.
 */
internal class ExtraAudioTags(private val context: Context) {
    // Composer credits were not stored in v1. Re-read headers once after the schema change.
    private val cacheFile = java.io.File(context.cacheDir, "library-tags-v2.json")
    private val cached = try { JSONObject(cacheFile.readText()) } catch (_: Exception) { JSONObject() }
    private val retained = JSONObject()

    suspend fun read(uri: Uri, key: String): ExtraTags {
        val tags = cached.optJSONObject(key)?.let { runCatching { decode(it) }.getOrNull() } ?: extract(uri)
        retained.put(key, JSONObject().apply {
            put("artists", JSONArray(tags.artists))
            put("composers", JSONArray(tags.composers))
            tags.date?.let { date -> put("date", "%04d".format(java.util.Locale.ROOT, date.year) +
                (date.month?.let { "-%02d".format(java.util.Locale.ROOT, it) } ?: "") +
                (date.day?.let { "-%02d".format(java.util.Locale.ROOT, it) } ?: "")) }
        })
        return tags
    }

    fun save() {
        try { cacheFile.writeText(retained.toString()) } catch (_: IOException) { /* Disposable cache. */ }
    }

    private fun decode(json: JSONObject): ExtraTags {
        val artists = json.optJSONArray("artists")
        val composers = json.optJSONArray("composers")
        return ExtraTags((0 until (artists?.length() ?: 0)).map { artists!!.getString(it) }, ReleaseDate.parse(json.optString("date")),
            ComposerTags.names((0 until (composers?.length() ?: 0)).map { composers!!.getString(it) }))
    }

    private suspend fun extract(uri: Uri): ExtraTags {
        val source = DefaultDataSource(context, false)
        val extractors = DefaultExtractorsFactory().setDisableArtworkMetadata(true).createExtractors(uri, emptyMap())
        var remaining = 8 * 1024 * 1024
        fun open(position: Long): DefaultExtractorInput {
            source.close()
            val length = source.open(DataSpec.Builder().setUri(uri).setPosition(position).build())
            val reader = DataReader { buffer, offset, lengthToRead ->
                if (lengthToRead > remaining) throw IOException("Metadata read limit")
                val count = source.read(buffer, offset, lengthToRead)
                if (count > 0) remaining -= count
                count
            }
            return DefaultExtractorInput(reader, position, if (length == C.LENGTH_UNSET.toLong()) C.LENGTH_UNSET.toLong() else position + length)
        }
        val artists = mutableListOf<String>()
        val fallback = mutableListOf<String>()
        val composers = mutableListOf<String>()
        var date: ReleaseDate? = null
        fun result() = ExtraTags(artists.ifEmpty { fallback.takeIf { it.size > 1 }.orEmpty() }, date, ComposerTags.names(composers))
        try {
            var input = open(0)
            val extractor = extractors.firstOrNull { candidate ->
                try { candidate.sniff(input) } catch (_: IOException) { false } finally { input.resetPeekPosition() }
            } ?: return ExtraTags()
            var formatted = false
            val discard = DiscardingTrackOutput()
            extractor.init(object : ExtractorOutput {
                override fun track(id: Int, type: Int): TrackOutput = object : TrackOutput by discard {
                    override fun format(format: Format) {
                        if (type != C.TRACK_TYPE_AUDIO) return
                        formatted = true
                        val metadata = format.metadata ?: return
                        val builder = MediaMetadata.Builder()
                        for (i in 0 until metadata.length()) {
                            val entry = metadata[i]
                            entry.populateMediaMetadata(builder)
                            when (entry) {
                                is VorbisComment -> when (entry.key.uppercase(java.util.Locale.ROOT)) {
                                    "ARTISTS" -> artists += entry.value
                                    "ARTIST" -> fallback += entry.value
                                    "COMPOSER", "COMPOSERS" -> composers += entry.value
                                    "RELEASEDATE" -> ReleaseDate.parse(entry.value)?.let { date = it }
                                    "DATE" -> if (date == null) date = ReleaseDate.parse(entry.value)
                                }
                                is InternalFrame -> when {
                                    entry.description.equals("ARTISTS", true) -> artists += entry.text
                                    ComposerTags.isComposerField(entry.description) -> composers += entry.text
                                }
                                is TextInformationFrame -> when {
                                    entry.id == "TXXX" && entry.description.equals("ARTISTS", true) -> artists += entry.values
                                    entry.id == "TPE1" || entry.id == "TP1" -> fallback += entry.values
                                    ComposerTags.isComposerField(entry.id) ||
                                        (entry.id == "TXXX" && ComposerTags.isComposerField(entry.description)) -> composers += entry.values
                                }
                            }
                        }
                        val meta = builder.build()
                        // Media3 maps MP4 ©wrt and other supported composer metadata here.
                        if (composers.isEmpty()) meta.composer?.toString()?.let { composers += it }
                        if (date == null) {
                            val year = meta.releaseYear ?: meta.recordingYear
                            val month = if (meta.releaseYear != null) meta.releaseMonth else meta.recordingMonth
                            val day = if (meta.releaseYear != null) meta.releaseDay else meta.recordingDay
                            if (year != null) date = ReleaseDate.parse("%04d".format(java.util.Locale.ROOT, year) +
                                (month?.let { "-%02d".format(java.util.Locale.ROOT, it) } ?: "") +
                                (day?.let { "-%02d".format(java.util.Locale.ROOT, it) } ?: ""))
                        }
                    }
                }
                override fun endTracks() = Unit
                override fun seekMap(seekMap: SeekMap) = Unit
            })
            val position = PositionHolder()
            repeat(10_000) {
                currentCoroutineContext().ensureActive()
                when (extractor.read(input, position)) {
                    Extractor.RESULT_END_OF_INPUT -> return result()
                    Extractor.RESULT_SEEK -> input = open(position.position)
                }
                if (formatted) return result()
            }
        } catch (_: IOException) { /* Unreadable/unsupported metadata keeps the MediaStore tags. */
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: RuntimeException) { /* Broken files must not prevent the rest of the scan. */
        } finally {
            extractors.forEach { it.release() }
            source.close()
        }
        return result()
    }
}
