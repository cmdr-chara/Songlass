package dev.cmdrchara.songlass.core.artwork

import dev.cmdrchara.songlass.core.net.arrayOrNull
import dev.cmdrchara.songlass.core.net.asJsonObjectOrNull
import dev.cmdrchara.songlass.core.net.booleanOrNull
import dev.cmdrchara.songlass.core.net.intOrNull
import dev.cmdrchara.songlass.core.net.longOrNull
import dev.cmdrchara.songlass.core.net.parseJsonOrNull
import dev.cmdrchara.songlass.core.net.stringOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** One file of the private cover cache. */
data class StoredArtworkFile(val name: String, val sizeBytes: Long, val modifiedAtMs: Long)

/**
 * Raw access to the app-private directory holding downloaded covers.
 *
 * Only names, sizes and bytes cross this boundary: the platform decides where the directory is
 * (application files, private to the app, excluded from the media store). Implementations perform
 * the I/O off the caller's thread and never throw for a missing file; a failed write reports
 * `false`. Nothing here ever touches an audio file.
 */
interface ArtworkFileStore {
    suspend fun list(): List<StoredArtworkFile>
    suspend fun read(name: String): ByteArray?
    suspend fun write(name: String, bytes: ByteArray): Boolean
    suspend fun delete(name: String): Boolean
}

/**
 * Filesystem-safe, deterministic name for one logical album: a 64-bit FNV-1a hash, so two albums
 * never share a file (1 in 2^64) and the name never contains a title, an artist or a path.
 * The extension is deliberately meaningless: decoders sniff the image content.
 */
fun artworkFileName(subjectKey: String): String {
    var hash = FNV_OFFSET_BASIS
    for (byte in subjectKey.encodeToByteArray()) {
        hash = hash xor (byte.toLong() and 0xff)
        hash *= FNV_PRIME
    }
    return "cover-" + hash.toULong().toString(16).padStart(16, '0') + ".img"
}

private const val FNV_OFFSET_BASIS = -3750763034362895579L // 0xcbf29ce484222325
private const val FNV_PRIME = 1099511628211L // 0x100000001b3

/** One cover in the private cache, bound to the logical album it was chosen for. */
data class DownloadedArtwork(
    val subject: ArtworkSubject,
    val fileName: String,
    val sizeBytes: Long,
    val savedAtMs: Long,
    /** Automatic matches yield to an embedded/folder image or an explicit user choice. */
    val automatic: Boolean = false,
) {
    val key: String get() = subject.key
}

/**
 * Downloaded covers in the app's private storage.
 *
 * Explicit choices and conservative automatic matches are distinguished in the index.
 * Automatic entries yield to local images and explicit choices, and cannot evict explicit entries.
 * Entries are keyed by [ArtworkSubject], so a rescan, a rename of the folder or
 * a new MediaStore row keeps the cover attached to the album. An index file in the same directory
 * stores the mapping between key and hashed file name; it is re-read on start and repaired when a
 * file has disappeared. The cache is bounded in bytes and in entries, evicting the oldest cover
 * first; the cover just saved is kept even if it alone exceeds the limit, because the user asked
 * for it. Nothing here writes to audio files or to MediaStore.
 *
 * Mutable state is published through [state]; all mutations are serialised, so the UI can save a
 * cover while another screen renders the previous map.
 */
