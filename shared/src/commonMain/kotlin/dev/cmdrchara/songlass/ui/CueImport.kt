package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.*
import dev.cmdrchara.songlass.core.cue.CueSheets
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.MutationTicket
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.InfoSheet
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

private data class CueReview(val name: String, val text: String, val tracks: List<Track>,
    val source: List<Track>, val ticket: MutationTicket?)

@Composable
internal fun rememberCueImportAction(state: AppState): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf(false) }
    val savedSheets by state.cueSheets.collectAsState()
    var review by remember { mutableStateOf<CueReview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val fallbackName = stringResource(Res.string.cue_import_title)
    val title = stringResource(Res.string.cue_import_title)
    val restoration by state.restoreMutations.state.collectAsState()
    val ticket = remember(restoration) { state.restoreMutations.ticket() }
    LaunchedEffect(restoration.revision) { choosing = false; review = null }
    val importer = key(ticket) { rememberTextFileImporter(TextFileKind.CueSheet) { fileName, text ->
        if (!busy && text != null) {
            scope.launch {
                busy = true
                try {
                    if (!state.restoreMutations.accepts(ticket) || !state.isLibraryReadyForPlaylistFiles()) {
                        failed = true
                    } else {
                        val source = state.allTracks.value
                        val clips = withContext(Dispatchers.Default) { CueSheets.parse(text)?.let { CueSheets.resolve(it, source) } }
                        if (clips == null || state.allTracks.value !== source || !state.restoreMutations.accepts(ticket)) failed = true
                        else review = CueReview(fileName?.substringBeforeLast('.')?.take(256)?.takeIf(String::isNotBlank) ?: fallbackName, text, clips, source, ticket)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        } else if (text == null) failed = true
    } }
    ActionSheet(visible = choosing, title = title, cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { choosing = false }, actions = buildList {
            if (importer != null) add(SheetAction(stringResource(Res.string.cue_import_choose), AppIcons.ImportFile) {
                choosing = false; importer()
            })
            savedSheets.forEach { sheet ->
                add(SheetAction(stringResource(Res.string.cue_import_remove, sheet.name), AppIcons.Close) {
                    choosing = false; busy = true
                    scope.launch {
                        try { failed = !state.removeCueSheet(sheet.id, ticket) }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failed = true }
                        finally { busy = false }
                    }
                })
            }
        })
    val checked = review
    ActionSheet(visible = checked != null, title = title, cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { if (!busy) review = null }, actions = if (checked == null || busy) emptyList() else listOf(
            SheetAction(stringResource(Res.string.cue_import_confirm, checked.tracks.size), AppIcons.ImportFile) {
                busy = true
                scope.launch {
                    try {
                        val count = state.importCueSheet(checked.name, checked.text, checked.source, checked.ticket)
                        failed = count == null
                        review = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failed = true; review = null }
                    finally { busy = false }
                }
            }))
    InfoSheet(visible = failed, title = title, closeLabel = stringResource(Res.string.action_close), onDismiss = { failed = false },
        rows = listOf(InfoRow(stringResource(Res.string.cue_import_failed), stringResource(Res.string.cue_import_hint))))
    return importer?.let { { if (!busy) choosing = true } }
}
