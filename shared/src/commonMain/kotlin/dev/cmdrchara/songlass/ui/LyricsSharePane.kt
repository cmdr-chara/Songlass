package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.Alignment
import androidx.compose.ui.state.ToggleableState
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsSharing
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkImage
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun LyricsShareDialog(track: Track, lyrics: Lyrics, active: Int, onClose: () -> Unit) {
    var selected by remember(lyrics) { mutableStateOf(emptySet<Int>()) }
    var preview by remember { mutableStateOf(false) }
    var limited by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val excerpt = remember(lyrics, selected) { LyricsSharing.excerpt(lyrics, selected) }
    val sharer = rememberLyricsCardSharer { success -> working = false; failed = !success }
    LaunchedEffect(lyrics) {
        val first = active.takeIf { it in lyrics.lines.indices && lyrics.lines[it].text.isNotBlank() }
            ?: lyrics.lines.indexOfFirst { it.text.isNotBlank() }
        selected = LyricsSharing.toggle(lyrics, emptySet(), first)
    }
    val colors = AppTheme.colors
    val ink = if (colors.isDark) Color.White else Color(0xFF19191D)
    Dialog(onDismissRequest = { if (!working) { if (preview) preview = false else onClose() } },
        properties = DialogProperties(usePlatformDefaultWidth = false)) {
      Column(Modifier.fillMaxSize().background(colors.background).windowInsetsPadding(WindowInsets.safeDrawing)
          .padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            AppText(stringResource(if (preview) Res.string.lyrics_card_preview else Res.string.lyrics_share_select), AppTheme.typography.headline)
            IconButton(AppIcons.Close, stringResource(Res.string.lyrics_share_close), { if (!working) onClose() })
        }
        Spacer(Modifier.height(16.dp))
        if (preview) {
            val cover = rememberArtworkImage(track.artwork(), 256)
            val look = LocalGlassLook.current.lyricsCardStyle
            val textStyle = AppTheme.typography.body
            val measurer = rememberTextMeasurer()
            val label = stringResource(Res.string.lyrics_card_label)
            var image by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            var rendering by remember { mutableStateOf(true) }
            LaunchedEffect(excerpt, cover, colors, look, textStyle, label) {
                image = null; rendering = true
                try {
                    image = withContext(Dispatchers.Default) {
                        renderLyricsCard(track.title, track.artist.orEmpty(), excerpt, cover, colors, look, textStyle, measurer, label)
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { image = null }
                finally { rendering = false }
            }
            val bitmap = image
            if (bitmap != null) Image(bitmap, track.title + "\n" + excerpt.joinToString("\n"),
                Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp), contentScale = ContentScale.Fit)
            else Box(Modifier.weight(1f).fillMaxWidth()) {
                AppText(stringResource(if (rendering) Res.string.lyrics_card_rendering else Res.string.lyrics_card_fit), AppTheme.typography.body)
            }
            if (failed) AppText(stringResource(Res.string.lyrics_share_failed), AppTheme.typography.footnote, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (sharer == null) AppText(stringResource(Res.string.lyrics_share_unavailable), AppTheme.typography.footnote)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(stringResource(Res.string.lyrics_share_edit), { if (!working) preview = false }, Modifier.weight(1f))
                if (bitmap != null && sharer != null && !working) ActionButton(stringResource(Res.string.lyrics_share_image), {
                    working = true; failed = false; sharer(bitmap, track.title)
                }, Modifier.weight(1f), icon = AppIcons.Share)
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(bottom = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArt(track.artwork(), Modifier.size(52.dp), cornerRadius = 12.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    AppText(track.title, AppTheme.typography.headline, color = ink, maxLines = 1)
                    AppText(artistName(track.artist), AppTheme.typography.subheadline, color = colors.secondaryLabel, maxLines = 1)
                }
            }
            AppText(stringResource(Res.string.lyrics_share_hint, LyricsSharing.MAX_LINES, LyricsSharing.MAX_CHARACTERS), AppTheme.typography.footnote)
            if (limited) AppText(stringResource(Res.string.lyrics_share_limit), AppTheme.typography.footnote,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
                itemsIndexed(lyrics.lines) { index, line ->
                    if (line.text.isNotBlank()) {
                        val checked = index in selected
                        val corner = LocalGlassLook.current.lyricsPresentation.selectionCorner.dp
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(if (checked) colors.fill else Color.Transparent)
                            .toggleable(value = checked, role = Role.Checkbox, onValueChange = {
                                val next = LyricsSharing.toggle(lyrics, selected, index)
                                limited = next == selected && index !in selected
                                selected = next
                            }).padding(horizontal = 16.dp, vertical = 18.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AppText(line.text, AppTheme.typography.title2.copy(lineHeight = 33.sp),
                                color = ink.copy(alpha = if (checked) 1f else .64f), modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(16.dp))
                            Box(Modifier.size(24.dp).border(1.dp,
                                if (checked) colors.accent else colors.tertiaryLabel, CircleShape),
                                contentAlignment = Alignment.Center) {
                                if (checked) AppIcon(AppIcons.Checkmark, null, size = 16.dp, tint = colors.accent)
                            }
                        }
                    }
                }
            }
            if (excerpt.isNotEmpty()) ActionButton(stringResource(Res.string.lyrics_share_preview_count, excerpt.size),
                { preview = true; failed = false }, Modifier.fillMaxWidth())
        }
    }
}
}
