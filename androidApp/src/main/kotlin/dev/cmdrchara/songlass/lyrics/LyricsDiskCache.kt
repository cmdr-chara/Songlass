package dev.cmdrchara.songlass.lyrics

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Only successful exact lookups are cached; Android may evict this private cache at any time. */
internal class LyricsDiskCache(private val directory: File) {
    fun read(signature: String): String? {
        val file = entry(signature)
        val age = System.currentTimeMillis() - file.lastModified()
        if (!file.isFile || age !in 0..MAX_AGE_MS || file.length() > LrcLibLyricsSource.MAX_RESPONSE_BYTES) return null
        return try {
            AtomicFile(file).openRead().use { input ->
                val bytes = input.readNBytes(LrcLibLyricsSource.MAX_RESPONSE_BYTES + 1)
                if (bytes.size > LrcLibLyricsSource.MAX_RESPONSE_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        } catch (_: IOException) {
            null
        }
    }

    fun write(signature: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        if (bytes.size > LrcLibLyricsSource.MAX_RESPONSE_BYTES) return
        try {
            if (!directory.isDirectory && !directory.mkdirs()) return
            val atomic = AtomicFile(entry(signature))
            val stream = atomic.startWrite()
            try {
                stream.write(bytes)
                atomic.finishWrite(stream)
            } catch (error: IOException) {
                atomic.failWrite(stream)
                throw error
            }
            // The directory is app-owned and contains only hashed cache entries.
            directory.listFiles { file -> file.isFile && file.name.matches(ENTRY_NAME) }
                ?.sortedByDescending(File::lastModified)
                ?.drop(MAX_ENTRIES)
                ?.forEach { AtomicFile(it).delete() }
        } catch (_: IOException) {
            // An unavailable cache must not hide successfully fetched lyrics.
        }
    }

    private fun entry(signature: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray(Charsets.UTF_8))
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(directory, "$name.json")
    }

    private companion object {
        const val MAX_ENTRIES = 32
        const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
        val ENTRY_NAME = Regex("[0-9a-f]{64}\\.json")
    }
}
