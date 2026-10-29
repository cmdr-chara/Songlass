package dev.cmdrchara.songlass.theme.miuix

import dev.cmdrchara.songlass.designsystem.foundation.prefetchArtworkOnPress
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
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
    val interaction = remember { MutableInteractionSource() }
    NativeCard(
        modifier = miuixArtworkTileFeedback(modifier, interaction, RoundedCornerShape(20.dp)),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().heightIn(min = 80.dp)
                .clickable(interactionSource = interaction, indication = MiuixLook.pressIndication(), role = Role.Button, onClick = onClick)
                .padding(14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Same-width tiles pick one layout for the whole grid: no single longer label can make
            // a card taller than its neighbours (large text moves every card to the stacked form).
            val stacked = maxWidth < 130.dp * LocalDensity.current.fontScale
            if (!stacked) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppIcon(icon, null, size = 28.dp, tint = AppTheme.colors.accent)
                    MiuixCollectionLabels(title, subtitle, Modifier.weight(1f))
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppIcon(icon, null, size = 28.dp, tint = AppTheme.colors.accent)
                    MiuixCollectionLabels(title, subtitle, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun MiuixCollectionLabels(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        AppText(title, AppTheme.typography.headline, maxLines = 2)
        if (subtitle.isNotBlank()) {
            AppText(subtitle, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 2)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
                else Box(Modifier.size(56.dp).clip(RoundedCornerShape(if (item.circular) 28.dp else 14.dp)).background(AppTheme.colors.fill), contentAlignment = Alignment.Center) {
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
                        val artworkRadius = if (item.circular) itemSize / 2 else 16.dp
                        if (art != null) AlbumArt(art, Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = artworkRadius)
                        else Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(artworkRadius)).background(AppTheme.colors.fill), contentAlignment = Alignment.Center) {
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
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                actions.forEach { item ->
                    MiuixAction(item.compactTitle ?: item.title, item.icon, item.onClick, Modifier.weight(1f).heightIn(min = 48.dp),
                        maxLines = Int.MAX_VALUE, accessibilityLabel = item.title)
                }
            }
        }
    }
}

@Composable
internal fun MiuixDetails(kind: String, title: String, subtitle: String?, onSubtitleClick: (() -> Unit)?, meta: String, playLabel: String, shuffleLabel: String, onPlay: () -> Unit, onShuffle: () -> Unit, action: @Composable () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { MiuixTag(kind, Modifier) }
            action()
        }
        AppText(title, AppTheme.typography.title1, maxLines = 3)
        subtitle?.let { AppText(it, AppTheme.typography.body, color = AppTheme.colors.accent, maxLines = 2,
            modifier = if (onSubtitleClick != null) Modifier.clickable(onClick = onSubtitleClick).padding(vertical = 4.dp) else Modifier) }
        AppText(meta, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val requiredWidth = with(density) {
                maxOf(measurer.measure(playLabel, AppTheme.typography.headline).size.width,
                    measurer.measure(shuffleLabel, AppTheme.typography.headline).size.width).toDp() + 20.dp + 8.dp + 32.dp
            }
            if ((maxWidth - 12.dp) / 2 >= requiredWidth) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MiuixAction(playLabel, AppIcons.Play, onPlay, Modifier.weight(1f), primary = true)
                    MiuixAction(shuffleLabel, AppIcons.Shuffle, onShuffle, Modifier.weight(1f))
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiuixAction(playLabel, AppIcons.Play, onPlay, Modifier.fillMaxWidth(), primary = true)
                    MiuixAction(shuffleLabel, AppIcons.Shuffle, onShuffle, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun MiuixArtworkHeader(artwork: Artwork, label: String?, modifier: Modifier) {
    BoxWithConstraints(
        modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 20.dp, end = 20.dp, top = 52.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AlbumArt(artwork, Modifier.size(minOf(maxWidth, MiuixLook.collectionArtworkHeight()))
            .semantics { if (label != null) contentDescription = label }, cornerRadius = 28.dp)
    }
}
