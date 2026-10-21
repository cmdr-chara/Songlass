package dev.cmdrchara.songlass.ui

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.model.FileTagEditor
import dev.cmdrchara.songlass.core.model.TagEditRequest
import dev.cmdrchara.songlass.core.model.TagEditSourceChangedException
import dev.cmdrchara.songlass.core.tags.TagRecoveryRequiredException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
actual fun rememberTagCoverPicker(onSelected: (String) -> Unit): (() -> Unit)? {
    val result by rememberUpdatedState(onSelected)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { result(it.toString()) } }
    return { picker.launch(arrayOf("image/jpeg", "image/png")) }
}

@Composable
actual fun rememberTagSaver(editor: FileTagEditor, onResult: (TagSaveResult) -> Unit): ((TagEditRequest) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    var pending by remember { mutableStateOf<TagEditRequest?>(null) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { response ->
        val request = pending
        pending = null
        if (response.resultCode != Activity.RESULT_OK || request == null) result(TagSaveResult.PermissionDenied)
        else scope.launch {
            try { editor.save(request); result(TagSaveResult.Saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: TagEditSourceChangedException) { result(TagSaveResult.SourceChanged) }
            catch (_: TagRecoveryRequiredException) { result(TagSaveResult.RecoveryRequired) }
            catch (_: Exception) { result(TagSaveResult.Failed) }
        }
    }
    return { request ->
        if (pending == null) scope.launch {
            try { editor.save(request); result(TagSaveResult.Saved) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: TagEditSourceChangedException) { result(TagSaveResult.SourceChanged) }
            catch (_: TagRecoveryRequiredException) { result(TagSaveResult.RecoveryRequired) }
            catch (_: SecurityException) {
                val uri = Uri.parse(request.track.uri)
                try {
                    val sender = MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
                    pending = request
                    consent.launch(IntentSenderRequest.Builder(sender).build())
                } catch (_: Exception) { pending = null; result(TagSaveResult.Failed) }
            } catch (_: Exception) { result(TagSaveResult.Failed) }
        }
    }
}
