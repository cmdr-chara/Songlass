package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.cmdrchara.songlass.core.settings.AppSettings
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsImportResult
import dev.cmdrchara.songlass.core.lyrics.LyricsRecoveryEntry
import dev.cmdrchara.songlass.core.lyrics.LyricsRelinkPreview
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.lyrics.LyricsResult
import dev.cmdrchara.songlass.core.lyrics.LyricsUnavailable
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.LyricLine
import dev.cmdrchara.songlass.designsystem.LocalLyricsPlaybackPosition
import dev.cmdrchara.songlass.designsystem.LocalLyricsTextScale
import dev.cmdrchara.songlass.designsystem.LyricWordTiming
import dev.cmdrchara.songlass.designsystem.LyricsViewport
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LyricsPane(
    track: Track,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    repository: LyricsRepository,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    playbackPosition: (() -> Long)? = null,
    settings: AppSettings,
    visible: Boolean,
) {
    val preferences by settings.state.collectAsState()
    val presentation = preferences.lyricsPresentation
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    var options by remember { mutableStateOf(false) }
    val repositoryRevision by repository.changes.collectAsState()
    var result by remember(track, repositoryRevision) { mutableStateOf<LyricsResult?>(null) }
    var sharing by remember(track, repositoryRevision) { mutableStateOf(false) }
    var recovering by remember(track) { mutableStateOf(false) }
    var revision by remember(track) { mutableIntStateOf(0) }
    var importResult by remember(track) { mutableStateOf<LyricsImportResult?>(null) }
    val importFile = LocalLyricsImportAction.current
    LaunchedEffect(track, revision, repositoryRevision) {
        result = null
        result = withContext(Dispatchers.Default) { repository.load(track, refresh = revision > 0) }
    }
    if (recovering) LyricsRecoveryDialog(track, repository, repositoryRevision,
        onClose = { recovering = false }, onLinked = { recovering = false; importResult = LyricsImportResult.Saved })
    val lyrics = (result as? LyricsResult.Found)?.lyrics
    val secondary = LocalContentColor.current.copy(alpha = 0.65f)
    KeepLyricsScreenAwake(visible && presentation.keepScreenOn && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        lyrics != null && !lyrics.instrumental && lyrics.lines.any { it.text.isNotBlank() })
    if (options) LyricsOptionsDialog(settings, presentation) { options = false }
    val adjustedPosition = presentation.displayPosition(positionMs)
    val adjustedClock = remember(playbackPosition, presentation.offsetMs) {
        playbackPosition?.let { clock -> { presentation.displayPosition(clock()) } }
    }
    if (sharing && lyrics != null && !lyrics.instrumental) {
        androidx.compose.runtime.key(track, lyrics) {
            LyricsShareDialog(track, lyrics, lyrics.activeLineAt(adjustedPosition), { sharing = false })
        }
    }
    Column(modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(rememberTrackArtwork(track), Modifier.size(44.dp), cornerRadius = 6.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                AppText(track.title, AppTheme.typography.headline, maxLines = 1)
                AppText(artistName(track.artist), AppTheme.typography.subheadline, color = secondary, maxLines = 1)
            }
            IconButton(AppIcons.Settings, stringResource(Res.string.lyrics_options), { options = true })
            if (lyrics != null && !lyrics.instrumental && lyrics.lines.any { it.text.isNotBlank() }) {
                IconButton(AppIcons.Share, stringResource(Res.string.lyrics_share), { sharing = true })
            }
        }
        if (lyrics != null && !lyrics.instrumental) {
            androidx.compose.runtime.key(track, lyrics) {
                LyricsLines(lyrics, adjustedPosition, presentation.displayPosition(durationMs).coerceAtLeast(0L), isPlaying,
                    { onSeek(presentation.seekPosition(it, durationMs)) }, Modifier.weight(1f).fillMaxWidth(), adjustedClock,
                    presentation.textSizePercent)
            }
        } else {
            Column(
                Modifier.weight(1f).fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                AppIcon(if (lyrics?.instrumental == true) AppIcons.MusicNote else AppIcons.Lyrics, null, size = 36.dp, tint = secondary)
                Spacer(Modifier.height(16.dp))
                AppText(
                    stringResource(when {
                        lyrics?.instrumental == true -> Res.string.lyrics_instrumental
                        result == null -> Res.string.lyrics_loading
                        result == LyricsUnavailable -> Res.string.lyrics_identity_unavailable
                        result == LyricsResult.Failed -> Res.string.lyrics_error
                        else -> Res.string.lyrics_not_found
                    }),
                    AppTheme.typography.title3,
                    textAlign = TextAlign.Center,
                )
                if (result != null && lyrics?.instrumental != true) {
                    Spacer(Modifier.height(8.dp))
                    AppText(
                        stringResource(when {
                            result == LyricsUnavailable -> Res.string.lyrics_identity_unavailable_hint
                            result == LyricsResult.Failed -> Res.string.lyrics_error_hint
                            else -> Res.string.lyrics_not_found_hint
                        }),
                        AppTheme.typography.subheadline,
                        color = secondary,
                        textAlign = TextAlign.Center,
                    )
                }
                if (result == LyricsResult.Failed || result == LyricsResult.NotFound || result == LyricsUnavailable) {
                    Spacer(Modifier.height(20.dp))
                    ActionButton(stringResource(Res.string.lyrics_retry), onClick = { revision++ })
                }
            }
        }
        if (importResult != null) {
            val notice = when {
                importResult == LyricsImportResult.Saved -> Res.string.lyrics_identity_saved
                importResult == LyricsImportResult.Unavailable -> Res.string.lyrics_identity_unavailable_hint
                importResult == LyricsImportResult.Changed -> Res.string.lyrics_identity_changed
                importResult == LyricsImportResult.Invalid -> Res.string.lyrics_import_error
                else -> Res.string.lyrics_identity_write_failed
            }
            AppText(stringResource(notice), AppTheme.typography.footnote,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically) {
            if (!sharing) {
                ActionButton(stringResource(Res.string.lyrics_identity_recover), { recovering = true }, Modifier, icon = AppIcons.Folder)
                if (importFile != null) IconButton(AppIcons.ImportFile, stringResource(Res.string.lyrics_import), onClick = { importFile(track) })
            }
        }
    }
}

