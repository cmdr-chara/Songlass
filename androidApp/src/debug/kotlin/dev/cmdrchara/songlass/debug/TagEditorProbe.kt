package dev.cmdrchara.songlass.debug

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.kyant.taglib.TagLib
import dev.cmdrchara.songlass.core.model.TagEditRequest
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.library.TagLibFileTagEditor
import java.io.File
import java.security.MessageDigest

/** Only touches generated fixtures in the app's private tag-editor-qa cache directory. */
object TagEditorProbe {
    suspend fun run(context: Context) {
        val root = File(context.cacheDir, "tag-editor-qa").apply { mkdirs() }
        // Fixtures are packaged only in debug builds; app storage permissions stay untouched.
        for (name in context.assets.list("tag-editor-qa").orEmpty()) {
            context.assets.open("tag-editor-qa/$name").use { input -> File(root, name).outputStream().use { input.copyTo(it) } }
        }
        val writer = TagLibFileTagEditor(context)
        val cover = File(root, "cover.png")
        for (extension in listOf("flac", "mp3", "m4a", "ogg", "opus", "wav")) {
            val file = File(root, "fixture.$extension")
            if (!file.isFile) continue
            try {
                val track = Track("tag-qa-$extension", "QA", Uri.fromFile(file).toString(), fileName = file.name)
                val original = writer.read(track)
                val edited = original.copy(title = "Songlass QA – 夜", artist = "QA Artist", album = "QA Album", albumArtist = "QA Album Artist", trackNumber = "7/12", discNumber = "2/3", date = "2026-10-19")
                writer.save(TagEditRequest(track, original, edited, Uri.fromFile(cover).toString()))
                check(writer.read(track) == edited)
                val hash = sha(file)
                val badCover = File(root, "bad.png")
                try {
                    writer.save(TagEditRequest(track, edited, edited.copy(title = "Must not persist"), Uri.fromFile(badCover).toString()))
                    error("Invalid cover accepted")
                } catch (_: IllegalArgumentException) { check(sha(file) == hash) }
                try {
                    writer.save(TagEditRequest(track, edited, edited.copy(trackNumber = "invalid")))
                    error("Invalid number accepted")
                } catch (_: IllegalArgumentException) { check(sha(file) == hash) }
                val descriptors = descriptorsFor(file)
                repeat(32) { writer.read(track) }
                check(descriptorsFor(file) == descriptors) { "Descriptor leak" }
                val data = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).let { TagLib.getMetadata(it.detachFd())!! }
                Log.i("TagEditorProbe", "PASS $extension tags=${data.propertyMap.keys.sorted()} pictures=${data.pictures.size} bad-cover/invalid-tags preserved file; descriptors stable")
            } catch (error: Throwable) { Log.e("TagEditorProbe", "FAIL $extension", error) }
        }
        val restored = File(root, "fixture.flac")
        val expectedHash = sha(restored)
        val recoveryDirectory = File(context.filesDir, "tag-editor").apply { mkdirs() }
        val recovery = File.createTempFile("original-", ".flac", recoveryDirectory)
        restored.copyTo(recovery, overwrite = true)
        val journal = File(recoveryDirectory, "pending.json")
        journal.writeText(org.json.JSONObject().put("uri", Uri.fromFile(restored).toString()).put("original", recovery.name).toString())
        restored.writeBytes(byteArrayOf(0, 1, 2))
        TagLibFileTagEditor(context).recoverInterruptedSave()
        check(sha(restored) == expectedHash && !journal.exists() && !recovery.exists())
        Log.i("TagEditorProbe", "PASS simulated interrupted-commit recovery with recreated writer")
        Log.i("TagEditorProbe", "DONE")
    }

    private fun descriptorsFor(file: File): Int = File("/proc/self/fd").listFiles().orEmpty().count {
        runCatching { android.system.Os.readlink(it.absolutePath) == file.canonicalPath }.getOrDefault(false)
    }

    private fun sha(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(65536); while (true) { val n = input.read(b); if (n < 0) break; hash.update(b, 0, n) } }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }
}
