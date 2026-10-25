package dev.cmdrchara.songlass.debug

import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Entirely synthetic SAF fixtures. Reads no real preferences, history, library or media paths. */
internal object VisualAuditDocuments {
    suspend fun prepare(app: SonglassApplication) = withContext(Dispatchers.IO) {
        val syntheticValues = mapOf("settings.reduce_motion" to "false")
        val store = InMemoryKeyValueStore().apply {
            syntheticValues.forEach { (key, value) -> putString(key, value) }
        }
        check(store.snapshot() == syntheticValues)
        val backup = DataBackup(store, clock = { 0L })
        val archive = backup.export(emptyList())
        // Public preview decodes the same format and verifies its checksum without applying it.
        backup.preview(archive.text, emptyList())
        check(store.snapshot() == syntheticValues)
        val playlist = "#EXTM3U\n#EXTINF:-1,QA Synthetic First\n" +
            "QA-Synthetic-Fixtures-9d9e735e/first-not-present.flac\n" +
            "#EXTINF:-1,QA Synthetic Second\n" +
            "QA-Synthetic-Fixtures-9d9e735e/second-not-present.flac\n"
        val profile = "Preamp: -4 dB\nFilter 1: ON LSC Fc 100 Hz Gain 3 dB Q 0.7\n" +
            "Filter 2: ON PK Fc 1000 Hz Gain -2 dB Q 1.41\n"
        val directory = File(app.cacheDir, "visual-audit-documents")
        check(directory.mkdirs() || directory.isDirectory) { "Cannot create private QA document directory" }
        File(directory, "synthetic-backup.songlass").writeText(archive.text, Charsets.UTF_8)
        File(directory, "profile.txt").writeText(profile, Charsets.UTF_8)
        File(directory, "playlist.m3u").writeText(playlist, Charsets.UTF_8)
    }
}
