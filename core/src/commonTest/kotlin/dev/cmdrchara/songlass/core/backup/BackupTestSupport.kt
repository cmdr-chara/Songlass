package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore

internal const val NOW = 1_700_000_000_000L

/** Text with the characters most likely to break a hand-made serialization. */
internal const val ODD = "A:b\u001F\u001E\u001D\n\r\t\u0000 12:34 é 日本語 😀"

/** A track whose id, URI and storage path are independent, as after a MediaStore reindex. */
internal fun song(
    id: String,
    path: String? = "Music/$id.mp3",
    title: String = "Song $id",
    artist: String? = "Artist",
    album: String? = "Album",
    durationMs: Long = 200_000L,
) = Track(
    id = id,
    title = title,
    uri = "content://media/external/audio/media/$id",
    artist = artist,
    album = album,
    durationMs = durationMs,
    folder = path?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() },
    fileName = path?.substringAfterLast('/'),
)

private fun field(value: String?) = if (value == null) "-1:" else "${value.length}:$value"

private fun line(vararg fields: String?) = fields.joinToString("") { field(it) } + "\n"

/** Appends a correct end record, so a hand-built file is only wrong in the way a test makes it wrong. */
internal fun sealed(body: String) = body + line("E", BackupFormat.checksum(body, body.length))

/** A well-formed file with exactly the given entries and `T` records (id, uri, path, title, artist, album, duration). */
internal fun handBuilt(
    entries: List<Pair<String, String>>,
    sources: List<List<String?>> = emptyList(),
    declaredEntries: Int = entries.size,
): String = sealed(
    "SONGLASS-BACKUP 1\n" +
        line("H", "0", declaredEntries.toString(), sources.size.toString()) +
        entries.joinToString("") { (key, value) -> line("K", key, value) } +
        sources.joinToString("") { line("T", *it.toTypedArray()) },
)

/**
 * A store whose [failOnPut]th write throws, once. It does not override `replace`, so the default
 * put-and-roll-back implementation is what gets exercised.
 */
internal class FailingStore(val inner: InMemoryKeyValueStore, private val failOnPut: Int) : KeyValueStore {
    private var puts = 0
    override fun getString(key: String): String? = inner.getString(key)
    override fun putString(key: String, value: String?) {
        puts++
        if (puts == failOnPut) error("disk full")
        inner.putString(key, value)
    }
    override fun snapshot(): Map<String, String> = inner.snapshot()
}

internal fun storeOf(vararg entries: Pair<String, String>) = InMemoryKeyValueStore().apply {
    entries.forEach { (key, value) -> putString(key, value) }
}
