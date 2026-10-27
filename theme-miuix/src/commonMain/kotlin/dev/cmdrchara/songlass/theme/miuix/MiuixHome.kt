package dev.cmdrchara.songlass.theme.miuix

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Scaffold

/** A compact pinned heading, with the same lazy music sections and navigation callbacks. */
@Composable
internal fun MiuixHomePage(title: String, trailing: @Composable () -> Unit, contentPadding: PaddingValues, modifier: Modifier, content: LazyListScope.() -> Unit) {
    val direction = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = AppTheme.colors.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        popupHost = {},
        topBar = { MiuixHeading(title, trailing = trailing) },
    ) { header ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = contentPadding.calculateStartPadding(direction),
                end = contentPadding.calculateEndPadding(direction),
                top = header.calculateTopPadding() + contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding(),
            ),
            content = content,
        )
    }
}

/** One quiet group; each recommendation keeps its own target, focus and navigation. */
@Composable
internal fun MiuixHomeHighlights(items: List<HomeHighlight>, modifier: Modifier) {
    if (items.isEmpty()) return
    Card(
        modifier = modifier.padding(horizontal = 16.dp).fillMaxWidth(),
        cornerRadius = 22.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground),
    ) {
        items.forEachIndexed { index, pick ->
            key(pick.key) {
                val interaction = remember { MutableInteractionSource() }
                val rowShape = RoundedCornerShape(
                    topStart = if (index == 0) 22.dp else 0.dp,
                    topEnd = if (index == 0) 22.dp else 0.dp,
                    bottomStart = if (index == items.lastIndex) 22.dp else 0.dp,
                    bottomEnd = if (index == items.lastIndex) 22.dp else 0.dp,
                )
                val action = pick.icon
                BasicComponent(
                    modifier = miuixArtworkTileFeedback(
                        Modifier.fillMaxWidth().prefetchArtworkOnPress(pick.artwork.takeIf { action == null }),
                        interaction,
                        rowShape,
                    ).clickable(
                        interactionSource = interaction,
                        indication = MiuixLook.pressIndication(),
                        role = Role.Button,
                        onClick = pick.onClick,
                    ),
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    interactionSource = interaction,
                    startAction = {
                        Box(Modifier.width(68.dp)) {
                            if (action != null) {
                                HomeMosaic(pick.artworks.ifEmpty { listOf(pick.artwork) }, Modifier.size(48.dp))
                            } else {
                                AlbumArt(pick.artwork, Modifier.size(64.dp), cornerRadius = 14.dp)
                            }
                        }
                    },
                    endActions = action?.let { { AppIcon(it, null, size = 22.dp, tint = AppTheme.colors.accent) } },
                ) {
                    if (action == null && pick.label.isNotBlank()) {
                        AppText(pick.label, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                        Spacer(Modifier.height(2.dp))
                    }
                    AppText(pick.title, AppTheme.typography.headline, maxLines = 2)
                    pick.subtitle?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(2.dp))
                        AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2)
                    }
                }
                if (index < items.lastIndex) {
                    Spacer(
                        Modifier.fillMaxWidth().padding(start = 90.dp, end = 14.dp)
                            .height(0.5.dp).background(AppTheme.colors.separator),
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeMosaic(artworks: List<Artwork>, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(15.dp))) {
        if (artworks.size == 1) AlbumArt(artworks.first(), Modifier.fillMaxSize(), cornerRadius = 0.dp)
        else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(2) { row ->
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(2) { column ->
                        AlbumArt(artworks[(row * 2 + column) % artworks.size], Modifier.weight(1f).fillMaxHeight(), cornerRadius = 0.dp)
                    }
                }
            }
        }
    }
}
