package dev.cmdrchara.songlass.ui

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.lyrics.LyricsReadGrants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@Composable
actual fun rememberLyricsFilePicker(
    requestKey: String,
    onResult: (String, LyricsFileSelection?) -> Boolean,
): LyricsFilePicker? = key(requestKey) {
    val resolver = LocalContext.current.applicationContext.contentResolver
    val currentResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) currentResult(requestKey, null)
        else if (uri.scheme != "content" || uri.toString().length > MAX_REFERENCE_CHARS) {
            // A failed selection is visible; only a cancelled picker returns null.
            currentResult(requestKey, LyricsFileSelection(""))
        } else {
            // Save the URI/token synchronously. Activity disposal while later permission IO is
            // suspended must not lose the picker result. Obsolete tokens are rejected by the host.
            currentResult(requestKey, LyricsFileSelection(uri.toString(), grantToken = requestKey))
        }
    }
    remember(launcher, resolver) {
        object : LyricsFilePicker {
            override fun open() {
                // .lrc has no consistently registered MIME type; the bounded parser checks the file.
                launcher.launch(arrayOf("*/*"))
            }

            override suspend fun retain(selection: LyricsFileSelection): LyricsFileSelection {
                val uri = selection.validUri() ?: return selection
                val token = selection.validToken() ?: return selection
                val owned = readGrants.retain(uri.toString(), token, selection.releaseReadGrant) {
                    withContext(Dispatchers.IO) {
                        try {
                            val alreadyHeld = resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
                            if (alreadyHeld) false else {
                                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                true
                            }
                        } catch (_: Exception) { false } // The temporary picker grant may still work.
                    }
                }
                return selection.copy(releaseReadGrant = owned)
            }

            override suspend fun read(selection: LyricsFileSelection): String? = withContext(Dispatchers.IO) {
                val uri = selection.validUri() ?: return@withContext null
                try {
                    currentCoroutineContext().ensureActive()
                    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    if (name == null || name.length > 4_096 ||
                        name.substringAfterLast('.', "").lowercase() !in SUPPORTED_EXTENSIONS) return@withContext null
                    resolver.openInputStream(uri)?.use { stream ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = stream.read(buffer)
                            if (count < 0) break
                            if (count > MAX_FILE_BYTES - output.size()) return@withContext null
                            output.write(buffer, 0, count)
                        }
                        currentCoroutineContext().ensureActive()
                        val bytes = output.toByteArray()
                        val charset = when {
                            bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                            bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
                            else -> Charsets.UTF_8
                        }
                        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
                            .toString().removePrefix("\uFEFF")
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
            }

            override suspend fun release(selection: LyricsFileSelection) {
                val uri = selection.validUri() ?: return
                val token = selection.validToken() ?: return
                // The shared coordinator also knows an acquisition whose old Activity was
                // disposed before it could save the returned ownership flag.
                readGrants.release(uri.toString(), token, selection.releaseReadGrant) {
                    withContext(Dispatchers.IO) {
                        try { resolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                        catch (_: Exception) { /* Provider/framework may already have revoked it. */ }
                    }
                }
            }
        }
    }
}

private fun LyricsFileSelection.validUri(): Uri? = reference.takeIf { it.length in 1..MAX_REFERENCE_CHARS }
    ?.let(Uri::parse)?.takeIf { it.scheme == ContentResolver.SCHEME_CONTENT }

private fun LyricsFileSelection.validToken(): String? = grantToken?.takeIf { it.isNotBlank() && it.length <= 128 }

private val readGrants = LyricsReadGrants()
private val SUPPORTED_EXTENSIONS = setOf("lrc", "txt", "ttml", "xml")
private const val MAX_FILE_BYTES = 128 * 1024
private const val MAX_REFERENCE_CHARS = 16 * 1024