@Composable
private fun LyricsLines(lyrics: Lyrics, positionMs: Long, durationMs: Long, isPlaying: Boolean, onSeek: (Long) -> Unit, modifier: Modifier, playbackPosition: (() -> Long)?, textSizePercent: Int) {
    val latestPosition by rememberUpdatedState(positionMs)
    val latestClock by rememberUpdatedState(playbackPosition)
    var framePosition by remember(lyrics) { mutableLongStateOf(positionMs) }
    val drawClock = remember(lyrics) { { framePosition } }
    SideEffect { framePosition = playbackPosition?.invoke() ?: positionMs }
    LaunchedEffect(isPlaying, playbackPosition != null) {
        framePosition = latestClock?.invoke() ?: latestPosition
        if (isPlaying && playbackPosition != null) {
            while (true) withFrameNanos { framePosition = latestClock?.invoke() ?: latestPosition }
        }
    }
    val listState = rememberLazyListState()
    val active by remember(lyrics) { derivedStateOf { lyrics.activeLineAt(framePosition) } }
    var following by remember { mutableStateOf(true) }
    val reduceMotion = AppTheme.accessibility.reduceMotion
    // Scroll and emphasis use the same sampled native clock; never anticipate a phrase.
    val scrollTarget = active
    // A user's drag pauses following; it never fights them while they read another verse.
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) following = false
        }
    }
    val textScale = textSizePercent / 100f
    val fontScale = LocalDensity.current.fontScale * textScale
    LaunchedEffect(scrollTarget, following, reduceMotion, fontScale) {
        if (following && lyrics.isSynced) {
            if (scrollTarget < 0) {
                listState.scrollToItem(0)
                return@LaunchedEffect
            }
            val height = snapshotFlow { listState.layoutInfo.viewportSize.height }.first { it > 0 }
            val offset = -(height / 3)
            if (reduceMotion) listState.scrollToItem(scrollTarget, offset)
            else listState.animateScrollToItem(scrollTarget, offset)
        }
    }
    Box(modifier) {
        val breakLabel = stringResource(Res.string.lyrics_break)
        val seekLabel = stringResource(Res.string.lyrics_seek)
        CompositionLocalProvider(LocalLyricsPlaybackPosition provides drawClock) {
        LyricsViewport(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp),
            ) {
                itemsIndexed(lyrics.lines) { index, line ->
                    CompositionLocalProvider(LocalLyricsTextScale provides textScale) {
                    LyricLine(
                        text = if (lyrics.isSynced && line.text.isBlank()) "···" else line.text,
                        active = lyrics.isSynced && index == active,
                        subdued = lyrics.isSynced,
                        progress = if (index == active) lyrics.lineProgressAt(index, positionMs, durationMs) else null,
                        isPlaying = isPlaying,
                        words = remember(line) { line.words.map { LyricWordTiming(it.start, it.end, it.startMs, it.endMs) } },
                        positionMs = positionMs,
                        seekLabel = seekLabel,
                        onSeek = line.timeMs?.let { timestamp -> { following = true; onSeek(timestamp) } },
                        modifier = if (lyrics.isSynced && line.text.isBlank()) Modifier.semantics { contentDescription = breakLabel } else Modifier,
                    )
                    }
                }
            }
        }
        }
        if (!following && lyrics.isSynced) {
            ActionButton(
                stringResource(Res.string.lyrics_follow),
                onClick = { following = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            )
        }
    }
}


