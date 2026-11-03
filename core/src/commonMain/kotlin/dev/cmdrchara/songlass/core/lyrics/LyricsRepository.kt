package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.random.Random

/** On-demand lookup: verified imported file, embedded/local lyrics, then the optional online source. */
class LyricsRepository(
    private val local: LyricsSource = LyricsSource.Empty,
    private val remote: LyricsSource = LyricsSource.Empty,
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    /** Partitions cached results when the configured online providers change. */
    private val cacheVersion: () -> Int = { 0 },
    /** Pure, thread-safe lookup; null denies both file reads and metadata requests. */
    private val resolveSelection: (Track) -> LyricsSelection? = { LyricsSelection(it) },
) {
    private data class Entry(val selection: LyricsSelection, val result: LyricsResult, val sourceVersion: Int)
    private data class Cache(val entries: List<Entry> = emptyList(), val revision: Long = 0)

    private val cache = MutableStateFlow(Cache())
    private val _changes = MutableStateFlow(0L)
    /** Invalidates visible results when imports or the verified library boundary change. */
    val changes = _changes.asStateFlow()
    private val writeMutex = Mutex()

    suspend fun load(track: Track, refresh: Boolean = false): LyricsResult {
        while (true) {
            currentCoroutineContext().ensureActive()
            val selection = resolveSelection(track) ?: return LyricsUnavailable
            val revision = cache.value.revision
            val sourceVersion = cacheVersion()
            fun isCurrent() = resolveSelection(track) == selection
            var failed = false
            fun imported(): Lyrics? = try {
                importedRecord(selection)?.text?.let { LyricsParser.parse(it, LyricsOrigin.File) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) { failed = true; null }

            val importedLyrics = imported()
            if (importedLyrics != null) {
                if (!isCurrent()) return LyricsUnavailable
                if (cache.value.revision != revision) continue
                return LyricsResult.Found(importedLyrics)
            }
            val cachedResult = if (refresh) null else cached(selection, sourceVersion)
            if (cachedResult != null) {
                if (!isCurrent()) return LyricsUnavailable
                if (cache.value.revision != revision || sourceVersion != cacheVersion()) continue
                return cachedResult
            }

            suspend fun read(source: LyricsSource): Lyrics? {
                if (!isCurrent()) return null
                return try {
                    source.load(selection.track).also { currentCoroutineContext().ensureActive() }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { currentCoroutineContext().ensureActive(); failed = true; null }
            }
            val localLyrics = read(local)?.forSegment(selection.track)
            if (!isCurrent()) return LyricsUnavailable
            // A stale local lookup must not start another file read or send track metadata.
            val lyrics = imported() ?: localLyrics ?: read(remote)
            if (!isCurrent()) return LyricsUnavailable
            val result = lyrics?.let(LyricsResult::Found)
                ?: if (failed) LyricsResult.Failed else LyricsResult.NotFound

            if (sourceVersion != cacheVersion()) continue
            // Restore, relink or access invalidation starts a fresh validated lookup.
            if (cache.value.revision != revision) continue
            val latest = imported()?.let(LyricsResult::Found) ?: result
            val final = if (latest == LyricsResult.NotFound && failed) LyricsResult.Failed else latest
            if (remember(selection, final, revision, sourceVersion) && isCurrent()) {
                if (sourceVersion == cacheVersion() && cache.value.revision == revision) return final
            } else if (!isCurrent()) return LyricsUnavailable
        }
    }

    /** No migration/write: restore must already contain its final safe bindings. */
    fun reload() = selectionChanged()

    /** Called at scan/permission/restore boundaries; no work occurs on playback ticks. */
    fun selectionChanged() {
        cache.update { Cache(revision = it.revision + 1) }
        _changes.update { it + 1 }
    }

    /** Restore uses this same lock, so a background import cannot race the transaction. */
    suspend fun <T> withImportWritesPaused(block: suspend () -> T): T = writeMutex.withLock { block() }

    /** Compatibility API for synchronous callers; UI uses [importText] off the main thread. */
    fun import(track: Track, text: String): Boolean {
        if (!writeMutex.tryLock()) return false
        return try { importLocked(track, text) == LyricsImportResult.Saved } finally { writeMutex.unlock() }
    }

    suspend fun importText(track: Track, text: String): LyricsImportResult = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        writeMutex.withLock { importLocked(track, text) { context.ensureActive() } }
    }

    /** Captured before opening the system picker; no URI-only or unverified selection is restorable. */
    fun prepareImportRequest(track: Track): LyricsImportRequest? =
        resolveSelection(track)?.let(LyricsImportRequest::create)

    /** Lets the restored picker wait for a complete scan without repeatedly opening its document. */
    fun isImportRequestAvailable(request: LyricsImportRequest): Boolean =
        resolveSelection(request.identity.asTrack())?.let {
            it.canonicalId && it.track.id == request.identity.id
        } == true

    /** Called again after recreation/scan; it does not write and never substitutes another song. */
    suspend fun previewImport(request: LyricsImportRequest, text: String): LyricsImportReview = withContext(Dispatchers.Default) {
        writeMutex.withLock {
            try {
                currentCoroutineContext().ensureActive()
                val requested = request.identity.asTrack()
                val selection = resolveSelection(requested)?.takeIf {
                    it.canonicalId && it.track.id == request.identity.id
                } ?: return@withLock LyricsImportReview.Rejected(LyricsImportResult.Unavailable)
                val lyrics = LyricsParser.parse(text, LyricsOrigin.File)
                    ?: return@withLock LyricsImportReview.Rejected(LyricsImportResult.Invalid)
                val key = ImportedLyricsCodec.trackKey(selection.track.id)
                    ?: return@withLock LyricsImportReview.Rejected(LyricsImportResult.Invalid)
                val previous = store.getString(key)
                if (resolveSelection(requested) != selection) {
                    return@withLock LyricsImportReview.Rejected(LyricsImportResult.Unavailable)
                }
                currentCoroutineContext().ensureActive()
                LyricsImportReview.Ready(LyricsImportPreview(selection.track, lyrics, previous != null,
                    text, selection, previous))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { LyricsImportReview.Rejected(LyricsImportResult.Failed) }
        }
    }

    /** Explicit confirmation only. Cancel/dismiss never calls this method. */
    suspend fun confirmImport(preview: LyricsImportPreview): LyricsImportResult = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        writeMutex.withLock { importLocked(preview.track, preview.text, preview) { context.ensureActive() } }
    }

    private fun importLocked(
        track: Track,
        text: String,
        reviewed: LyricsImportPreview? = null,
        checkActive: () -> Unit = {},
    ): LyricsImportResult = guardedWrite {
        checkActive()
        val selection = resolveSelection(track) ?: return@guardedWrite LyricsImportResult.Unavailable
        if (reviewed != null && selection != reviewed.selection) return@guardedWrite LyricsImportResult.Changed
        if (LyricsParser.parse(text, LyricsOrigin.File) == null) return@guardedWrite LyricsImportResult.Invalid
        val key = ImportedLyricsCodec.trackKey(selection.track.id) ?: return@guardedWrite LyricsImportResult.Invalid
        val value = ImportedLyricsCodec.encode(ImportedLyricsRecord.bound(selection, text))
        val previous = store.getString(key)
        if (reviewed != null && previous != reviewed.previousTarget) return@guardedWrite LyricsImportResult.Changed
        val writing = linkedMapOf(key to value)
        // Damaged/unassigned data is retained, rather than silently overwritten by a new import.
        if (previous != null && ImportedLyricsCodec.decode(previous)?.matches(selection) != true) {
            writing[newRecoveryKey(writing.keys)] = previous
        }
        if (resolveSelection(track) != selection) return@guardedWrite LyricsImportResult.Unavailable
        checkActive()
        store.replace(emptySet(), writing)
        selectionChanged()
        LyricsImportResult.Saved
    }

    /** Explicit recovery work only: playback lookup never enumerates the store. */
    suspend fun recoverableImports(): List<LyricsRecoveryEntry> = withContext(Dispatchers.Default) {
        val snapshot = store.snapshot() ?: error("Lyrics store cannot enumerate recovery records")
        buildList {
            for ((key, value) in snapshot.entries.sortedBy { it.key }) {
                currentCoroutineContext().ensureActive()
                val legacy = key.startsWith(ImportedLyricsCodec.LEGACY_PREFIX) && key.length > ImportedLyricsCodec.LEGACY_PREFIX.length
                if (!legacy && !ImportedLyricsCodec.isKey(key)) continue
                val record = if (legacy) null else ImportedLyricsCodec.decode(value)
                val text = if (legacy) value else record?.text
                val parsed = text?.let { LyricsParser.parse(it, LyricsOrigin.File) }
                if (parsed != null && record != null && record.binding != LyricsBinding.Unassigned) {
                    val selected = resolveSelection(record.asTrack())
                    if (selected != null && record.matches(selected) && ImportedLyricsCodec.trackKey(selected.track.id) == key) continue
                }
                add(LyricsRecoveryEntry(key, record?.title?.takeIf { it.isNotBlank() },
                    parsed?.lines?.firstOrNull { it.text.isNotBlank() }?.text?.take(160).orEmpty(),
                    parsed != null, value, text))
            }
        }
    }

    /** Reads the exact record and target import shown by the confirmation, without writing. */
    suspend fun previewRelink(track: Track, entry: LyricsRecoveryEntry): LyricsRelinkPreview? = withContext(Dispatchers.Default) {
        writeMutex.withLock {
            currentCoroutineContext().ensureActive()
            val selection = resolveSelection(track) ?: return@withLock null
            if (!entry.canLink || store.getString(entry.key) != entry.storedValue) return@withLock null
            val lyrics = entry.text?.let { LyricsParser.parse(it, LyricsOrigin.File) } ?: return@withLock null
            val key = ImportedLyricsCodec.trackKey(selection.track.id) ?: return@withLock null
            val existing = store.getString(key)
            if (resolveSelection(track) != selection) return@withLock null
            currentCoroutineContext().ensureActive()
            LyricsRelinkPreview(entry, selection.track, lyrics, key != entry.key && existing != null, selection, existing)
        }
    }

    /** Cancel is simply not calling this method. Changed text/target always needs another preview. */
    suspend fun relink(preview: LyricsRelinkPreview): LyricsImportResult = withContext(Dispatchers.Default) {
        writeMutex.withLock {
            guardedWrite {
                val selection = resolveSelection(preview.track) ?: return@guardedWrite LyricsImportResult.Unavailable
                if (selection != preview.selection) return@guardedWrite LyricsImportResult.Changed
                val key = ImportedLyricsCodec.trackKey(selection.track.id) ?: return@guardedWrite LyricsImportResult.Invalid
                if (store.getString(preview.entry.key) != preview.entry.storedValue ||
                    store.getString(key) != preview.previousTarget) return@guardedWrite LyricsImportResult.Changed
                val text = preview.entry.text ?: return@guardedWrite LyricsImportResult.Invalid
                if (LyricsParser.parse(text, LyricsOrigin.File) == null) return@guardedWrite LyricsImportResult.Invalid
                val writing = linkedMapOf(key to ImportedLyricsCodec.encode(ImportedLyricsRecord.bound(selection, text)))
                val previous = preview.previousTarget
                if (key != preview.entry.key && previous != null &&
                    ImportedLyricsCodec.decode(previous)?.matches(selection) != true) {
                    writing[newRecoveryKey(writing.keys + preview.entry.key)] = previous
                }
                if (resolveSelection(preview.track) != selection) return@guardedWrite LyricsImportResult.Unavailable
                currentCoroutineContext().ensureActive()
                store.replace(setOf(preview.entry.key), writing)
                selectionChanged()
                LyricsImportResult.Saved
            }
        }
    }

    private fun importedRecord(selection: LyricsSelection): ImportedLyricsRecord? {
        val key = ImportedLyricsCodec.trackKey(selection.track.id) ?: return null
        return store.getString(key)?.let(ImportedLyricsCodec::decode)?.takeIf { it.matches(selection) }
    }

    private fun newRecoveryKey(reserved: Set<String>): String {
        repeat(16) {
            val key = ImportedLyricsCodec.PREFIX + "r." + Random.nextLong().toULong().toString(16) +
                Random.nextLong().toULong().toString(16)
            if (key !in reserved && store.getString(key) == null) return key
        }
        error("Unable to allocate a recovery record")
    }

    private inline fun guardedWrite(block: () -> LyricsImportResult): LyricsImportResult = try { block() }
    catch (cancelled: CancellationException) { throw cancelled }
    catch (_: Exception) { LyricsImportResult.Failed }

    private fun cached(selection: LyricsSelection, sourceVersion: Int): LyricsResult? {
        while (true) {
            val current = cache.value
            val entry = current.entries.firstOrNull { it.selection == selection && it.sourceVersion == sourceVersion } ?: return null
            val updated = current.copy(entries = current.entries.filterNot { it.selection == selection } + entry)
            if (cache.compareAndSet(current, updated)) return entry.result
        }
    }

    private fun remember(selection: LyricsSelection, result: LyricsResult, revision: Long, sourceVersion: Int): Boolean {
        while (true) {
            val current = cache.value
            if (current.revision != revision) return false
            if (result == LyricsResult.Failed || result == LyricsUnavailable) return true
            val updated = current.copy(entries = (current.entries.filterNot { it.selection == selection } +
                Entry(selection, result, sourceVersion)).takeLast(CACHE_CAPACITY))
            if (cache.compareAndSet(current, updated)) return true
        }
    }

    private companion object { const val CACHE_CAPACITY = 32 }
}
