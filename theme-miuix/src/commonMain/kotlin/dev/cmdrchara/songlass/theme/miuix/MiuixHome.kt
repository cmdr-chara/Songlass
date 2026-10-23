package dev.cmdrchara.songlass.theme.miuix

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.HomeHighlight
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
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

/** Action collections and album recommendations have different visual roles on Home. */
@Composable
internal fun MiuixHomeHighlights(items: List<HomeHighlight>, modifier: Modifier) {
    Column(modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { pick ->
            key(pick.key) {
                Card(
                    modifier = Modifier.fillMaxWidth().prefetchArtworkOnPress(pick.artwork.takeIf { pick.icon == null }).semantics { role = Role.Button },
                    cornerRadius = 22.dp,
                    insideMargin = PaddingValues(0.dp),
                    colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground),
                    onClick = pick.onClick,
                ) {
                    val action = pick.icon
                    if (action != null) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            HomeMosaic(pick.artworks.ifEmpty { listOf(pick.artwork) }, Modifier.size(48.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                AppText(pick.title, AppTheme.typography.headline, maxLines = 2)
                                pick.subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2) }
                            }
                            AppIcon(action, null, size = 22.dp, tint = AppTheme.colors.accent)
                        }
                    } else {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AlbumArt(pick.artwork, Modifier.size(88.dp), cornerRadius = 16.dp)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                AppText(pick.label, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                                AppText(pick.title, AppTheme.typography.headline, maxLines = 2)
                                pick.subtitle?.let { AppText(it, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel, maxLines = 2) }
                            }
                        }
                    }
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