class DownloadedArtworks(
    private val files: ArtworkFileStore,
    private val now: () -> Long,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val mutex = Mutex()

    private val _state = MutableStateFlow<Map<String, DownloadedArtwork>>(emptyMap())

    /** Every stored cover by [ArtworkSubject.key]. */
    val state: StateFlow<Map<String, DownloadedArtwork>> = _state.asStateFlow()

    private val _revision = MutableStateFlow(0L)

    /** Bumps on every change, so image caches can drop a cover the user removed. */
    val revision: StateFlow<Long> = _revision.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Automatic discovery may not evict confirmed user choices to make room. */
    val hasAutomaticCapacity: Boolean get() {
        val explicit = _state.value.values.filterNot { it.automatic }
        return explicit.size < maxEntries && explicit.sumOf { it.sizeBytes } < maxBytes
    }

    fun get(key: String): DownloadedArtwork? = _state.value[key]

    /** Reads the index and prunes entries whose file is gone; safe to call again on each start. */
    suspend fun load(): Unit = mutex.withLock {
        val listed = runCatching { files.list() }.getOrNull().orEmpty().associateBy { it.name }
        val stored = readIndex()
        currentCoroutineContext().ensureActive()
        val present = stored
            .filter { listed[it.fileName] != null }
            .associateBy { it.key }
        publish(present)
        if (present.size != stored.size) writeIndex(present)
        _loaded.value = true
    }

    /** Stores [bytes] as the cover of [subject]; null when the file could not be written. */
    suspend fun save(subject: ArtworkSubject, bytes: ByteArray): DownloadedArtwork? = mutex.withLock {
        if (bytes.isEmpty()) return null
        val name = artworkFileName(subject.key)
        if (!runCatching { files.write(name, bytes) }.getOrDefault(false)) return null
        val record = DownloadedArtwork(subject, name, bytes.size.toLong(), now())
        val next = trim(_state.value + (subject.key to record), keep = subject.key)
        publish(next)
        writeIndex(next)
        record
    }

    /** Atomic missing-only save: a manual selection made during lookup is never overwritten. */
    suspend fun saveAutomaticIfAbsent(
        subject: ArtworkSubject,
        bytes: ByteArray,
        canSave: () -> Boolean,
    ): DownloadedArtwork? = mutex.withLock {
        if (bytes.isEmpty() || _state.value[subject.key] != null || !canSave()) return null
        val name = artworkFileName(subject.key)
        val written = runCatching { files.write(name, bytes) }.getOrDefault(false)
        if (!written || !canSave()) {
            // A cancelled IO dispatcher may report cancellation after the file was written.
            // This entry did not exist before this lock; cleanup cannot remove a manual choice.
            withContext(NonCancellable) { runCatching { files.delete(name) } }
            return null
        }
        val record = DownloadedArtwork(subject, name, bytes.size.toLong(), now(), automatic = true)
        val explicit = _state.value.values.filterNot { it.automatic }
        if (explicit.size + 1 > maxEntries || explicit.sumOf { it.sizeBytes } + bytes.size > maxBytes) {
            withContext(NonCancellable) { runCatching { files.delete(name) } }
            return null
        }
        // Complete index bookkeeping even if this owner is cancelled during pruning. The fresh
        // guard still prevents publishing an automatic choice after its source/network changed.
        withContext(NonCancellable) {
            val next = trim(_state.value + (subject.key to record), keep = subject.key, automaticOnly = true)
            if (!canSave()) {
                runCatching { files.delete(name) }
                val retained = next - subject.key
                publish(retained)
                writeIndex(retained)
                return@withContext null
            }
            publish(next)
            writeIndex(next)
            record
        }
    }

    /** Removes the cover of [key]; true when something was stored and deleted. */
    suspend fun remove(key: String): Boolean = mutex.withLock {
        val record = _state.value[key] ?: return false
        val deleted = runCatching { files.delete(record.fileName) }.getOrDefault(false)
        val next = _state.value - key
        publish(next)
        writeIndex(next)
        deleted
    }

    /** Removes every downloaded cover; returns how many were stored. */
    suspend fun clear(): Int = mutex.withLock {
        val records = _state.value
        records.values.forEach { runCatching { files.delete(it.fileName) } }
        publish(emptyMap())
        writeIndex(emptyMap())
        records.size
    }

    /** The image bytes of [key], or null when it is not stored or the file is unreadable. */
    suspend fun bytesOf(key: String): ByteArray? {
        val record = _state.value[key] ?: return null
        return runCatching { files.read(record.fileName) }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    private fun publish(next: Map<String, DownloadedArtwork>) {
        if (next == _state.value) return
        _state.value = next
        _revision.value = _revision.value + 1
    }

    /** Drops the oldest covers until both limits hold; [keep] is never evicted in this pass. */
    private suspend fun trim(covers: Map<String, DownloadedArtwork>, keep: String, automaticOnly: Boolean = false): Map<String, DownloadedArtwork> {
        if (covers.size <= maxEntries && covers.values.sumOf { it.sizeBytes } <= maxBytes) return covers
        val removable = covers.values
            .filter { it.key != keep && (!automaticOnly || it.automatic) }
            .sortedWith(compareBy<DownloadedArtwork> { it.savedAtMs }.thenBy { it.key })
        var result = covers
        for (candidate in removable) {
            if (result.size <= maxEntries && result.values.sumOf { it.sizeBytes } <= maxBytes) break
            runCatching { files.delete(candidate.fileName) }
            result = result - candidate.key
        }
        return result
    }

    private suspend fun readIndex(): List<DownloadedArtwork> {
        val bytes = runCatching { files.read(INDEX_FILE) }.getOrNull() ?: return emptyList()
        val text = bytes.decodeToString()
        val root = parseJsonOrNull(text).asJsonObjectOrNull() ?: return emptyList()
        val version = root.intOrNull("version") ?: return emptyList()
        if (version > FORMAT_VERSION) return emptyList()
        val entries = root.arrayOrNull("entries") ?: return emptyList()
        return entries.mapNotNull { element ->
            val entry = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val artist = entry.stringOrNull("artist") ?: ""
            val album = entry.stringOrNull("album") ?: return@mapNotNull null
            val file = entry.stringOrNull("file") ?: return@mapNotNull null
            val trackId = entry.stringOrNull("trackId")
            val subject = if (trackId == null) ArtworkSubject.of(artist, album)
                else if (trackId.isNotBlank() && album.isNotBlank()) ArtworkSubject(artist, album, trackId) else null
            subject ?: return@mapNotNull null
            // A name that does not belong to this subject is not trusted for a write or a delete.
            if (file != artworkFileName(subject.key)) return@mapNotNull null
            DownloadedArtwork(
                subject = subject,
                fileName = file,
                sizeBytes = entry.longOrNull("size")?.takeIf { it >= 0 } ?: 0,
                savedAtMs = entry.longOrNull("saved")?.takeIf { it >= 0 } ?: 0,
                automatic = entry.booleanOrNull("automatic") ?: false,
            )
        }
    }

    private suspend fun writeIndex(covers: Map<String, DownloadedArtwork>) {
        val json = buildJsonObject {
            put("version", JsonPrimitive(FORMAT_VERSION))
            put(
                "entries",
                JsonArray(
                    covers.values
                        .sortedBy { it.key }
                        .map { cover ->
                            buildJsonObject {
                                put("artist", JsonPrimitive(cover.subject.artist))
                                put("album", JsonPrimitive(cover.subject.album))
                                cover.subject.trackId?.let { put("trackId", JsonPrimitive(it)) }
                                put("file", JsonPrimitive(cover.fileName))
                                put("size", JsonPrimitive(cover.sizeBytes))
                                put("saved", JsonPrimitive(cover.savedAtMs))
                                if (cover.automatic) put("automatic", JsonPrimitive(true))
                            }
                        },
                ),
            )
        }
        runCatching { files.write(INDEX_FILE, json.toString().encodeToByteArray()) }
    }

    companion object {
        /** The index lives beside the covers; it is not an image and is never decoded. */
        const val INDEX_FILE = "index.json"

        const val FORMAT_VERSION = 1

        /** 32 MiB: a few hundred covers, or well over a hundred at the 600 px this feature keeps. */
        const val DEFAULT_MAX_BYTES = 32L * 1024L * 1024L

        const val DEFAULT_MAX_ENTRIES = 120
    }
}