/** The same themed rows/buttons in all three styles; the document preview is a lazy, readable list. */
@Composable
private fun LyricsRecoveryDialog(
    track: Track,
    repository: LyricsRepository,
    repositoryRevision: Long,
    onClose: () -> Unit,
    onLinked: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<LyricsRecoveryEntry>>(emptyList()) }
    var preview by remember { mutableStateOf<LyricsRelinkPreview?>(null) }
    var loading by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<LyricsImportResult?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    LaunchedEffect(repository, repositoryRevision, revision) {
        if (preview != null) { preview = null; notice = LyricsImportResult.Changed }
        loading = true
        try { entries = repository.recoverableImports() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { entries = emptyList(); notice = LyricsImportResult.Failed }
        finally { loading = false }
    }
    fun dismiss() { if (!working) { if (preview != null) preview = null else onClose() } }
    Dialog(onDismissRequest = ::dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(AppTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = pageInset, vertical = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                AppText(stringResource(Res.string.lyrics_identity_recover), AppTheme.typography.headline,
                    modifier = Modifier.weight(1f))
                if (!working) IconButton(AppIcons.Close, stringResource(Res.string.lyrics_identity_close), { onClose() })
            }
            val selected = preview
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selected == null) {
                    item { AppText(stringResource(Res.string.lyrics_identity_intro), AppTheme.typography.body) }
                    if (loading) item { AppText(stringResource(Res.string.lyrics_loading), AppTheme.typography.body) }
                    else if (entries.isEmpty() && notice == null) item { AppText(stringResource(Res.string.lyrics_identity_empty), AppTheme.typography.body) }
                    items(entries, key = { it.key }) { entry ->
                        SettingsGroup {
                            ListRow(title = entry.title ?: entry.excerpt.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.lyrics_identity_unknown),
                                subtitle = if (entry.canLink && entry.title != null) entry.excerpt else null,
                                onClick = if (entry.canLink && !working) ({
                                    working = true; notice = null
                                    scope.launch {
                                        try {
                                            preview = repository.previewRelink(track, entry)
                                            if (preview == null) notice = LyricsImportResult.Changed
                                        } catch (cancelled: CancellationException) { throw cancelled }
                                        catch (_: Exception) { notice = LyricsImportResult.Failed }
                                        finally { working = false }
                                    }
                                }) else null)
                            if (!entry.canLink) AppText(stringResource(Res.string.lyrics_identity_invalid),
                                AppTheme.typography.body, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                        }
                    }
                } else {
                    item {
                        AppText(stringResource(Res.string.lyrics_identity_target, selected.track.title), AppTheme.typography.title3)
                        AppText(artistName(selected.track.artist), AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel)
                    }
                    item { AppText(stringResource(if (selected.replacesExisting) Res.string.lyrics_identity_replace_hint
                        else Res.string.lyrics_identity_link_hint), AppTheme.typography.body) }
                    if (selected.lyrics.instrumental) item {
                        AppText(stringResource(Res.string.lyrics_instrumental), AppTheme.typography.body)
                    }
                    itemsIndexed(selected.lyrics.lines) { _, line ->
                        AppText(line.text, AppTheme.typography.body, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (notice != null) item {
                    AppText(stringResource(when (notice) {
                        LyricsImportResult.Changed -> Res.string.lyrics_identity_changed
                        LyricsImportResult.Unavailable -> Res.string.lyrics_identity_unavailable_hint
                        LyricsImportResult.Invalid -> Res.string.lyrics_identity_invalid
                        else -> Res.string.lyrics_identity_write_failed
                    }), AppTheme.typography.body, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                }
            }
            if (working) AppText(stringResource(Res.string.lyrics_identity_saving), AppTheme.typography.body,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            else if (selected != null) {
                ActionButton(stringResource(if (selected.replacesExisting) Res.string.lyrics_identity_replace else Res.string.lyrics_identity_link), {
                    working = true; notice = null
                    scope.launch {
                        try {
                            val saved = repository.relink(selected)
                            if (saved == LyricsImportResult.Saved) onLinked()
                            else { notice = saved; preview = null; revision++ }
                        } finally { working = false }
                    }
                }, Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                ActionButton(stringResource(Res.string.lyrics_identity_back), { preview = null }, Modifier.fillMaxWidth())
            } else if (!loading && notice != null) {
                ActionButton(stringResource(Res.string.lyrics_retry), { notice = null; revision++ }, Modifier.fillMaxWidth())
            }
        }
    }
}
