package dev.cmdrchara.songlass.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.RingtoneManager
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.sound.DefaultSoundCopyBudget
import dev.cmdrchara.songlass.core.sound.DefaultSoundPolicy
import dev.cmdrchara.songlass.core.sound.DefaultSoundSourceFacts
import dev.cmdrchara.songlass.core.sound.DefaultSoundTarget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

@Composable
actual fun rememberDefaultSoundActions(
    isSourceCurrent: (Track) -> Boolean,
    onResult: (DefaultSoundResult) -> Unit,
): DefaultSoundActions? {
    val context = LocalContext.current
    val application = context.applicationContext
    val owner = remember(context) { context.lifecycleOwner() }
    val scope = rememberCoroutineScope()
    val currentSource by rememberUpdatedState(isSourceCurrent)
    val result by rememberUpdatedState(onResult)
    var permission by remember(application) { mutableStateOf(Settings.System.canWrite(application)) }
    var applying by remember { mutableStateOf(false) }
    val writer = remember(application) { AndroidDefaultSoundWriter(application) }
    DisposableEffect(owner, application) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permission = Settings.System.canWrite(application)
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return DefaultSoundActions(
        permissionGranted = permission,
        isApplying = applying,
        openSettings = {
            // This callback only opens consent. Resuming never replays an earlier Apply.
            try {
                context.startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                    Uri.parse("package:${application.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) { result(DefaultSoundResult.Failed) }
        },
        apply = { track, target ->
            if (!applying) {
                // Bind this operation to the confirmed request, not to a replacement dialog.
                val sourceCheck = currentSource
                val notify = result
                permission = Settings.System.canWrite(application)
                if (!permission) notify(DefaultSoundResult.PermissionRequired)
                else {
                    applying = true
                    scope.launch {
                        try {
                            val outcome = writer.apply(track, target) {
                                // The captured request guard reads live AppState/library state on Main.
                                withContext(Dispatchers.Main.immediate) { sourceCheck(track) }
                            }
                            permission = Settings.System.canWrite(application)
                            notify(outcome)
                        } finally { applying = false }
                    }
                }
            }
        },
    )
}

private fun Context.lifecycleOwner(): LifecycleOwner? {
    var candidate = this
    while (true) {
        if (candidate is LifecycleOwner) return candidate
        if (candidate !is ContextWrapper || candidate.baseContext === candidate) return null
        candidate = candidate.baseContext
    }
}

private class DefaultSoundSourceChanged : IOException("Selected audio changed")

/** In-process seam for native verification; production always uses the platform-backed default. */
interface AndroidDefaultSoundStore {
    fun canWrite(): Boolean
    fun setDefault(type: Int, uri: Uri)
    fun getDefault(type: Int): Uri?
}

private class PlatformDefaultSoundStore(private val context: Context) : AndroidDefaultSoundStore {
    override fun canWrite(): Boolean = Settings.System.canWrite(context)
    override fun setDefault(type: Int, uri: Uri) = RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)
    override fun getDefault(type: Int): Uri? = RingtoneManager.getActualDefaultRingtoneUri(context, type)
}

/**
 * Owns only the newly inserted public copy. The selected source is always opened read-only.
 * Store injection is a compile-time, in-process seam; no Intent or external input selects it.
 */
class AndroidDefaultSoundWriter(
    private val context: Context,
    private val store: AndroidDefaultSoundStore = PlatformDefaultSoundStore(context),
) {
    private val resolver = context.contentResolver

    private data class OwnedCopy(val uri: Uri, val path: String, val name: String)
    private data class Witness(val facts: DefaultSoundSourceFacts, val stat: Stat, val mime: String)
    private data class Stat(val device: Long, val inode: Long, val size: Long,
        val modifiedSeconds: Long, val modifiedNanos: Long, val changedSeconds: Long, val changedNanos: Long)

    suspend fun apply(track: Track, target: DefaultSoundTarget, isCurrent: suspend () -> Boolean): DefaultSoundResult {
        if (!operation.tryLock()) return DefaultSoundResult.Failed
        try {
            return withContext(Dispatchers.IO) { applyLocked(track, target, isCurrent) }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: DefaultSoundSourceChanged) { return DefaultSoundResult.SourceChanged }
        catch (_: SecurityException) {
            return if (!store.canWrite()) DefaultSoundResult.PermissionRequired else DefaultSoundResult.Failed
        } catch (_: Exception) { return DefaultSoundResult.Failed }
        finally { operation.unlock() }
    }

    private suspend fun applyLocked(track: Track, target: DefaultSoundTarget, isCurrent: suspend () -> Boolean): DefaultSoundResult {
        requireRights()
        if (!DefaultSoundPolicy.isSupportedSource(track.uri) || !isCurrent()) changed()
        val sourceUri = Uri.parse(track.uri)
        var created: OwnedCopy? = null
        try {
            resolver.openFileDescriptor(sourceUri, "r")?.use { source ->
                val before = witness(track, source)
                val nativeDuration = audioDuration(source)
                if (!DefaultSoundPolicy.sameDuration(before.facts.durationMs, nativeDuration) ||
                    (track.durationMs > 0 && !DefaultSoundPolicy.sameDuration(track.durationMs, nativeDuration))) changed()
                validate(track, source, before)
                requireRights()
                if (!isCurrent()) changed()
                val directory = when (target) {
                    DefaultSoundTarget.Ringtone -> Environment.DIRECTORY_RINGTONES
                    DefaultSoundTarget.Alarm -> Environment.DIRECTORY_ALARMS
                }
                val relativePath = "$directory/Songlass/"
                val suffix = track.fileName?.substringAfterLast('.', "")
                    ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,8}")) }?.lowercase()
                val displayName = "songlass-${UUID.randomUUID()}${suffix?.let { ".$it" }.orEmpty()}"
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
                    put(MediaStore.Audio.Media.MIME_TYPE, before.mime)
                    put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
                    put(MediaStore.Audio.Media.TITLE, track.title.filterNot(Char::isISOControl).take(128))
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                    put(MediaStore.Audio.Media.IS_MUSIC, 0)
                    put(MediaStore.Audio.Media.IS_RINGTONE, if (target == DefaultSoundTarget.Ringtone) 1 else 0)
                    put(MediaStore.Audio.Media.IS_ALARM, if (target == DefaultSoundTarget.Alarm) 1 else 0)
                }
                val copyUri = resolver.insert(MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
                    ?: throw IOException("Cannot create sound copy")
                created = OwnedCopy(copyUri, relativePath, displayName)
                val hash = copy(source, copyUri, before.stat.size)
                requireCopy(copyUri, relativePath, displayName, before.stat.size, hash, pending = true)
                val coroutine = currentCoroutineContext()
                val sourceHash = try { digest(source, before.stat.size) { coroutine.ensureActive() } }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: IllegalArgumentException) { changed() }
                    catch (_: IllegalStateException) { changed() }
                if (!hash.contentEquals(sourceHash)) changed()
                if (!DefaultSoundPolicy.sameDuration(nativeDuration, audioDuration(source))) changed()
                validate(track, source, before)
                requireRights()
                if (!isCurrent()) changed()
                currentCoroutineContext().ensureActive()
                if (resolver.update(copyUri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null) != 1)
                    throw IOException("Cannot publish sound copy")
                requireCopy(copyUri, relativePath, displayName, before.stat.size, hash, pending = false)
                validate(track, source, before)
                requireRights()
                if (!isCurrent()) changed()
                currentCoroutineContext().ensureActive()
                val type = when (target) {
                    DefaultSoundTarget.Ringtone -> RingtoneManager.TYPE_RINGTONE
                    DefaultSoundTarget.Alarm -> RingtoneManager.TYPE_ALARM
                }
                // No suspension between the final authority check and the platform commit.
                requireRights()
                store.setDefault(type, copyUri)
                if (!sameSound(store.getDefault(type), copyUri))
                    throw IOException("System did not retain the selected sound")
                created = null // Published and verified: never cleaned up as a failed staging row.
                return DefaultSoundResult.Applied
            } ?: changed()
        } finally {
            val abandoned = created
            if (abandoned != null) withContext(NonCancellable + Dispatchers.IO) { cleanup(abandoned) }
        }
    }

    private fun requireRights() {
        if (!store.canWrite() || context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Sound permissions are no longer granted")
    }

    private fun witness(track: Track, source: ParcelFileDescriptor): Witness {
        val uri = Uri.parse(track.uri)
        if (!DefaultSoundPolicy.isSupportedSource(track.uri)) changed()
        val native = Os.fstat(source.fileDescriptor)
        if (!OsConstants.S_ISREG(native.st_mode) || native.st_ino <= 0 || native.st_nlink <= 0 || native.st_size <= 0) changed()
        val link = Os.readlink("/proc/self/fd/${source.fd}")
        if (!link.startsWith('/') || link.endsWith(" (deleted)")) changed()
        val path = File(link).canonicalPath
        val columns = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.VOLUME_NAME,
            MediaStore.Audio.Media.GENERATION_ADDED, MediaStore.Audio.Media.GENERATION_MODIFIED,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE)
        val observation = resolver.query(uri, columns, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst() || columns.indices.any(cursor::isNull)) changed()
            val row = cursor.getLong(0)
            val volume = cursor.getString(1)
            val addedGeneration = cursor.getLong(2)
            val modifiedGeneration = cursor.getLong(3)
            val added = cursor.getLong(4)
            val name = cursor.getString(5)
            val folder = cursor.getString(6)
            val duration = cursor.getLong(7)
            val bytes = cursor.getLong(8)
            val mime = cursor.getString(9)
            if (row <= 0 || volume.isBlank() || addedGeneration <= 0 || modifiedGeneration <= 0 ||
                added !in 1..Long.MAX_VALUE / 1_000 || bytes != native.st_size ||
                !mime.matches(Regex("audio/[a-zA-Z0-9][a-zA-Z0-9.+-]{0,80}")) || cursor.moveToNext()) changed()
            val version = MediaStore.getVersion(context, volume).takeIf(String::isNotBlank) ?: changed()
            val provider = listOf(row.toString(), volume, version, addedGeneration.toString(), modifiedGeneration.toString(), mime)
                .joinToString("") { "${it.length}:$it" }
            Witness(DefaultSoundSourceFacts(track.uri, name, folder, added * 1_000, duration, path, provider), native.snapshot(), mime)
        } ?: changed()
        if (!DefaultSoundPolicy.matches(track, observation.facts)) changed()
        return observation
    }

    private fun validate(track: Track, held: ParcelFileDescriptor, expected: Witness) {
        val heldWitness = witness(track, held)
        if (!DefaultSoundPolicy.unchanged(expected.facts, heldWitness.facts) ||
            heldWitness.stat != expected.stat || heldWitness.mime != expected.mime) changed()
        // Detect URI reassignment even while the old descriptor still points at a live file.
        resolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { reopened ->
            if (witness(track, reopened) != expected) changed()
        } ?: changed()
    }

    private fun audioDuration(source: ParcelFileDescriptor): Long {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(source.fileDescriptor)
            return (0 until extractor.trackCount).mapNotNull { index ->
                val format = extractor.getTrackFormat(index)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true && format.containsKey(MediaFormat.KEY_DURATION))
                    format.getLong(MediaFormat.KEY_DURATION).takeIf { it > 0 }?.div(1_000) else null
            }.maxOrNull() ?: changed()
        } finally { extractor.release() }
    }

    private suspend fun copy(source: ParcelFileDescriptor, destination: Uri, expectedBytes: Long): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val budget = DefaultSoundCopyBudget(expectedBytes)
        Os.lseek(source.fileDescriptor, 0, OsConstants.SEEK_SET)
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(source.fileDescriptor)).use { input ->
            resolver.openFileDescriptor(destination, "w")?.use { outputDescriptor ->
                ParcelFileDescriptor.AutoCloseOutputStream(ParcelFileDescriptor.dup(outputDescriptor.fileDescriptor)).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        try { budget.consume(count) } catch (_: IllegalArgumentException) { changed() }
                        output.write(buffer, 0, count)
                        hash.update(buffer, 0, count)
                    }
                    try { budget.requireComplete() } catch (_: IllegalStateException) { changed() }
                    output.flush()
                    Os.fsync(outputDescriptor.fileDescriptor)
                }
            } ?: throw IOException("Cannot write sound copy")
        }
        return hash.digest()
    }

    private suspend fun requireCopy(uri: Uri, relativePath: String, name: String, bytes: Long, hash: ByteArray, pending: Boolean) {
        val columns = arrayOf(MediaStore.Audio.Media.OWNER_PACKAGE_NAME, MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.IS_PENDING)
        val owned = resolver.query(uri, columns, null, null, null)?.use {
            it.moveToFirst() && it.getString(0) == context.packageName && it.getString(1) == relativePath &&
                it.getString(2) == name && it.getInt(3) == (if (pending) 1 else 0) && !it.moveToNext()
        } == true
        if (!owned) throw IOException("Sound copy identity changed")
        resolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val before = Os.fstat(descriptor.fileDescriptor)
            if (!OsConstants.S_ISREG(before.st_mode) || before.st_size != bytes || before.st_nlink <= 0)
                throw IOException("Sound copy length changed")
            val coroutine = currentCoroutineContext()
            if (!hash.contentEquals(digest(descriptor, bytes) { coroutine.ensureActive() }) ||
                before.snapshot() != Os.fstat(descriptor.fileDescriptor).snapshot()) throw IOException("Sound copy verification failed")
            // An intact copy must still expose decodable audio metadata through its own descriptor.
            audioDuration(descriptor)
        } ?: throw IOException("Cannot verify sound copy")
    }

    private fun digest(descriptor: ParcelFileDescriptor, bytes: Long, checkActive: () -> Unit): ByteArray {
        val hash = MessageDigest.getInstance("SHA-256")
        val budget = DefaultSoundCopyBudget(bytes)
        Os.lseek(descriptor.fileDescriptor, 0, OsConstants.SEEK_SET)
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                checkActive()
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                budget.consume(count)
                hash.update(buffer, 0, count)
            }
            budget.requireComplete()
        }
        return hash.digest()
    }

    private fun sameSound(actual: Uri?, copy: Uri): Boolean {
        if (actual == null) return false
        if (actual == copy) return true
        // Canonical/user-qualified provider URIs may differ while opening the very same inode.
        // An alias rather than an actual URI leaves the state unknown: never delete on that basis.
        if (RingtoneManager.isDefault(actual)) throw IOException("Default URI is an unresolved alias")
        val actualDescriptor = resolver.openFileDescriptor(actual, "r") ?: throw IOException("Cannot inspect actual default")
        actualDescriptor.use { actualFd ->
            val copyDescriptor = resolver.openFileDescriptor(copy, "r") ?: throw IOException("Cannot inspect sound copy")
            copyDescriptor.use { copyFd ->
                val a = Os.fstat(actualFd.fileDescriptor)
                val b = Os.fstat(copyFd.fileDescriptor)
                return a.st_dev == b.st_dev && a.st_ino == b.st_ino && a.st_nlink > 0 && b.st_nlink > 0
            }
        }
    }

    private fun cleanup(copy: OwnedCopy) {
        // A setter may change the setting and then throw. Unknown state must retain the copy.
        try {
            val isDefault = listOf(RingtoneManager.TYPE_RINGTONE, RingtoneManager.TYPE_ALARM, RingtoneManager.TYPE_NOTIFICATION)
                .any { sameSound(store.getDefault(it), copy.uri) }
            if (isDefault) return
            // Only our fresh UUID-named row is eligible; never clean an original or a reused row.
            resolver.delete(copy.uri,
                "${MediaStore.Audio.Media.OWNER_PACKAGE_NAME}=? AND ${MediaStore.Audio.Media.RELATIVE_PATH}=? AND ${MediaStore.Audio.Media.DISPLAY_NAME}=?",
                arrayOf(context.packageName, copy.path, copy.name))
        } catch (_: Exception) { /* Retain rather than delete a possibly active default. */ }
    }

    private fun StructStat.snapshot() = Stat(st_dev, st_ino, st_size, st_mtim.tv_sec, st_mtim.tv_nsec, st_ctim.tv_sec, st_ctim.tv_nsec)
    private fun changed(): Nothing = throw DefaultSoundSourceChanged()

    private companion object { val operation = Mutex() }
}
