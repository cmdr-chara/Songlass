package dev.cmdrchara.songlass.theme.miuix

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Card as NativeCard
import top.yukonga.miuix.kmp.basic.CardDefaults

@Composable
internal fun MiuixPoster(label: String, title: String, subtitle: String?, artwork: Artwork, onClick: () -> Unit, modifier: Modifier) {
    NativeCard(modifier, cornerRadius = 26.dp, insideMargin = PaddingValues(0.dp), onClick = onClick,
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) {
        AlbumArt(artwork, Modifier.fillMaxWidth().aspectRatio(1.05f), cornerRadius = 0.dp)
        Column(Modifier.padding(16.dp)) {
            AppText(label, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            AppText(title, AppTheme.typography.title3, maxLines = 2)
            subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2) }
        }
    }
}

@Composable
internal fun MiuixShortcut(title: String, artwork: Artwork, circular: Boolean, onClick: () -> Unit, modifier: Modifier) {
    NativeCard(modifier.heightIn(min = 64.dp), cornerRadius = 19.dp, insideMargin = PaddingValues(10.dp), onClick = onClick,
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(artwork, Modifier.size(42.dp), cornerRadius = if (circular) 21.dp else 11.dp)
            AppText(title, AppTheme.typography.subheadline,
                modifier = Modifier.padding(start = 10.dp).weight(1f), maxLines = 2)
        }
    }
}

@Composable
internal fun MiuixCollection(title: String, subtitle: String, icon: ImageVector, artworks: List<Artwork>, circular: Boolean, onClick: () -> Unit, modifier: Modifier) {
    NativeCard(modifier.heightIn(min = 112.dp), cornerRadius = 20.dp, insideMargin = PaddingValues(16.dp), onClick = onClick,
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) {
        AppIcon(icon, null, size = 28.dp, tint = AppTheme.colors.accent)
        Spacer(Modifier.height(12.dp))
        AppText(title, AppTheme.typography.headline, maxLines = 2)
        Spacer(Modifier.height(2.dp))
        AppText(subtitle, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2)
    }
}

@Composable
internal fun MiuixShelf(items: List<ShelfItem>, style: ShelfStyle, itemSize: Dp, modifier: Modifier) {
    val collections = items.filterNot { it.isAction }
    val actions = items.filter { it.isAction }
    if (style == ShelfStyle.Pins && items.size == 1 && !items.single().isAction) {
        val item = items.single()
        NativeCard(modifier.padding(horizontal = 16.dp).fillMaxWidth().heightIn(min = 80.dp)
            .prefetchArtworkOnPress(item.artwork), cornerRadius = 20.dp, insideMargin = PaddingValues(12.dp), onClick = item.onClick,
            colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val art = item.artwork
                if (art != null) AlbumArt(art, Modifier.size(56.dp), cornerRadius = if (item.circular) 28.dp else 14.dp)
                else Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(AppTheme.colors.fill), contentAlignment = Alignment.Center) {
                    AppIcon(item.icon ?: AppIcons.Library, null, size = 26.dp, tint = AppTheme.colors.accent)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    AppText(item.title, AppTheme.typography.headline, maxLines = 2)
                    item.subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2) }
                }
                AppIcon(AppIcons.ChevronRight, null, size = 18.dp, tint = AppTheme.colors.secondaryLabel)
            }
        }
        return
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (collections.isNotEmpty()) {
            LazyRow(Modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(collections, key = { it.key }) { item ->
                    NativeCard(Modifier.width(itemSize).prefetchArtworkOnPress(item.artwork), cornerRadius = 0.dp, insideMargin = PaddingValues(0.dp), onClick = item.onClick,
                        colors = CardDefaults.defaultColors(color = Color.Transparent)) {
                        val art = item.artwork
                        if (art != null) AlbumArt(art, Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 16.dp)
                        else Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(AppTheme.colors.fill), contentAlignment = Alignment.Center) {
                            AppIcon(item.icon ?: AppIcons.Library, null, size = 32.dp, tint = AppTheme.colors.accent)
                        }
                        Spacer(Modifier.height(8.dp))
                        AppText(item.title, AppTheme.typography.subheadline.copy(fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.Medium), maxLines = 2)
                        item.subtitle?.let { AppText(it, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel, maxLines = 1) }
                    }
                }
            }
        }
        if (actions.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(actions, key = { it.key }) { item ->
                    MiuixAction(item.title, item.icon, item.onClick, Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}

@Composable
internal fun MiuixDetails(kind: String, title: String, subtitle: String?, onSubtitleClick: (() -> Unit)?, meta: String, playLabel: String, shuffleLabel: String, onPlay: () -> Unit, onShuffle: () -> Unit, action: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MiuixTag(kind, Modifier)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                AppText(title, AppTheme.typography.title1, maxLines = 3)
                subtitle?.let { AppText(it, AppTheme.typography.body, color = AppTheme.colors.accent, maxLines = 2,
                    modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick).padding(vertical = 4.dp) else Modifier) }
            }
            action()
        }
        AppText(meta, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiuixAction(playLabel, AppIcons.Play, onPlay, Modifier.weight(1f), primary = true)
            MiuixAction(shuffleLabel, AppIcons.Shuffle, onShuffle, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun MiuixArtworkHeader(artwork: Artwork, label: String?, modifier: Modifier) {
    Box(modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = 20.dp, end = 20.dp, top = 68.dp, bottom = 20.dp)) {
        AlbumArt(artwork, Modifier.fillMaxWidth().aspectRatio(1f).semantics { if (label != null) contentDescription = label }, cornerRadius = 28.dp)
    }
}
