package dev.cmdrchara.songlass.debug

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import android.util.Log
import com.kyant.taglib.TagLib
import dev.cmdrchara.songlass.core.model.TagEditRequest
import dev.cmdrchara.songlass.core.model.TagEditSnapshot
import dev.cmdrchara.songlass.core.model.TagEditSourceChangedException
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException
import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException.Reason
import dev.cmdrchara.songlass.library.TagLibFileTagEditor
import dev.cmdrchara.songlass.playback.Media3AudioPlayer
import dev.cmdrchara.songlass.core.player.PlayerStatus
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** Only touches generated fixtures in the app's private tag-editor-qa cache directory. */
object TagEditorProbe {
    suspend fun run(context: Context) {
        // Never replace a real pending recovery (or rerun over a failed probe's retained evidence).
        val recoveryDirectory = File(context.filesDir, "tag-editor")
        check(!File(recoveryDirectory, "pending.json").exists() && !File(recoveryDirectory, "pending.json.bak").exists()) {
            "Resolve the existing tag recovery before running this probe"
        }
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
                val snapshot = writer.read(track)
                val original = snapshot.tags
                val edited = original.copy(title = "Songlass QA – 夜", artist = "QA Artist", album = "QA Album", albumArtist = "QA Album Artist", trackNumber = "7/12", discNumber = "2/3", date = "2026-10-19")
                writer.save(TagEditRequest(track, snapshot, edited, Uri.fromFile(cover).toString()))
                val saved = writer.read(track)
                check(saved.tags == edited)
                val hash = sha(file)
                val badCover = File(root, "bad.png")
                try {
                    writer.save(TagEditRequest(track, saved, edited.copy(title = "Must not persist"), Uri.fromFile(badCover).toString()))
                    error("Invalid cover accepted")
                } catch (_: IllegalArgumentException) { check(sha(file) == hash) }
                try {
                    writer.save(TagEditRequest(track, saved, edited.copy(trackNumber = "invalid")))
                    error("Invalid number accepted")
                } catch (_: IllegalArgumentException) { check(sha(file) == hash) }
                val descriptors = descriptorsFor(file)
                repeat(32) { writer.read(track) }
                check(descriptorsFor(file) == descriptors) { "Descriptor leak" }
                val data = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).let { TagLib.getMetadata(it.detachFd())!! }
                Log.i("TagEditorProbe", "PASS $extension tags=${data.propertyMap.keys.sorted()} pictures=${data.pictures.size} bad-cover/invalid-tags preserved file; descriptors stable")
            } catch (error: Throwable) { Log.e("TagEditorProbe", "FAIL $extension", error) }
        }
        fileWriteGuardProbe(context, root)
        selectionWitnessProbe(context, root, recoveryDirectory)
        recoveryProbe(context, root, recoveryDirectory)
        Log.i("TagEditorProbe", "DONE")
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun fileWriteGuardProbe(context: Context, root: File) = withContext(Dispatchers.Main) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val audio = Media3AudioPlayer(ExoPlayer.Builder(context).build(), scope, context = context)
        val first = Track("guard-a", "A", Uri.fromFile(File(root, "fixture.wav")).toString(), durationMs = 60_000)
        val second = Track("guard-b", "B", Uri.fromFile(File(root, "fixture.flac")).toString(), durationMs = 60_000)
        try {
            audio.loadAt(first, false, 600)
            audio.setNext(first.copy(id = "different-application-id-for-same-uri"))
            audio.withFileWriteUri(first.uri) {
                check(audio.exoPlayer.mediaItemCount == 0) { "Edited URI still has a current/prepared MediaItem" }
                check(audio.state.value.status == PlayerStatus.Buffering && audio.state.value.positionMs == 600L)
                audio.play()
                check(audio.state.value.playWhenReady)
                audio.seekTo(900)
                audio.pause()
                check(!audio.state.value.playWhenReady && audio.state.value.positionMs == 900L)
            }
            check(!audio.state.value.playWhenReady && audio.state.value.positionMs == 900L) { "Guard lost pause/seek intent" }

            audio.withFileWriteUri(first.uri) { audio.stop() }
            check(audio.state.value.status == PlayerStatus.Idle && audio.exoPlayer.mediaItemCount == 0) { "Guard resurrected stopped audio" }

            audio.loadAt(first, false, 600)
            audio.withFileWriteUri(first.uri) { audio.loadAt(second, false, 300) }
            check(audio.exoPlayer.currentMediaItem?.mediaId == second.id && audio.state.value.positionMs == 300L) {
                "Guard replaced a newer explicit selection"
            }

            audio.loadAt(first, false, 600)
            audio.setNext(second)
            audio.withFileWriteUri(second.uri) {
                check(audio.exoPlayer.currentMediaItem?.mediaId == first.id && audio.exoPlayer.mediaItemCount == 1)
                audio.setNext(second)
                check(audio.exoPlayer.mediaItemCount == 1) { "Prefetch reopened the URI being edited" }
            }
            check(audio.exoPlayer.mediaItemCount == 2 && audio.exoPlayer.currentMediaItem?.mediaId == first.id)
            Log.i("TagEditorProbe", "PASS native URI write guard preserves pause/seek/stop/new selection and excludes matching prepared items")

            suspend fun rejectedWrite(uri: String, failure: Exception = TagEditSourceChangedException(), commands: suspend () -> Unit = {}) {
                try {
                    audio.withFileWriteUri(uri) { commands(); throw failure }
                    error("Rejected write returned successfully")
                } catch (caught: Exception) { check(caught === failure) { "Guard changed the write failure" } }
            }

            audio.loadAt(first, false, 600)
            audio.setNext(first.copy(id = "same-uri-after-rejection"), entryId = 41)
            rejectedWrite(first.uri) {
                audio.play()
                audio.seekTo(900)
                audio.pause()
            }
            check(audio.state.value.status == PlayerStatus.Idle && !audio.state.value.playWhenReady &&
                audio.exoPlayer.mediaItemCount == 0) { "Rejected witness reopened the edited URI" }
            audio.loadAt(second, false, 300)
            check(audio.exoPlayer.mediaItemCount == 1) { "Rejected URI survived as a prepared next item" }

            audio.loadAt(first, false, 600)
            audio.setNext(second, entryId = 42)
            rejectedWrite(second.uri, java.io.IOException("Synthetic unverified recovery"))
            check(audio.exoPlayer.currentMediaItem?.mediaId == first.id && audio.exoPlayer.mediaItemCount == 1 &&
                audio.state.value.positionMs == 600L && !audio.state.value.playWhenReady)
            // An ordinary reconciliation must not resurrect the rejected prefetch either.
            audio.setCrossfade(1_000)
            check(audio.exoPlayer.mediaItemCount == 1) { "Later reconciliation rearmed a failed recovery URI" }
            audio.setCrossfade(0)

            audio.loadAt(first, false, 600)
            audio.setNext(first, entryId = 43)
            rejectedWrite(first.uri) {
                audio.loadAt(second, false, 300)
                audio.seekTo(800)
                audio.pause()
            }
            check(audio.exoPlayer.currentMediaItem?.mediaId == second.id && audio.exoPlayer.mediaItemCount == 1 &&
                audio.state.value.positionMs == 800L && !audio.state.value.playWhenReady) {
                "Rejected write discarded a newer different-URI selection or its transport commands"
            }

            audio.loadAt(first, false, 600)
            rejectedWrite(first.uri) { audio.stop() }
            check(audio.state.value.status == PlayerStatus.Idle && audio.exoPlayer.mediaItemCount == 0)
            Log.i("TagEditorProbe", "PASS rejected URI writes never reopen/rearm it and preserve newer different-URI selection, seek, pause and stop")
        } finally { audio.release(); scope.cancel() }
    }

    /** The replacement is valid synthetic PCM with identical tags and one different audio byte. */
    private suspend fun selectionWitnessProbe(context: Context, root: File, directory: File) {
        val file = File(root, "selection-qa.wav")
        val displaced = File(root, "selection-displaced.wav")
        check(!file.exists() && !displaced.exists()) { "Inspect the retained selection probe files before retrying" }
        context.assets.open("tag-editor-qa/fixture.wav").use { input -> file.outputStream().use { input.copyTo(it) } }
        val track = Track("selection-qa", "QA", Uri.fromFile(file).toString(), fileName = file.name)
        var writes = 0
        var notifications = 0
        lateinit var writer: TagLibFileTagEditor
        writer = TagLibFileTagEditor(context,
            withFileWrite = { _, write -> writes++; write() },
            onSourceChanged = {
                notifications++
                // This must acquire the same editor mutex: notification cannot run inside it.
                check(kotlinx.coroutines.withTimeout(2_000) { writer.recoveryInfo() } == null)
            },
        )
        val selected = writer.read(track)
        check(writer.read(track).sourceKey == selected.sourceKey) { "Unchanged reads must retain draft provenance" }
        check(notifications == 0)
        val copiesBefore = directory.listFiles().orEmpty().filter(File::isFile).associate { it.name to sha(it) }
        suspend fun rejects(snapshot: TagEditSnapshot) {
            val before = sha(file)
            val beforeNotifications = notifications
            try {
                writer.save(TagEditRequest(track, snapshot, snapshot.tags.copy(title = "Must not modify a replacement")))
                error("Stale edit source accepted")
            } catch (_: TagEditSourceChangedException) { }
            check(notifications == beforeNotifications + 1) { "Source rejection must notify exactly once before returning" }
            check(sha(file) == before && writes == 0) { "Stale Save reached the write guard or changed B" }
            check(directory.listFiles().orEmpty().filter(File::isFile).associate { it.name to sha(it) } == copiesBefore) {
                "Stale Save created or changed recovery/staging files"
            }
        }
        Os.rename(file.absolutePath, displaced.absolutePath)
        displaced.copyTo(file)
        changeWaveSample(file)
        val replacement = writer.read(track)
        check(replacement.tags == selected.tags && replacement.sourceKey != selected.sourceKey)
        rejects(selected)
        Log.i("TagEditorProbe", "PASS same-tags audio replacement is rejected before staging/journal/write")

        val inode = Os.stat(file.absolutePath).st_ino
        changeWaveSample(file)
        check(Os.stat(file.absolutePath).st_ino == inode)
        val mutated = writer.read(track)
        check(mutated.tags == replacement.tags && mutated.sourceKey != replacement.sourceKey)
        rejects(replacement)
        Log.i("TagEditorProbe", "PASS same-inode audio mutation is rejected by the full read-time content witness")

        val beforeReadRejection = notifications
        try { writer.read(track.copy(fileName = "stale-selection.wav")); error("Conflicting selection accepted") }
        catch (_: TagEditSourceChangedException) { }
        check(notifications == beforeReadRejection + 1)

        val beforeCancellation = notifications
        val cancelled = TagLibFileTagEditor(context,
            isCurrentSelection = { throw kotlinx.coroutines.CancellationException("Synthetic edit cancellation") },
            onSourceChanged = { notifications++ },
        )
        try { cancelled.read(track); error("Read cancellation was swallowed") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        try { cancelled.save(TagEditRequest(track, mutated, mutated.tags.copy(title = "Must not save"))); error("Save cancellation was swallowed") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        check(notifications == beforeCancellation && writes == 0) { "Cancellation must not become source rejection or a write" }

        // A proven mismatch must invalidate stale playback even if its form closes before the
        // callback reaches Main. The caller still observes cancellation after that notification.
        val beforeCancelledRejection = sha(file)
        for (saving in listOf(false, true)) kotlinx.coroutines.coroutineScope {
            val rejected = kotlinx.coroutines.CompletableDeferred<Unit>()
            val finishNotification = kotlinx.coroutines.CompletableDeferred<Unit>()
            var deferredNotifications = 0
            var sawCancellation = false
            lateinit var guarded: TagLibFileTagEditor
            guarded = TagLibFileTagEditor(context, onSourceChanged = {
                rejected.complete(Unit)
                finishNotification.await()
                withContext(Dispatchers.Main) { deferredNotifications++ }
                check(kotlinx.coroutines.withTimeout(2_000) { guarded.recoveryInfo() } == null)
            })
            val current = guarded.read(track)
            val operation = launch(Dispatchers.Default) {
                try {
                    if (saving) guarded.save(TagEditRequest(track.copy(id = "obsolete-edit-route"), current,
                        current.tags.copy(title = "Must not save a cancelled stale selection")))
                    else guarded.read(track.copy(fileName = "obsolete-read-route.wav"))
                    error("Rejected operation returned successfully")
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                    sawCancellation = true
                    throw cancelled
                }
            }
            try {
                kotlinx.coroutines.withTimeout(5_000) { rejected.await() }
                operation.cancel()
            } finally { finishNotification.complete(Unit) }
            kotlinx.coroutines.withTimeout(5_000) { operation.join() }
            check(deferredNotifications == 1 && sawCancellation && operation.isCancelled) {
                "Proven source rejection must notify once before propagating caller cancellation"
            }
        }
        check(sha(file) == beforeCancelledRejection && writes == 0)
        check(directory.listFiles().orEmpty().filter(File::isFile).associate { it.name to sha(it) } == copiesBefore)
        Log.i("TagEditorProbe", "PASS proven read/save rejection survives cancellation before Main notification; caller stays cancelled and source/copies stay unchanged")
        check(file.delete() && displaced.delete())
        Log.i("TagEditorProbe", "PASS early read/save rejections notify once after unlocking; cancellation stays cancellation; unchanged source key is stable")
    }

    private fun changeWaveSample(file: File) = java.io.RandomAccessFile(file, "rw").use { audio ->
        val header = ByteArray(12)
        audio.readFully(header)
        check(String(header, 0, 4, Charsets.US_ASCII) == "RIFF" && String(header, 8, 4, Charsets.US_ASCII) == "WAVE")
        while (audio.filePointer + 8 <= audio.length()) {
            val name = ByteArray(4).also(audio::readFully)
            val bytes = Integer.reverseBytes(audio.readInt()).toLong() and 0xffff_ffffL
            val start = audio.filePointer
            check(bytes <= audio.length() - start)
            if (String(name, Charsets.US_ASCII) == "data") {
                check(bytes > 0)
                val sample = audio.readUnsignedByte()
                audio.seek(start)
                audio.write(sample xor 1)
                audio.fd.sync()
                return@use
            }
            audio.seek(start + bytes + (bytes and 1L))
        }
        error("Synthetic WAV has no audio data")
    }

    /** A real v2 journal and sequential writes, confined to generated private fixtures. */
    private suspend fun recoveryProbe(context: Context, root: File, directory: File) {
        directory.mkdirs()
        val file = File(root, "recovery-qa.flac")
        val journal = File(directory, "pending.json")
        val track = Track("tag-recovery-qa", "QA", Uri.fromFile(file).toString(), fileName = file.name)
        fun freshFixture() {
            check(!journal.exists() && !File("${journal.path}.bak").exists())
            context.assets.open("tag-editor-qa/fixture.flac").use { input -> file.outputStream().use { input.copyTo(it) } }
        }
        suspend fun interruptedCommit(fullCopy: Boolean = false): Pair<String, String> {
            freshFixture()
            val originalHash = sha(file)
            var stagedHash = ""
            val writer = TagLibFileTagEditor(context)
            val snapshot = writer.read(track)
            writer.onCommitPreparedForTest = { staged, destination ->
                stagedHash = sha(staged)
                val prefix = if (fullCopy) staged.length() else differingPrefix(file, staged, 3)
                writePrefix(staged, destination, prefix)
                if (fullCopy) { Os.ftruncate(destination.fileDescriptor, staged.length()); Os.fsync(destination.fileDescriptor) }
                else check(sha(file) != originalHash && sha(file) != stagedHash) { "Fixture must leave a partial sequential image" }
                throw SimulatedProcessDeath()
            }
            try {
                writer.save(TagEditRequest(track, snapshot, snapshot.tags.copy(title = "Interrupted transaction – preserve the original recording")))
                error("Fault injection did not interrupt the commit")
            } catch (_: SimulatedProcessDeath) { check(journal.isFile) }
            return originalHash to stagedHash
        }
        fun retainedCopies(): List<File> {
            val record = JSONObject(journal.readText())
            return listOfNotNull(record.getJSONObject("original"), record.getJSONObject("staged"), record.optJSONObject("rollback"))
                .map { File(directory, it.getString("name")) }
        }
        suspend fun expectBlocked(reason: Reason) {
            val before = sha(file)
            val record = journal.readText()
            try { TagLibFileTagEditor(context).recoverInterruptedSave(); error("Unsafe recovery accepted") }
            catch (blocked: TagRecoveryRequiredException) { check(blocked.reason == reason) { "Expected $reason, got ${blocked.reason}" } }
            check(sha(file) == before && journal.readText() == record) { "Rejected recovery changed the destination or journal" }
            check(TagLibFileTagEditor(context).recoveryInfo() != null) { "Retained recovery must remain discoverable" }
        }

        // The old journal has no safe identity proof: keep both the foreign bytes and original.
        freshFixture()
        val recovery = File.createTempFile("original-", ".flac", directory)
        file.copyTo(recovery, overwrite = true)
        val originalHash = sha(recovery)
        journal.writeText(JSONObject().put("uri", track.uri).put("original", recovery.name).toString())
        file.writeBytes(byteArrayOf(0, 1, 2))
        expectBlocked(Reason.LegacyJournal)
        check(sha(recovery) == originalHash)
        journal.writeText("{malformed")
        expectBlocked(Reason.InvalidJournal)
        check(sha(recovery) == originalHash)
        // Only these exact synthetic files are cleared after the preservation assertions passed.
        check(journal.delete() && recovery.delete())
        Log.i("TagEditorProbe", "PASS legacy/malformed journal refuses overwrite and retains discoverable originals")

        val (expectedOriginal, _) = interruptedCommit()
        val copies = retainedCopies()
        var recoveryWrites = 0
        TagLibFileTagEditor(context, withFileWrite = { uri, write ->
            check(uri == track.uri) { "Recovery must guard the real URI without constructing a track ID" }
            recoveryWrites++
            write()
        }).recoverInterruptedSave()
        check(recoveryWrites == 1) { "Recovery bypassed native reader exclusion" }
        check(sha(file) == expectedOriginal && !journal.exists() && copies.none(File::exists))
        Log.i("TagEditorProbe", "PASS actual journal + partial commit restores original with a recreated writer")

        val (beforeRepeated, _) = interruptedCommit()
        val partialHash = sha(file)
        val interruptedRollback = TagLibFileTagEditor(context).apply {
            onRollbackPreparedForTest = { original, destination ->
                writePrefix(original, destination, differingPrefix(file, original, 1))
                check(sha(file) != partialHash && sha(file) != beforeRepeated)
                throw SimulatedProcessDeath()
            }
        }
        try { interruptedRollback.recoverInterruptedSave(); error("Rollback fault injection did not run") }
        catch (_: SimulatedProcessDeath) { check(JSONObject(journal.readText()).has("rollback")) }
        val repeatedCopies = retainedCopies()
        TagLibFileTagEditor(context).recoverInterruptedSave()
        check(sha(file) == beforeRepeated && !journal.exists() && repeatedCopies.none(File::exists))
        Log.i("TagEditorProbe", "PASS second interruption during rollback preserves and then restores the original")

        val (_, expectedStaged) = interruptedCommit(fullCopy = true)
        TagLibFileTagEditor(context).recoverInterruptedSave()
        check(sha(file) == expectedStaged && !journal.exists())
        Log.i("TagEditorProbe", "PASS fully committed staged image is recognized without reverting it")

        val (beforeTokenMismatch, _) = interruptedCommit()
        val validJournal = journal.readText()
        journal.writeText(JSONObject(validJournal).put("installation", "11111111-2222-4333-8444-555555555555").toString())
        val protectedCopies = retainedCopies().associateWith(::sha)
        expectBlocked(Reason.DifferentInstallation)
        check(protectedCopies.all { (copy, hash) -> copy.isFile && sha(copy) == hash })
        journal.writeText(validJournal)
        TagLibFileTagEditor(context).recoverInterruptedSave()
        check(sha(file) == beforeTokenMismatch && !journal.exists())
        Log.i("TagEditorProbe", "PASS transferred journal refuses writes and retains all copies")

        val (beforeReplacement, _) = interruptedCommit()
        val displaced = File(root, "recovery-displaced.flac")
        val foreign = File(root, "recovery-foreign.flac")
        check(!displaced.exists() && !foreign.exists())
        Os.rename(file.absolutePath, displaced.absolutePath)
        foreign.writeBytes(byteArrayOf(71, 72, 73, 74))
        Os.rename(foreign.absolutePath, file.absolutePath)
        val protectedOriginals = retainedCopies().associateWith(::sha)
        expectBlocked(Reason.DestinationChanged)
        check(protectedOriginals.all { (copy, hash) -> copy.isFile && sha(copy) == hash })
        Os.rename(file.absolutePath, foreign.absolutePath)
        Os.rename(displaced.absolutePath, file.absolutePath)
        TagLibFileTagEditor(context).recoverInterruptedSave()
        check(sha(file) == beforeReplacement && !journal.exists())
        check(foreign.delete())
        Log.i("TagEditorProbe", "PASS reused destination path/inode is rejected without touching foreign bytes")
    }

    private class SimulatedProcessDeath : Error("Injected process interruption after durable journal")

    /** Position just after the nth differing replacement byte, before the final truncate. */
    private fun differingPrefix(original: File, replacement: File, ordinal: Int): Long {
        original.inputStream().buffered().use { old -> replacement.inputStream().buffered().use { new ->
            var offset = 0L
            var differences = 0
            while (true) {
                val value = new.read()
                if (value < 0) break
                offset++
                if (value != old.read() && ++differences == ordinal) return offset
            }
        } }
        error("Generated fixture has too few differing bytes for partial-copy injection")
    }

    private fun writePrefix(file: File, destination: ParcelFileDescriptor, bytes: Long) {
        Os.lseek(destination.fileDescriptor, 0, OsConstants.SEEK_SET)
        ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(destination.fileDescriptor)).use { output ->
            file.inputStream().use { input ->
                val buffer = ByteArray(8 * 1024)
                var left = bytes
                while (left > 0) {
                    val count = input.read(buffer, 0, minOf(left, buffer.size.toLong()).toInt())
                    check(count > 0)
                    output.write(buffer, 0, count)
                    left -= count
                }
            }
            output.flush()
            Os.fsync(destination.fileDescriptor)
        }
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
