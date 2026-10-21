package dev.cmdrchara.songlass.library

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import androidx.core.net.toUri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.DocumentsContract
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import com.kyant.taglib.Metadata
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import dev.cmdrchara.songlass.core.model.EditableTags
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagEditRequest
import dev.cmdrchara.songlass.core.model.TagEditSnapshot
import dev.cmdrchara.songlass.core.model.TagEditSourceChangedException
import dev.cmdrchara.songlass.core.model.TagEditWitness
import dev.cmdrchara.songlass.core.model.TagRecoveryInfo
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.tags.TagEditSelection
import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException
import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException.Reason
import dev.cmdrchara.songlass.core.tags.TagWritePrefixProof
import dev.cmdrchara.songlass.core.tags.TagRecoveryAction
import dev.cmdrchara.songlass.core.tags.TagRecoveryFingerprint
import dev.cmdrchara.songlass.core.tags.TagRecoveryPolicy
import dev.cmdrchara.songlass.core.tags.TagRecoveryExportPolicy
import dev.cmdrchara.songlass.core.tags.TagRecoveryExportReceipt
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
import java.io.InputStream
import java.io.OutputStream
import java.io.FilterOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.DigestOutputStream
import java.security.DigestInputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume

/** TagLib only edits a private staged file. No audio decoding/encoding or artwork recompression. */
class TagLibFileTagEditor(
    private val context: Context,
    private val withFileWrite: suspend (String, suspend () -> Unit) -> Unit = { _, write -> write() },
    /** Production supplies the complete current library; the default is for isolated native fixtures. */
    private val isCurrentSelection: suspend (Track) -> Boolean = { true },
    /** Invalidates stale playback opens after this operation has released its editor lock. */
    private val onSourceChanged: suspend () -> Unit = {},
    private val afterSave: suspend () -> Unit = {},
) : FileTagEditor {
    private val native = TagLib
    private val resolver = context.contentResolver
    private val mutex = Mutex()
    private val witnessOwner = Any()
    // Internal files (not evictable cache) retain a recovery copy if a commit is interrupted.
    private val directory = File(context.filesDir, "tag-editor")
    private val journal = File(directory, "pending.json")
    private val journalFile = AtomicFile(journal)
    private val exportReceiptFile = AtomicFile(File(directory, "export-verified.json"))

    /** Debug fault injection after a durable journal exists, before the normal sequential copy. */
    internal var onCommitPreparedForTest: ((File, ParcelFileDescriptor) -> Unit)? = null
    internal var onRollbackPreparedForTest: ((File, ParcelFileDescriptor) -> Unit)? = null

    private data class RecoveryCopy(val name: String, val bytes: Long, val sha256: String) {
        val fingerprint: TagRecoveryFingerprint get() = TagRecoveryFingerprint(bytes, sha256)
    }
    private data class DestinationIdentity(val device: Long, val inode: Long, val path: String, val mediaRow: String?)
    private data class RecoveryRecord(
        val installation: String,
        val uri: String,
        val identity: DestinationIdentity,
        val original: RecoveryCopy,
        val staged: RecoveryCopy,
        val rollback: RecoveryCopy? = null,
    )
    private data class CurrentImage(val bytes: Long, val sha256: String, val action: TagRecoveryAction)
    private data class RecoveryPackage(val id: String, val files: List<RecoveryCopy>, val bytes: Long)
    private data class ExportReceipt(val uri: String, val receipt: TagRecoveryExportReceipt)
    private class ReadWitness(
        val owner: Any,
        val track: Track,
        val identity: DestinationIdentity,
        val fingerprint: TagRecoveryFingerprint,
        val tags: EditableTags,
        override val sourceKey: String,
    ) : TagEditWitness

    private suspend fun <T> editOperation(block: suspend () -> T): T = try {
        withContext(Dispatchers.IO) { block() }
    } catch (changed: TagEditSourceChangedException) {
        // The block and mutex have unwound before notifying the application. Cancellation and
        // unrelated failures keep their original path; source rejection is reported exactly once.
        // Once identity has been disproved, closing the form must not skip invalidation.
        // Reads/writes stay cancellable; only this bounded notification is mandatory.
        withContext(NonCancellable) { onSourceChanged() }
        currentCoroutineContext().ensureActive()
        throw changed
    }

    override suspend fun read(track: Track): TagEditSnapshot = editOperation {
        mutex.withLock {
            if (!isCurrentSelection(track)) sourceChanged()
            if (readRecord()?.uri == track.uri) {
                recoverPending(track.uri.toUri())
                scan(track.uri.toUri())
            }
            val uri = track.uri.toUri()
            resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                val identity = selectedIdentity(track, descriptor)
                val before = Os.fstat(descriptor.fileDescriptor)
                val coroutine = currentCoroutineContext()
                val fingerprint = TagRecoveryFingerprint(before.st_size, descriptorDigest(descriptor, before.st_size) { coroutine.ensureActive() })
                Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
                // TagLib owns its duplicate; retain the original fd until the entire read is checked.
                val metadata = ParcelFileDescriptor.dup(descriptor.fileDescriptor).let {
                    TagLib.getMetadata(it.detachFd(), false)
                } ?: throw IOException("Unsupported tags")
                val tags = metadata.editable()
                requireUnchangedStat(before, Os.fstat(descriptor.fileDescriptor))
                requireSourceIdentity(track.uri, identity, descriptor)
                if (!isCurrentSelection(track)) sourceChanged()
                val key = sourceKey(track.uri, identity, fingerprint)
                TagEditSnapshot(tags, ReadWitness(witnessOwner, track, identity, fingerprint, tags, key))
            } ?: throw IOException("Cannot open audio")
        }
    }

    override suspend fun save(request: TagEditRequest) = editOperation {
        require(request.edited.isValidComparedTo(request.original)) { "Invalid tags" }
        mutex.withLock {
            if (!isCurrentSelection(request.track)) sourceChanged()
            val witness = request.snapshot.witness as? ReadWitness ?: sourceChanged()
            if (witness.owner !== witnessOwner || witness.track.id != request.track.id ||
                witness.track.uri != request.track.uri || witness.tags != request.original) sourceChanged()
            // Bind Save to what this form read, before requesting write consent or making copies.
            resolver.openFileDescriptor(request.track.uri.toUri(), "r")?.use {
                requireReadWitness(witness, it)
            } ?: sourceChanged()
            if (!isCurrentSelection(request.track)) sourceChanged()
            directory.mkdirs()
            recoverPending(request.track.uri.toUri())
            val changes = request.edited.changesFrom(request.original)
            if (changes.isEmpty() && request.coverUri == null) return@withLock
            val uri = request.track.uri.toUri()
            // Opening rw requests consent before any expensive staging, without truncation.
            resolver.openFileDescriptor(uri, "rw")?.use { destination ->
                requireReadWitness(witness, destination)
                val identity = witness.identity
                val suffix = request.track.fileName?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }
                    ?.let { ".$it" } ?: ".audio"
                val original = File.createTempFile("original-", suffix, directory)
                val staged = File.createTempFile("staged-", suffix, directory)
                var retainRecovery = false
                try {
                    withDescriptorInput(destination) { input ->
                        FileOutputStream(original).use { output ->
                            copyExactly(input, output, Os.fstat(destination.fileDescriptor).st_size)
                            output.fd.sync()
                        }
                    }
                    val originalCopy = recoveryCopy(original)
                    if (originalCopy.fingerprint != witness.fingerprint) sourceChanged()
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
                    ParcelFileDescriptor.open(staged, ParcelFileDescriptor.MODE_READ_WRITE).use { Os.fsync(it.fileDescriptor) }
                    currentCoroutineContext().ensureActive()
                    val record = RecoveryRecord(installationToken(create = true), uri.toString(), identity,
                        originalCopy, recoveryCopy(staged))
                    // Once copying starts, navigating away cannot cancel a partially written file.
                    withContext(NonCancellable) {
                        withFileWrite(request.track.uri) {
                            withContext(Dispatchers.IO) {
                                if (!isCurrentSelection(request.track)) sourceChanged()
                                requireReadWitness(witness, destination)
                                if (!isCurrentSelection(request.track)) sourceChanged()
                                retainRecovery = true
                                writeRecord(record)
                                onCommitPreparedForTest?.invoke(staged, destination)
                                try {
                                    copyInto(staged, destination)
                                    requireDestination(record, destination)
                                    check(descriptorDigest(destination, record.staged.bytes) == record.staged.sha256) { "Incomplete save" }
                                } catch (failure: Exception) {
                                    try {
                                        recoverRecord(record, destination, forceRollback = true)
                                        retainRecovery = false
                                    } catch (restore: Exception) { failure.addSuppressed(restore) }
                                    throw failure
                                }
                                clearJournal()
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
                    if (!retainRecovery) { staged.delete(); original.delete() }
                }
            } ?: throw IOException("Cannot open audio for editing")
        }
    }

    suspend fun pendingRecoveryUri(): Uri? = withContext(Dispatchers.IO) {
        mutex.withLock { readRecord()?.also(::requireInstallation)?.uri?.toUri() }
    }

    suspend fun recoverInterruptedSave() = withContext(Dispatchers.IO) {
        mutex.withLock {
            readRecord()?.let { record ->
                val uri = record.uri.toUri()
                withContext(NonCancellable) { recoverPending(uri); kotlinx.coroutines.withTimeoutOrNull(15_000) { scan(uri) } }
            }
        }
    }

    override suspend fun recoveryInfo(): TagRecoveryInfo? = withContext(Dispatchers.IO) {
        mutex.withLock {
            recoveryPackage()?.let { pending ->
                TagRecoveryInfo(pending.id, pending.files.count { it.name != journal.name }, pending.bytes,
                    readExportReceipt()?.receipt?.recoveryId == pending.id)
            }
        }
    }

    /** Exports copies, including legacy/malformed journals, without opening the music destination. */
    override suspend fun exportRecovery(recoveryId: String, destinationUri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending = recoveryPackage()?.takeIf { it.id == recoveryId } ?: throw IOException("Recovery changed; reopen its details")
            val uri = destinationUri.toUri()
            if (uri.scheme != "content" || !DocumentsContract.isDocumentUri(context, uri)) throw IOException("Choose a new document")
            val coroutine = currentCoroutineContext()
            val hash = MessageDigest.getInstance("SHA-256")
            val written = resolver.openFileDescriptor(uri, "rw")?.use { descriptor ->
                val stat = Os.fstat(descriptor.fileDescriptor)
                // CreateDocument should create an empty document. Never truncate an existing file,
                // including an alternate DocumentsProvider URI for the original audio.
                if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_size != 0L) throw IOException("Choose a new empty local document")
                val counted = CountingOutputStream(ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)))
                ZipOutputStream(DigestOutputStream(counted, hash)).use { zip ->
                    for (copy in pending.files) {
                        coroutine.ensureActive()
                        zip.putNextEntry(ZipEntry(copy.name))
                        val copyHash = MessageDigest.getInstance("SHA-256")
                        DigestInputStream(copyFile(copy).inputStream(), copyHash).use { input ->
                            copyExactly(input, zip, copy.bytes) { coroutine.ensureActive() }
                        }
                        if (copyHash.digest().hex() != copy.sha256) throw IOException("Recovery copy changed during export")
                        zip.closeEntry()
                    }
                    zip.putNextEntry(ZipEntry("RECOVERY.txt"))
                    zip.write(RECOVERY_README.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                }
                Os.fsync(descriptor.fileDescriptor)
                TagRecoveryFingerprint(counted.count, hash.digest().hex())
            } ?: throw IOException("Cannot create recovery document")
            coroutine.ensureActive()
            val readBack = documentFingerprint(uri, written.bytes)
            val receipt = TagRecoveryExportPolicy.verify(pending.id, written, readBack)
            if (recoveryPackage()?.id != pending.id) throw IOException("Recovery changed during export")
            coroutine.ensureActive()
            // Only a verified document gets a receipt. No journal/copy is removed by export.
            writeExportReceipt(ExportReceipt(destinationUri, receipt))
        }
    }

    override suspend fun archiveRecovery(recoveryId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val pending = recoveryPackage()?.takeIf { it.id == recoveryId } ?: throw IOException("Recovery changed; reopen its details")
            val exported = readExportReceipt() ?: throw IOException("Save and verify a recovery archive first")
            val readBack = documentFingerprint(exported.uri.toUri(), exported.receipt.document.bytes)
            if (!TagRecoveryExportPolicy.canArchive(pending.id, exported.receipt, readBack)) throw IOException("The recovery archive is unavailable or changed")
            if (recoveryPackage()?.id != pending.id) throw IOException("Recovery changed before archiving")
            currentCoroutineContext().ensureActive()
            // A single same-filesystem rename preserves the complete journal and every copy. The
            // no-backup location also prevents archived audio from entering an OS transfer.
            withContext(NonCancellable) {
                val archives = File(context.noBackupFilesDir, "tag-recovery-archives")
                if (!archives.isDirectory && !archives.mkdirs()) throw IOException("Cannot create private recovery archive")
                val target = File(archives, UUID.randomUUID().toString())
                if (target.exists()) throw IOException("Recovery archive already exists")
                Os.rename(directory.absolutePath, target.absolutePath)
            }
        }
    }

    private suspend fun recoveryPackage(): RecoveryPackage? {
        if (!journal.exists() && !File("${journal.path}.bak").exists()) return null
        // AtomicFile restores a durable .bak if needed, but journal parsing is deliberately optional
        // for export: even a malformed legacy record must not trap the user's private copies.
        journalFile.openRead().use { }
        val coroutine = currentCoroutineContext()
        val files = directory.listFiles()?.filter { it.name == journal.name || RECOVERY_COPY_NAME.matches(it.name) }
            ?.sortedBy(File::getName) ?: throw IOException("Cannot list retained recovery copies")
        val copies = files.map { file ->
            coroutine.ensureActive()
            if (!file.isFile || file.canonicalFile != File(directory.canonicalFile, file.name)) throw IOException("Invalid recovery copy")
            val bytes = file.length()
            val hash = file.inputStream().use { digestExactly(it, bytes) { coroutine.ensureActive() } }
            RecoveryCopy(file.name, bytes, hash)
        }
        if (copies.none { it.name == journal.name }) throw IOException("Recovery journal changed")
        var bytes = 0L
        for (copy in copies) {
            if (copy.bytes > Long.MAX_VALUE - bytes) throw IOException("Recovery archive is too large")
            bytes += copy.bytes
        }
        val description = copies.joinToString("") { "${it.name.length}:${it.name}:${it.bytes}:${it.sha256};" }
        val id = MessageDigest.getInstance("SHA-256").digest(description.toByteArray(Charsets.UTF_8)).hex()
        return RecoveryPackage(id, copies, bytes)
    }

    private suspend fun documentFingerprint(uri: Uri, bytes: Long): TagRecoveryFingerprint {
        val coroutine = currentCoroutineContext()
        val hash = resolver.openInputStream(uri)?.use { digestExactly(it, bytes) { coroutine.ensureActive() } }
            ?: throw IOException("Cannot verify the saved recovery archive")
        return TagRecoveryFingerprint(bytes, hash)
    }

    private fun readExportReceipt(): ExportReceipt? = try {
        val bytes = exportReceiptFile.openRead().use { it.readNBytes(MAX_JOURNAL_BYTES + 1) }
        if (bytes.size > MAX_JOURNAL_BYTES) null else JSONObject(bytes.toString(Charsets.UTF_8)).let {
            val fingerprint = TagRecoveryFingerprint(it.strictLong("bytes"), it.strictString("sha256"))
            val recoveryId = it.strictString("recoveryId")
            if (fingerprint.bytes <= 0 || !SHA256.matches(recoveryId)) null
            else ExportReceipt(it.strictString("uri"), TagRecoveryExportReceipt(recoveryId, fingerprint))
        }
    } catch (_: Exception) { null }

    private fun writeExportReceipt(exported: ExportReceipt) {
        val data = JSONObject().put("uri", exported.uri).put("recoveryId", exported.receipt.recoveryId)
            .put("bytes", exported.receipt.document.bytes).put("sha256", exported.receipt.document.sha256)
            .toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_JOURNAL_BYTES) throw IOException("Recovery export receipt is too large")
        val out = exportReceiptFile.startWrite()
        try { out.write(data); out.fd.sync(); exportReceiptFile.finishWrite(out) }
        catch (error: Exception) { exportReceiptFile.failWrite(out); throw error }
        if (readExportReceipt() != exported) throw IOException("Recovery export receipt was not saved")
    }

    private class CountingOutputStream(output: OutputStream) : FilterOutputStream(output) {
        var count = 0L
            private set
        override fun write(value: Int) { out.write(value); count++ }
        override fun write(bytes: ByteArray, offset: Int, length: Int) { out.write(bytes, offset, length); count += length }
    }

    /** Retried before the next save after a process interruption; never discard an un-restored original. */
    private suspend fun recoverPending(requestedUri: Uri) {
        val record = readRecord() ?: return
        requireInstallation(record)
        if (record.uri != requestedUri.toString()) blocked(Reason.PendingOtherFile)
        verifyCopies(record)
        // Check identity/content read-only before requesting consent for a write.
        resolver.openFileDescriptor(requestedUri, "r")?.use { destination ->
            val image = currentImage(record, destination)
            if (image.action != TagRecoveryAction.RestoreOriginal) {
                finishRecovery(record)
                return
            }
        } ?: blocked(Reason.DestinationChanged)
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            // Recovery has only a verified URI, not a library row/application ID. Use the same
            // reader exclusion as Save before reopening the destination for the rollback.
            withFileWrite(record.uri) {
                withContext(Dispatchers.IO) {
                    resolver.openFileDescriptor(requestedUri, "rw")?.use { recoverRecord(record, it) }
                        ?: blocked(Reason.DestinationChanged)
                }
            }
        }
    }

    private fun recoverRecord(record: RecoveryRecord, destination: ParcelFileDescriptor, forceRollback: Boolean = false) {
        requireInstallation(record)
        verifyCopies(record)
        val image = currentImage(record, destination)
        if (image.action == TagRecoveryAction.AlreadyOriginal || (image.action == TagRecoveryAction.AlreadyCommitted && !forceRollback)) {
            finishRecovery(record)
            return
        }
        // Persist the exact partial image before rollback. A second interruption is then another
        // provable sequential copy (original prefix + this snapshot's suffix), not guessed damage.
        val rollbackRecord = if (record.rollback == null) prepareRollback(record, image, destination) else record
        requireDestination(rollbackRecord, destination)
        if (descriptorDigest(destination, image.bytes) != image.sha256) blocked(Reason.UnexpectedContent)
        onRollbackPreparedForTest?.invoke(copyFile(record.original), destination)
        copyInto(copyFile(record.original), destination)
        requireDestination(rollbackRecord, destination)
        if (descriptorDigest(destination, record.original.bytes) != record.original.sha256) {
            throw IOException("Tag recovery did not finish; the original and journal were retained")
        }
        finishRecovery(rollbackRecord)
    }

    private fun prepareRollback(record: RecoveryRecord, image: CurrentImage, destination: ParcelFileDescriptor): RecoveryRecord {
        val snapshot = File.createTempFile("rollback-", ".audio", directory)
        var retained = false
        try {
            withDescriptorInput(destination) { input ->
                FileOutputStream(snapshot).use { output -> copyExactly(input, output, image.bytes); output.fd.sync() }
            }
            val copy = recoveryCopy(snapshot)
            if (copy.bytes != image.bytes || copy.sha256 != image.sha256) blocked(Reason.UnexpectedContent)
            val updated = record.copy(rollback = copy)
            retained = true
            writeRecord(updated)
            return updated
        } finally { if (!retained) snapshot.delete() }
    }

    private fun currentImage(record: RecoveryRecord, destination: ParcelFileDescriptor): CurrentImage {
        requireDestination(record, destination)
        val before = Os.fstat(destination.fileDescriptor)
        val bytes = before.st_size
        // A sequential commit/rollback cannot grow beyond either complete private image. Reject
        // unrelated growth before reading a potentially huge or sparse destination.
        if (bytes <= 0 || bytes > maxOf(record.original.bytes, record.staged.bytes, record.rollback?.bytes ?: 0L)) {
            blocked(Reason.UnexpectedContent)
        }
        val hash = descriptorDigest(destination, bytes)
        val action = TagRecoveryPolicy.decide(record.original.fingerprint, record.staged.fingerprint,
            record.rollback?.fingerprint, TagRecoveryFingerprint(bytes, hash)) { _, _ ->
            val baseline = record.rollback ?: record.original
            val replacement = if (record.rollback == null) record.staged else record.original
            isCopyPrefix(baseline, replacement, destination, bytes)
        }
        val after = Os.fstat(destination.fileDescriptor)
        if (before.st_size != after.st_size || before.st_mtim.tv_sec != after.st_mtim.tv_sec ||
            before.st_mtim.tv_nsec != after.st_mtim.tv_nsec || before.st_ctim.tv_sec != after.st_ctim.tv_sec ||
            before.st_ctim.tv_nsec != after.st_ctim.tv_nsec
        ) blocked(Reason.DestinationChanged)
        requireDestination(record, destination)
        return CurrentImage(bytes, hash, action)
    }

    private fun isCopyPrefix(baseline: RecoveryCopy, replacement: RecoveryCopy, destination: ParcelFileDescriptor, bytes: Long): Boolean {
        val proof = TagWritePrefixProof(baseline.bytes, replacement.bytes, bytes)
        copyFile(baseline).inputStream().use { old -> copyFile(replacement).inputStream().use { new ->
            withDescriptorInput(destination) { current ->
                val a = ByteArray(IO_BUFFER_BYTES)
                val b = ByteArray(IO_BUFFER_BYTES)
                val c = ByteArray(IO_BUFFER_BYTES)
                var offset = 0L
                while (offset < bytes) {
                    val count = minOf(IO_BUFFER_BYTES.toLong(), bytes - offset).toInt()
                    val oldCount = minOf(count.toLong(), (baseline.bytes - offset).coerceAtLeast(0)).toInt()
                    val newCount = minOf(count.toLong(), (replacement.bytes - offset).coerceAtLeast(0)).toInt()
                    readExactly(old, a, oldCount); readExactly(new, b, newCount); readExactly(current, c, count)
                    if (!proof.accept(a, oldCount, b, newCount, c, count)) return false
                    offset += count
                }
                if (current.read() != -1) return false
            }
        } }
        return proof.complete
    }

    private fun selectedIdentity(track: Track, descriptor: ParcelFileDescriptor): DestinationIdentity {
        val uri = track.uri.toUri()
        val identity = try { destinationIdentity(uri, descriptor) }
        catch (_: TagRecoveryRequiredException) { sourceChanged() }
        val matches = when (uri.scheme) {
            "content" -> {
                val columns = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.DATE_ADDED, MediaStore.Audio.Media.DURATION)
                resolver.query(uri, columns, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst()) sourceChanged()
                    val added = if (cursor.isNull(2)) null else cursor.getLong(2).takeIf { it in 1..Long.MAX_VALUE / 1_000 }?.times(1_000)
                    TagEditSelection.matches(track, uri.toString(),
                        if (cursor.isNull(0)) null else cursor.getString(0),
                        if (cursor.isNull(1)) null else cursor.getString(1), added,
                        if (cursor.isNull(3)) null else cursor.getLong(3))
                } ?: false
            }
            "file" -> {
                val file = File(identity.path)
                val expectedFolder = track.folder?.trim('/')
                val parent = file.parentFile?.canonicalPath?.trim('/')
                val folder = if (expectedFolder != null &&
                    (parent == expectedFolder || parent?.endsWith("/$expectedFolder") == true)) expectedFolder else parent
                val duration = if (track.durationMs > 0) sourceDurationMs(descriptor) else null
                TagEditSelection.matches(track, uri.toString(), file.name, folder, null, duration)
            }
            else -> false
        }
        if (!matches) sourceChanged()
        requireSourceIdentity(track.uri, identity, descriptor)
        return identity
    }

    private fun sourceDurationMs(descriptor: ParcelFileDescriptor): Long? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(descriptor.fileDescriptor)
            return (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true && format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 }?.div(1_000)
                } else null
            }.maxOrNull()
        } finally { extractor.release() }
    }

    private suspend fun requireReadWitness(witness: ReadWitness, destination: ParcelFileDescriptor) {
        if (selectedIdentity(witness.track, destination) != witness.identity) sourceChanged()
        val before = Os.fstat(destination.fileDescriptor)
        val coroutine = currentCoroutineContext()
        if (before.st_size != witness.fingerprint.bytes ||
            descriptorDigest(destination, witness.fingerprint.bytes) { coroutine.ensureActive() } != witness.fingerprint.sha256) sourceChanged()
        requireUnchangedStat(before, Os.fstat(destination.fileDescriptor))
        requireSourceIdentity(witness.track.uri, witness.identity, destination)
    }

    private fun requireUnchangedStat(before: android.system.StructStat, after: android.system.StructStat) {
        if (before.st_size != after.st_size || before.st_mtim.tv_sec != after.st_mtim.tv_sec ||
            before.st_mtim.tv_nsec != after.st_mtim.tv_nsec || before.st_ctim.tv_sec != after.st_ctim.tv_sec ||
            before.st_ctim.tv_nsec != after.st_ctim.tv_nsec) sourceChanged()
    }

    private fun requireSourceIdentity(uri: String, identity: DestinationIdentity, descriptor: ParcelFileDescriptor) {
        try { requireDestination(uri.toUri(), identity, descriptor) }
        catch (_: TagRecoveryRequiredException) { sourceChanged() }
    }

    private fun sourceKey(uri: String, identity: DestinationIdentity, fingerprint: TagRecoveryFingerprint): String {
        val description = listOf(uri, identity.device.toString(), identity.inode.toString(), identity.path,
            identity.mediaRow.orEmpty(), fingerprint.bytes.toString(), fingerprint.sha256)
            .joinToString("") { "${it.length}:$it" }
        return MessageDigest.getInstance("SHA-256").digest(description.toByteArray(Charsets.UTF_8)).hex()
    }

    private fun sourceChanged(): Nothing = throw TagEditSourceChangedException()

    private fun destinationIdentity(uri: Uri, descriptor: ParcelFileDescriptor): DestinationIdentity {
        val stat = Os.fstat(descriptor.fileDescriptor)
        if (!OsConstants.S_ISREG(stat.st_mode) || stat.st_ino <= 0 || stat.st_nlink <= 0 || stat.st_size < 0) blocked(Reason.DestinationChanged)
        val link = Os.readlink("/proc/self/fd/${descriptor.fd}")
        if (!link.startsWith('/') || link.endsWith(" (deleted)")) blocked(Reason.DestinationChanged)
        val path = File(link).canonicalPath
        val row = when (uri.scheme) {
            "file" -> {
                if (uri.path == null || File(requireNotNull(uri.path)).canonicalPath != path) blocked(Reason.DestinationChanged)
                null
            }
            "content" -> {
                if (uri.authority != MediaStore.AUTHORITY) blocked(Reason.DestinationChanged)
                val columns = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.VOLUME_NAME,
                    MediaStore.MediaColumns.GENERATION_ADDED, MediaStore.MediaColumns.DATE_ADDED,
                    MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.DISPLAY_NAME)
                resolver.query(uri, columns, null, null, null)?.use { cursor ->
                    if (!cursor.moveToFirst() || (0..5).any(cursor::isNull)) blocked(Reason.DestinationChanged)
                    val id = cursor.getLong(0)
                    val volume = cursor.getString(1)
                    val generation = cursor.getLong(2)
                    val added = cursor.getLong(3)
                    val relativePath = cursor.getString(4)
                    val name = cursor.getString(5)
                    if (id <= 0 || generation <= 0 || added <= 0 || volume.isBlank() || name.isBlank()) blocked(Reason.DestinationChanged)
                    val version = MediaStore.getVersion(context, volume).takeIf(String::isNotBlank) ?: blocked(Reason.DestinationChanged)
                    listOf(id.toString(), volume, version, generation.toString(), added.toString(), relativePath, name)
                        .joinToString("") { "${it.length}:$it" }
                } ?: blocked(Reason.DestinationChanged)
            }
            else -> blocked(Reason.DestinationChanged)
        }
        return DestinationIdentity(stat.st_dev, stat.st_ino, path, row)
    }

    private fun requireDestination(record: RecoveryRecord, destination: ParcelFileDescriptor) {
        requireDestination(record.uri.toUri(), record.identity, destination)
    }

    private fun requireDestination(uri: Uri, identity: DestinationIdentity, destination: ParcelFileDescriptor) {
        if (destinationIdentity(uri, destination) != identity) blocked(Reason.DestinationChanged)
        // The held fd and the URI must still refer to the same inode; a reassigned row is not enough.
        resolver.openFileDescriptor(uri, "r")?.use {
            val current = Os.fstat(it.fileDescriptor)
            if (current.st_dev != identity.device || current.st_ino != identity.inode || current.st_nlink <= 0) {
                blocked(Reason.DestinationChanged)
            }
        } ?: blocked(Reason.DestinationChanged)
    }

    private fun requireInstallation(record: RecoveryRecord) {
        TagRecoveryPolicy.requireInstallation(record.installation, installationToken(create = false))
    }

    private fun installationToken(create: Boolean): String = synchronized(INSTALLATION_LOCK) {
        val file = File(context.noBackupFilesDir, "tag-editor-installation-id")
        val atomic = AtomicFile(file)
        if (!file.exists() && !File("${file.path}.bak").exists()) {
            if (!create) blocked(Reason.DifferentInstallation)
            val token = UUID.randomUUID().toString()
            val out = atomic.startWrite()
            try { out.write(token.toByteArray(Charsets.UTF_8)); out.fd.sync(); atomic.finishWrite(out) }
            catch (error: Exception) { atomic.failWrite(out); throw error }
        }
        val value = atomic.openRead().use { it.readNBytes(128).toString(Charsets.UTF_8) }
        if (!TagRecoveryPolicy.validInstallationId(value)) blocked(Reason.DifferentInstallation)
        value
    }

    private fun readRecord(): RecoveryRecord? {
        if (!journal.exists() && !File("${journal.path}.bak").exists()) return null
        try {
            val data = journalFile.openRead().use { it.readNBytes(MAX_JOURNAL_BYTES + 1) }
            if (data.size > MAX_JOURNAL_BYTES) blocked(Reason.InvalidJournal)
            val value = JSONObject(data.toString(Charsets.UTF_8))
            TagRecoveryPolicy.requireVersion(if (value.has("version")) value.strictLong("version") else null)
            val installation = value.strictString("installation").also { if (!TagRecoveryPolicy.validInstallationId(it)) blocked(Reason.InvalidJournal) }
            val uri = value.strictString("uri")
            val identity = value.getJSONObject("identity").let {
                DestinationIdentity(it.strictLong("device"), it.strictLong("inode").also { inode -> if (inode <= 0) blocked(Reason.InvalidJournal) },
                    it.strictString("path"), if (it.isNull("mediaRow")) null else it.strictString("mediaRow"))
            }
            fun copy(key: String, prefix: String): RecoveryCopy = value.getJSONObject(key).let {
                val name = it.strictString("name")
                if (!name.startsWith(prefix) || File(name).name != name || name.length > 128) blocked(Reason.InvalidJournal)
                val bytes = it.strictLong("bytes").also { size -> if (size <= 0) blocked(Reason.InvalidJournal) }
                val sha = it.strictString("sha256").also { hash -> if (!SHA256.matches(hash)) blocked(Reason.InvalidJournal) }
                RecoveryCopy(name, bytes, sha)
            }
            return RecoveryRecord(installation, uri, identity, copy("original", "original-"), copy("staged", "staged-"),
                if (value.isNull("rollback")) null else copy("rollback", "rollback-"))
        } catch (blocked: TagRecoveryRequiredException) { throw blocked }
        catch (_: Exception) { blocked(Reason.InvalidJournal) }
    }

    private fun JSONObject.strictString(key: String): String = (opt(key) as? String)?.takeIf { it.isNotBlank() && it.length <= MAX_JOURNAL_BYTES }
        ?: blocked(Reason.InvalidJournal)

    private fun JSONObject.strictLong(key: String): Long = when (val value = opt(key)) {
        is Long -> value
        is Int -> value.toLong()
        else -> blocked(Reason.InvalidJournal)
    }

    private fun writeRecord(record: RecoveryRecord) {
        fun RecoveryCopy.json() = JSONObject().put("name", name).put("bytes", bytes).put("sha256", sha256)
        val identity = record.identity
        val data = JSONObject().put("version", 2).put("installation", record.installation).put("uri", record.uri)
            .put("identity", JSONObject().put("device", identity.device).put("inode", identity.inode).put("path", identity.path)
                .put("mediaRow", identity.mediaRow ?: JSONObject.NULL))
            .put("original", record.original.json()).put("staged", record.staged.json())
            .put("rollback", record.rollback?.json() ?: JSONObject.NULL).toString().toByteArray(Charsets.UTF_8)
        if (data.size > MAX_JOURNAL_BYTES) throw IOException("Tag recovery journal is too large")
        val out = journalFile.startWrite()
        try { out.write(data); out.fd.sync(); journalFile.finishWrite(out) }
        catch (error: Exception) { journalFile.failWrite(out); throw error }
        // The journal rename and referenced copy names must precede mutation of the music file.
        val directoryFd = Os.open(directory.absolutePath, OsConstants.O_RDONLY, 0)
        try { Os.fsync(directoryFd) } finally { Os.close(directoryFd) }
        if (readRecord() != record) throw IOException("Tag recovery journal did not round trip")
    }

    private fun recoveryCopy(file: File) = RecoveryCopy(file.name, file.length(), fileDigest(file))

    private fun copyFile(copy: RecoveryCopy): File = File(directory, copy.name).also {
        if (!it.isFile || it.canonicalFile != File(directory.canonicalFile, copy.name)) blocked(Reason.MissingCopy)
    }

    private fun verifyCopies(record: RecoveryRecord) {
        for (copy in listOfNotNull(record.original, record.staged, record.rollback)) {
            val file = copyFile(copy)
            if (file.length() != copy.bytes || file.inputStream().use { digestExactly(it, copy.bytes) } != copy.sha256) blocked(Reason.DamagedCopy)
        }
    }

    private fun finishRecovery(record: RecoveryRecord) {
        clearJournal()
        listOfNotNull(record.original, record.staged, record.rollback).forEach { copyFile(it).delete() }
    }

    private fun clearJournal() {
        journalFile.delete()
        if (journal.exists() || File("${journal.path}.bak").exists()) throw IOException("Cannot clear completed tag recovery journal")
    }

    private fun blocked(reason: Reason): Nothing = throw TagRecoveryRequiredException(reason)

    private inline fun <T> withDescriptorInput(descriptor: ParcelFileDescriptor, block: (InputStream) -> T): T {
        Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
        return ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use(block)
    }

    private fun descriptorDigest(descriptor: ParcelFileDescriptor, bytes: Long, checkActive: () -> Unit = {}): String =
        withDescriptorInput(descriptor) { digestExactly(it, bytes, checkActive) }

    private fun digestExactly(input: InputStream, bytes: Long, checkActive: () -> Unit = {}): String {
        if (bytes < 0) throw IOException("Invalid audio file length")
        val hash = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var left = bytes
        while (left > 0) {
            checkActive()
            val count = minOf(left, buffer.size.toLong()).toInt()
            readExactly(input, buffer, count)
            hash.update(buffer, 0, count)
            left -= count
        }
        if (input.read() != -1) throw IOException("Audio file length changed")
        return hash.digest().hex()
    }

    private fun copyExactly(input: InputStream, output: OutputStream, bytes: Long, checkActive: () -> Unit = {}) {
        val buffer = ByteArray(IO_BUFFER_BYTES)
        var left = bytes
        while (left > 0) {
            checkActive()
            val count = minOf(left, buffer.size.toLong()).toInt()
            readExactly(input, buffer, count)
            output.write(buffer, 0, count)
            left -= count
        }
        if (input.read() != -1) throw IOException("Audio file length changed")
    }

    private fun readExactly(input: InputStream, buffer: ByteArray, count: Int) {
        var offset = 0
        while (offset < count) {
            val read = input.read(buffer, offset, count - offset)
            if (read < 0) throw IOException("Audio file ended unexpectedly")
            if (read == 0) {
                val byte = input.read()
                if (byte < 0) throw IOException("Audio file ended unexpectedly")
                buffer[offset++] = byte.toByte()
            } else offset += read
        }
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
        val INSTALLATION_LOCK = Any()
        val SHA256 = Regex("[0-9a-f]{64}")
        val RECOVERY_COPY_NAME = Regex("(?:original|staged|rollback)-[A-Za-z0-9_-]+\\.[A-Za-z0-9]{1,8}")
        const val RECOVERY_README = """Songlass tag-edit recovery archive

These are retained copies from an interrupted tag edit. A legacy or damaged journal may not
identify the current music file safely. The archive checksum verifies the exported bytes;
it does not guarantee that each retained audio copy is playable or the right destination.

Keep this archive until you have inspected the copies. Files beginning original- are the
retained pre-edit copies; staged- are edited copies; rollback- are partial recovery snapshots.
The journal contains local file identity details. Extract a copy to a separate location first.
Use your file manager to restore an original only after confirming the song and destination.
Songlass does not automatically import this archive or overwrite a music file from it.

Archivio Songlass di recupero delle modifiche ai tag

Queste copie sono state conservate dopo una modifica interrotta. Un registro precedente o
danneggiato potrebbe non identificare in sicurezza il file musicale attuale. Il controllo
dell'archivio verifica i byte esportati, non la riproducibilità di ogni copia o la destinazione.

Conserva questo archivio finché non hai controllato le copie. I file original- sono le copie
prima della modifica; staged- le copie modificate; rollback- le istantanee parziali del recupero.
Il registro contiene informazioni locali sull'identità del file. Estrai prima una copia in
una posizione separata. Ripristina un originale con il gestore file solo dopo aver verificato
il brano e la destinazione. Songlass non importa questo archivio e non sostituisce file da esso.
"""
        const val MAX_JOURNAL_BYTES = 16 * 1024
        const val IO_BUFFER_BYTES = 64 * 1024
        const val MAX_COVER_BYTES = 20 * 1024 * 1024
        const val MAX_PACKET_BYTES = 32 * 1024 * 1024
    }
}
