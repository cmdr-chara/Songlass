package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.core.lyrics.LyricsImportPreview
import dev.cmdrchara.songlass.core.lyrics.LyricsImportRequest
import dev.cmdrchara.songlass.core.lyrics.LyricsImportResult
import dev.cmdrchara.songlass.core.lyrics.LyricsImportReview
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random
import org.jetbrains.compose.resources.stringResource

internal val LocalLyricsImportAction = staticCompositionLocalOf<((Track) -> Unit)?> { null }

/**
 * Unconditional root owner of the Activity-result registration and its canonical pending request.
 * Place inside the interface theme, outside the player/screen branches. Saved state contains only
 * bounded identity metadata and the selected document URI; words are reread and reviewed on restore.
 */
@Composable
fun LyricsImportHost(
    repository: LyricsRepository,
    onRefreshLibrary: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val repositoryRevision by repository.changes.collectAsState()
    var requestState by rememberSaveable { mutableStateOf<String?>(null) }
    var documentReference by rememberSaveable { mutableStateOf<String?>(null) }
    var releaseReadGrant by rememberSaveable { mutableStateOf(false) }
    var readGrantToken by rememberSaveable { mutableStateOf<String?>(null) }
    var grantCleanupPending by rememberSaveable { mutableStateOf(false) }
    var requestKey by rememberSaveable { mutableStateOf(newImportRequestKey()) }
    var launchRequested by rememberSaveable { mutableStateOf(false) }
    var closingRequested by rememberSaveable { mutableStateOf(false) }
    var waitingForPicker by rememberSaveable { mutableStateOf(false) }
    var completed by rememberSaveable { mutableStateOf(false) }
    var completedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    var rejectedTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val request = remember(requestState) { requestState?.let(LyricsImportRequest::restore) }
    var preview by remember(requestState, documentReference) { mutableStateOf<LyricsImportPreview?>(null) }
    var notice by remember(requestState, documentReference) { mutableStateOf<LyricsImportResult?>(null) }
    var loading by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }

    fun selectedDocument(): LyricsFileSelection? = documentReference?.let { LyricsFileSelection(it, releaseReadGrant, readGrantToken) }
    val picker = rememberLyricsFilePicker(requestKey) result@{ returnedKey, selection ->
        // Each registration has its own saved key. A result from a cancelled/superseded picker
        // cannot be interpreted using a newer canonical request, even before recomposition.
        if (returnedKey != requestKey || requestState == null || !waitingForPicker || closingRequested || completed || grantCleanupPending) return@result false
        waitingForPicker = false
        launchRequested = false
        if (selection == null) {
            requestState = null
            documentReference = null
            releaseReadGrant = false
            readGrantToken = null
            completed = false
        } else {
            documentReference = selection.reference
            releaseReadGrant = selection.releaseReadGrant
            readGrantToken = selection.grantToken
        }
        true
    }

    fun close() {
        if (confirming || closingRequested || grantCleanupPending) return
        requestKey = newImportRequestKey() // Unregister the old picker before accepting another.
        launchRequested = false
        closingRequested = true
    }

    fun chooseDocument() {
        if (launchRequested || closingRequested || grantCleanupPending) return
        notice = null
        preview = null
        waitingForPicker = true
        requestKey = newImportRequestKey()
        launchRequested = true
    }

    LaunchedEffect(requestKey, launchRequested, closingRequested, grantCleanupPending) {
        if (!launchRequested && !closingRequested && !grantCleanupPending) return@LaunchedEffect
        val token = requestKey
        // The saved URI/token and cleanup intent remain until release completes. A recreated
        // host must finish this same cleanup before Done, a new selection, or another import.
        selectedDocument()?.let { picker?.release(it) }
        currentCoroutineContext().ensureActive()
        if (requestKey != token) return@LaunchedEffect
        documentReference = null
        releaseReadGrant = false
        readGrantToken = null
        grantCleanupPending = false
        if (closingRequested) {
            requestState = null
            waitingForPicker = false
            completed = false
            rejectedTitle = null
            closingRequested = false
        } else if (launchRequested && requestState != null) {
            launchRequested = false
            try { picker?.open() ?: error("Document picker unavailable") }
            catch (_: Exception) { waitingForPicker = false; notice = LyricsImportResult.Failed }
        }
    }

    LaunchedEffect(requestState, documentReference, readGrantToken, repositoryRevision, retry, completed, launchRequested, closingRequested, grantCleanupPending) {
        preview = null
        loading = false
        if (completed || confirming || launchRequested || closingRequested || grantCleanupPending || requestState == null || waitingForPicker) return@LaunchedEffect
        if (request == null) {
            notice = LyricsImportResult.Unavailable
            return@LaunchedEffect
        }
        val document = selectedDocument()
        if (document == null) {
            if (!repository.isImportRequestAvailable(request)) notice = LyricsImportResult.Unavailable
            return@LaunchedEffect
        }
        loading = true
        notice = null
        try {
            val retained = picker?.retain(document) ?: document
            currentCoroutineContext().ensureActive()
            if (documentReference != retained.reference || readGrantToken != retained.grantToken) return@LaunchedEffect
            releaseReadGrant = retained.releaseReadGrant
            // Retain the selected document even while a rescan is pending, but do not read its
            // contents or build a preview until the original canonical destination is available.
            if (!repository.isImportRequestAvailable(request)) {
                notice = LyricsImportResult.Unavailable
                return@LaunchedEffect
            }
            val text = picker?.read(retained)
            currentCoroutineContext().ensureActive()
            if (text == null) notice = LyricsImportResult.Invalid
            else when (val review = repository.previewImport(request, text)) {
                is LyricsImportReview.Ready -> preview = review.preview
                is LyricsImportReview.Rejected -> notice = review.reason
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { notice = LyricsImportResult.Failed }
        finally { if (currentCoroutineContext().isActive) loading = false }
    }

    val importAction: ((Track) -> Unit)? = if (picker == null || closingRequested || grantCleanupPending) null else { track ->
        if (requestState == null && !confirming) {
            val selected = repository.prepareImportRequest(track)
            if (selected == null) rejectedTitle = track.title.take(4_096)
            else {
                requestState = selected.savedState
                rejectedTitle = null
                completed = false
                chooseDocument()
            }
        }
    }
    CompositionLocalProvider(LocalLyricsImportAction provides importAction) { content() }

    if (rejectedTitle != null || (requestState != null && !waitingForPicker && !launchRequested)) {
        val reviewed = preview
        val title = (if (completed) completedTitle else null) ?: reviewed?.track?.title ?: request?.title ?: rejectedTitle.orEmpty()
        val unavailable = rejectedTitle != null || request == null || notice == LyricsImportResult.Unavailable
        Dialog(onDismissRequest = ::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Column(Modifier.fillMaxSize().background(AppTheme.colors.background)
                .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = pageInset, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppText(stringResource(Res.string.lyrics_import), AppTheme.typography.headline, modifier = Modifier.weight(1f))
                    if (!confirming && !closingRequested && !grantCleanupPending) IconButton(AppIcons.Close,
                        stringResource(if (request == null) Res.string.action_close else Res.string.lyrics_identity_import_cancel), ::close)
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        AppText(stringResource(Res.string.lyrics_identity_target, title), AppTheme.typography.title3)
                        (reviewed?.track?.artist ?: request?.artist)?.let {
                            AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel)
                        }
                    }
                    when {
                        completed -> item { AppText(stringResource(Res.string.lyrics_identity_saved), AppTheme.typography.body,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        loading || waitingForPicker || confirming || closingRequested -> item {
                            AppText(stringResource(if (confirming) Res.string.lyrics_identity_saving else Res.string.lyrics_identity_import_reading),
                                AppTheme.typography.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                        unavailable -> item { AppText(stringResource(if (request == null) Res.string.lyrics_identity_import_start_unavailable
                            else Res.string.lyrics_identity_import_unavailable), AppTheme.typography.body,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                        notice != null -> item {
                            AppText(stringResource(when (notice) {
                                LyricsImportResult.Changed -> Res.string.lyrics_identity_changed
                                LyricsImportResult.Invalid -> Res.string.lyrics_identity_import_invalid
                                else -> Res.string.lyrics_identity_write_failed
                            }), AppTheme.typography.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        }
                    }
                    if (reviewed != null && !completed) {
                        item { AppText(stringResource(if (reviewed.replacesExisting) Res.string.lyrics_identity_replace_hint
                            else Res.string.lyrics_identity_import_hint), AppTheme.typography.body) }
                        itemsIndexed(reviewed.lyrics.lines) { _, line -> AppText(line.text, AppTheme.typography.body) }
                    }
                }
                if (!confirming && !closingRequested && !grantCleanupPending) {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            completed -> ActionButton(stringResource(Res.string.lyrics_identity_import_done), ::close, Modifier.fillMaxWidth())
                            reviewed != null -> {
                                ActionButton(stringResource(if (reviewed.replacesExisting) Res.string.lyrics_identity_replace
                                    else Res.string.lyrics_identity_import_confirm), onClick = {
                                    confirming = true
                                    scope.launch {
                                        try {
                                            val result = repository.confirmImport(reviewed)
                                            preview = null
                                            notice = result
                                            if (result == LyricsImportResult.Saved) {
                                                completedTitle = reviewed.track.title
                                                completed = true
                                                // Cleanup is itself saveable; do not clear its URI before IO
                                                // or let Activity recreation expose Done while it is pending.
                                                grantCleanupPending = true
                                            }
                                        } finally { confirming = false }
                                    }
                                }, modifier = Modifier.fillMaxWidth())
                            }
                            request != null && !loading && !launchRequested -> {
                                ActionButton(stringResource(Res.string.lyrics_retry), onClick = {
                                    if (unavailable) onRefreshLibrary()
                                    if (documentReference == null) chooseDocument() else retry++
                                }, modifier = Modifier.fillMaxWidth())
                                if (documentReference != null) {
                                    ActionButton(stringResource(Res.string.lyrics_identity_import_choose), ::chooseDocument, Modifier.fillMaxWidth())
                                }
                            }
                        }
                        if (!completed) ActionButton(stringResource(if (request == null) Res.string.action_close
                            else Res.string.lyrics_identity_import_cancel), ::close, Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

private fun newImportRequestKey(): String = Random.nextLong().toULong().toString(16) + "-" +
    Random.nextLong().toULong().toString(16)
