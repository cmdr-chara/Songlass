package dev.cmdrchara.songlass.library

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.system.Os
import android.system.OsConstants
import com.kyant.taglib.Metadata
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import dev.cmdrchara.songlass.core.model.EditableTags
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagEditRequest
import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.resume

/** TagLib only edits a private staged file. No audio decoding/encoding or artwork recompression. */
class TagLibFileTagEditor(
    private val context: Context,
    private val withFileWrite: suspend (Track, suspend () -> Unit) -> Unit = { _, write -> write() },
    private val afterSave: suspend () -> Unit = {},
) : FileTagEditor {
    private val native = TagLib
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    // Internal files (not evictable cache) retain a recovery copy if a commit is interrupted.
    private val directory = File(context.filesDir, "tag-editor")
    private val journal = File(directory, "pending.json")

    override suspend fun read(track: Track): EditableTags = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (journal.exists() && JSONObject(journal.readText()).getString("uri") == track.uri) {
                recoverPending(track.uri.toUri())
                scan(track.uri.toUri())
            }
            resolver.openFileDescriptor(track.uri.toUri(), "r")?.let { descriptor ->
                // TagLib's FileStream owns/closes the descriptor. Never pass a borrowed fd.
                val metadata = TagLib.getMetadata(descriptor.detachFd(), false) ?: throw IOException("Unsupported tags")
                metadata.editable()
            } ?: throw IOException("Cannot open audio")
        }
    }

    override suspend fun save(request: TagEditRequest) = withContext(Dispatchers.IO) {
        require(request.edited.isValidComparedTo(request.original)) { "Invalid tags" }
        mutex.withLock {
            directory.mkdirs()
            recoverPending(request.track.uri.toUri())
            val changes = request.edited.changesFrom(request.original)
            if (changes.isEmpty() && request.coverUri == null) return@withLock
            val uri = request.track.uri.toUri()
            // Opening rw requests consent before any expensive staging, without truncation.
            resolver.openFileDescriptor(uri, "rw")?.use { destination ->
                val suffix = request.track.fileName?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
                    ?.let { ".$it" } ?: ".audio"
                val original = File.createTempFile("original-", suffix, directory)
                val staged = File.createTempFile("staged-", suffix, directory)
                var retainRecovery = false
                try {
                    resolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(original).use { output -> input.copyTo(output); output.fd.sync() }
                    } ?: throw IOException("Cannot read original audio")
                    original.copyTo(staged, overwrite = true)
                    val before = metadata(original)
                    // Fail rather than silently overwrite a field changed since the form was opened.
                    check(before.editable() == request.original) { "Tags changed while editing" }
                    val expected = HashMap(before.propertyMap)
                    for ((key, value) in changes) {
                        if (value.isEmpty()) expected.remove(key) else expected[key] = arrayOf(value)
                    }
                    request.edited.artistCreditsChangedFrom(request.original)?.let { credits ->
                        if ("ARTISTS" in expected) {
                            if (credits.isEmpty()) expected.remove("ARTISTS") else expected["ARTISTS"] = credits.toTypedArray()
                        }
                    }
                    if (changes.isNotEmpty()) check(nativeWrite(staged) { TagLib.savePropertyMap(it, expected) }) { "Cannot save tags" }
                    val mp4 = original.inputStream().use { input -> input.readNBytes(8).let { it.size == 8 && String(it, 4, 4, Charsets.US_ASCII) == "ftyp" } }
                    val coverUri = request.coverUri
                    val pictures = if (coverUri == null) before.pictures else replaceFrontCover(before.pictures, readCover(coverUri.toUri()), mp4)
                    if (request.coverUri != null) check(nativeWrite(staged) { TagLib.savePictures(it, pictures) }) { "Cannot save cover" }
                    val after = metadata(staged)
                    check(expected.keys == after.propertyMap.keys && expected.all { (k, v) -> v.contentEquals(after.propertyMap[k]) }) { "Tags did not round trip" }
                    check(pictures.contentEquals(after.pictures)) { "Cover did not round trip: expected ${pictures.toList()}, got ${after.pictures.toList()}" }
                    check(audioDigest(original) == audioDigest(staged)) { "Audio data changed" }
                    currentCoroutineContext().ensureActive()
                    check(fileDigest(original) == resolver.openInputStream(uri)?.use { digest(it) }) { "File changed while editing" }
                    // Once copying starts, navigating away cannot cancel a partially written file.
                    withContext(NonCancellable) {
                        withFileWrite(request.track) {
                            withContext(Dispatchers.IO) {
                                val record = JSONObject().put("uri", uri.toString()).put("original", original.name)
                                android.util.AtomicFile(journal).let { atomic ->
                                    val out = atomic.startWrite()
                                    try { out.write(record.toString().toByteArray()); atomic.finishWrite(out) }
                                    catch (error: Exception) { atomic.failWrite(out); throw error }
                                }
                                retainRecovery = true
                                try {
                                    copyInto(staged, destination)
                                    check(resolver.openInputStream(uri)?.use { digest(it) } == fileDigest(staged)) { "Incomplete save" }
                                } catch (failure: Exception) {
                                    try {
                                        copyInto(original, destination)
                                        check(resolver.openInputStream(uri)?.use { digest(it) } == fileDigest(original))
                                        journal.delete()
                                        retainRecovery = false
                                    } catch (restore: Exception) { failure.addSuppressed(restore) }
                                    throw failure
                                }
                                journal.delete()
                                retainRecovery = false
                            }
                        }
                        // A scanner failure after a verified commit must not imply the write failed.
                        kotlinx.coroutines.withTimeoutOrNull(15_000) { scan(uri) }
                        // Refresh is part of the committed transaction, independent of the form's lifetime.
                        try { afterSave() }
                        catch (failure: Exception) { android.util.Log.w("TagEditor", "File saved; library refresh pending", failure) }
                    }
                } finally {
                    staged.delete()
                    if (!retainRecovery) original.delete()
                }
            } ?: throw IOException("Cannot open audio for editing")
        }
    }

    suspend fun pendingRecoveryUri(): Uri? = withContext(Dispatchers.IO) {
        mutex.withLock { if (journal.exists()) JSONObject(journal.readText()).getString("uri").toUri() else null }
    }

    suspend fun recoverInterruptedSave() = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (journal.exists()) {
                val uri = JSONObject(journal.readText()).getString("uri").toUri()
                withContext(NonCancellable) { recoverPending(uri); kotlinx.coroutines.withTimeoutOrNull(15_000) { scan(uri) } }
            }
        }
    }

    /** Retried before the next save after a process interruption; never discard an un-restored original. */
    private fun recoverPending(requestedUri: Uri) {
        if (!journal.exists()) return
        val record = JSONObject(journal.readText())
        check(record.getString("uri") == requestedUri.toString()) { "An interrupted edit must be restored first" }
        val name = record.getString("original")
        check(name.startsWith("original-") && File(name).name == name)
        val original = File(directory, name)
        check(original.isFile) { "Missing recovery file" }
        resolver.openFileDescriptor(record.getString("uri").toUri(), "rw")?.use {
            copyInto(original, it)
            check(resolver.openInputStream(requestedUri)?.use { input -> digest(input) } == fileDigest(original)) { "Incomplete recovery" }
        } ?: throw IOException("Cannot restore interrupted save")
        journal.delete()
        original.delete()
    }

    private fun metadata(file: File): Metadata = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).let {
        TagLib.getMetadata(it.detachFd(), true) ?: throw IOException("Unsupported audio format")
    }

    private fun nativeWrite(file: File, write: (Int) -> Boolean): Boolean =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).let { write(it.detachFd()) }

    private fun Metadata.editable(): EditableTags {
        fun field(key: String) = propertyMap[key]?.joinToString("; ").orEmpty()
        return EditableTags(field("TITLE"), field("ARTIST"), field("ALBUM"), field("ALBUMARTIST"), field("TRACKNUMBER"), field("DISCNUMBER"), field("DATE"))
    }

    private fun readCover(uri: Uri): Picture {
        val data = resolver.openInputStream(uri)?.use { it.readNBytes(MAX_COVER_BYTES + 1) } ?: throw IOException("Cannot read picture")
        require(data.isNotEmpty() && data.size <= MAX_COVER_BYTES) { "Picture too large" }
        val info = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(data, 0, data.size, info)
        require(info.outWidth > 0 && info.outHeight > 0 && info.outMimeType in setOf("image/jpeg", "image/png")) { "Unsupported picture" }
        return Picture(data, "Front Cover", "Front Cover", info.outMimeType)
    }

    /** Replace only front artwork, retaining back covers, artist images and their descriptions. */
    private fun replaceFrontCover(pictures: Array<Picture>, cover: Picture, mp4: Boolean): Array<Picture> =
        if (mp4) {
            // MP4 covr stores an ordered list of images, with no description or picture type.
            (listOf(cover.copy(description = "", pictureType = "")) + pictures.drop(1)).toTypedArray()
        } else (listOf(cover) + pictures.filter { it.pictureType != "Front Cover" }).toTypedArray()

    private fun copyInto(file: File, destination: ParcelFileDescriptor) {
        Os.lseek(destination.fileDescriptor, 0, OsConstants.SEEK_SET)
        ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(destination.fileDescriptor)).use { output ->
            file.inputStream().use { it.copyTo(output) }
            output.flush()
            Os.ftruncate(destination.fileDescriptor, file.length())
            Os.fsync(destination.fileDescriptor)
        }
    }

    private suspend fun scan(uri: Uri) {
        // The row was opened through MediaStore. DATA is only used for scanner notification, never file writes.
        val path = runCatching {
            resolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: return
        suspendCancellableCoroutine { continuation ->
            MediaScannerConnection.scanFile(context, arrayOf(path), null) { _, _ ->
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    /** Encoded audio packets must be byte-identical; container headers/tags may legitimately move. */
    private fun audioDigest(file: File): String {
        val extractor = MediaExtractor()
        try {
            FileInputStream(file).use { extractor.setDataSource(it.fd) }
            val audio = (0 until extractor.trackCount).filter { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
            check(audio.isNotEmpty()) { "Cannot verify audio" }
            val result = MessageDigest.getInstance("SHA-256")
            for (track in audio) {
                extractor.selectTrack(track)
                extractor.seekTo(0, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                val format = extractor.getTrackFormat(track)
                result.update(format.getString(MediaFormat.KEY_MIME).orEmpty().toByteArray())
                var buffer = ByteBuffer.allocate(1024 * 1024)
                var samples = 0
                while (extractor.sampleTrackIndex >= 0) {
                    val size = extractor.sampleSize
                    check(size in 0..MAX_PACKET_BYTES.toLong()) { "Invalid audio packet" }
                    if (size > buffer.capacity()) buffer = ByteBuffer.allocate(size.toInt())
                    buffer.clear()
                    val count = extractor.readSampleData(buffer, 0)
                    if (count < 0) break
                    result.update(buffer.array(), 0, count)
                    samples++
                    if (!extractor.advance()) break
                }
                check(samples > 0) { "No audio packets" }
                extractor.unselectTrack(track)
            }
            return result.digest().hex()
        } finally { extractor.release() }
    }

    private fun fileDigest(file: File): String = file.inputStream().use { digest(it) }
    private fun digest(input: java.io.InputStream): String {
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            hash.update(buffer, 0, count)
        }
        return hash.digest().hex()
    }
    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_COVER_BYTES = 20 * 1024 * 1024
        const val MAX_PACKET_BYTES = 32 * 1024 * 1024
    }
}
