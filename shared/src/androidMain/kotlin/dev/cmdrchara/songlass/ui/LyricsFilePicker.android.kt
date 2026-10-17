package dev.cmdrchara.songlass.ui

import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun rememberLyricsFilePicker(onResult: (Track, String?) -> Unit): ((Track) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentResult by rememberUpdatedState(onResult)
    var requestedTrack by remember { mutableStateOf<Track?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val track = requestedTrack
        requestedTrack = null
        if (uri != null && track != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                try {
                    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }
                    val extension = name?.substringAfterLast('.', "")?.lowercase()
                    if (extension != "lrc" && extension != "txt") return@withContext null
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readNBytes(MAX_FILE_BYTES + 1)
                        if (bytes.size > MAX_FILE_BYTES) null else {
                            val charset = when {
                                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
                                else -> Charsets.UTF_8
                            }
                            charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes))
                                .toString().removePrefix("\uFEFF")
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    null
                }
            }
            currentResult(track, text)
        }
    }
    return { track ->
        requestedTrack = track
        // .lrc has no consistently registered MIME type; the bounded parser validates the chosen file.
        launcher.launch(arrayOf("*/*"))
    }
}

private const val MAX_FILE_BYTES = 128 * 1024
