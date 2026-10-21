package dev.cmdrchara.songlass.lyrics

import dev.cmdrchara.songlass.core.lyrics.LyricsTransportPolicy

import android.util.AtomicFile
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/** Only successful exact lookups are cached; Android may evict this private cache at any time. */
internal class LyricsDiskCache(
    private val directory: File,
    private val maxEntryBytes: Int = 512 * 1024,
) {
    init {
        require(maxEntryBytes in 1 until Int.MAX_VALUE)
    }

    fun read(signature: String): String? {
        val file = entry(signature)
        val age = System.currentTimeMillis() - file.lastModified()
        if (!file.isFile || age !in 0..MAX_AGE_MS || file.length() > maxEntryBytes) return null
        return try {
            AtomicFile(file).openRead().use { input ->
                val bytes = input.readNBytes(maxEntryBytes + 1)
                if (bytes.size > maxEntryBytes) null else LyricsTransportPolicy.decodeUtf8(bytes, maxBytes = maxEntryBytes.coerceAtMost(LyricsTransportPolicy.MAX_RESPONSE_BYTES))
            }
        } catch (_: IOException) {
            null
        }
    }

    fun write(signature: String, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxEntryBytes) return
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
