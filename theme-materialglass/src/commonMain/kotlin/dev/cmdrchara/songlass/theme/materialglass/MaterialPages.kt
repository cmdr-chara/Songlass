package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook

/**
 * Collection details in Material 3 Expressive: an assist chip with the type, the title, the
 * artist (in the primary colour when it leads somewhere) and details, then a button group —
 * Play filled, Shuffle tonal, the extra action as a toggle. A pressed button widens while its
 * neighbour gives way, as in M3 Expressive button groups.
 */
@Composable
internal fun MaterialCollectionDetails(
    kind: String,
    title: String,
    subtitle: String?,
    onSubtitleClick: (() -> Unit)?,
    meta: String,
    playLabel: String,
    shuffleLabel: String,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    action: @Composable () -> Unit,
    modifier: Modifier,
) {
    val palette = currentPalette()
    Column(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        MaterialTag(kind, Modifier)
        Spacer(Modifier.height(10.dp))
        AppText(title, AppTheme.typography.title1.copy(fontWeight = FontWeight.Bold), maxLines = 3)
        if (subtitle != null) {
            AppText(
                subtitle,
                AppTheme.typography.headline.copy(fontWeight = FontWeight.SemiBold),
                color = if (onSubtitleClick != null) palette.primary else AppTheme.colors.secondaryLabel,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp).then(
                    if (onSubtitleClick != null) Modifier.clip(CircleShape).clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onSubtitleClick) else Modifier,
                ),
            )
        }
        AppText(meta, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(16.dp))
        val playInteraction = remember { MutableInteractionSource() }
        val shuffleInteraction = remember { MutableInteractionSource() }
        val playPressed by playInteraction.collectIsPressedAsState()
        val shufflePressed by shuffleInteraction.collectIsPressedAsState()
        Row(Modifier.fillMaxWidth().height(56.dp).padding(bottom = 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            GroupButton(AppIcons.Play, playLabel, palette.primary, palette.onPrimary, playInteraction, onPlay, widen = playPressed, giveWay = shufflePressed)
            GroupButton(AppIcons.Shuffle, shuffleLabel, palette.primaryContainer, palette.onPrimaryContainer, shuffleInteraction, onShuffle, widen = shufflePressed, giveWay = playPressed)
            action()
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun RowScope.GroupButton(
    icon: ImageVector,
    label: String,
    container: Color,
    content: Color,
    interaction: MutableInteractionSource,
    onClick: () -> Unit,
    widen: Boolean,
    giveWay: Boolean,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val weight by animateFloatAsState(
        when {
            reduceMotion -> 1f
            widen -> 1.15f
            giveWay -> 0.9f
            else -> 1f
        },
        if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 600f),
        label = "groupWeight",
    )
    // Pressed, the pill squares off a little: shape follows state in M3 Expressive.
    val corner by animateDpAsState(if (widen && !reduceMotion) 16.dp else 28.dp, if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 600f), label = "groupCorner")
    Row(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interaction, indication = MaterialRipple, role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, contentDescription = null, size = 22.dp, tint = content)
        Spacer(Modifier.width(8.dp))
        AppText(label, AppTheme.typography.headline.copy(fontWeight = FontWeight.Bold), color = content, maxLines = 1)
    }
}

/** Material assist chip: outlined, slightly rounded, label in the secondary colour. */
@Composable
internal fun MaterialTag(text: String, modifier: Modifier) {
    Box(
        modifier
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppTheme.colors.label.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, AppTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.secondaryLabel, maxLines = 1)
    }
}

/** Material filter chips: outlined when off; filled in the primary container with a check when on. */
@Composable
internal fun MaterialFilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    LazyRow(modifier.selectableGroup(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { index, option ->
            val selected = index == selectedIndex
            val fill by animateColorAsState(if (selected) palette.primaryContainer else Color.Transparent, if (reduceMotion) snap() else tween(150), label = "chipFill")
            val shape = RoundedCornerShape(8.dp)
            Row(
                Modifier
                    .height(32.dp)
                    .clip(shape)
                    .background(fill)
                    .then(if (selected) Modifier else Modifier.border(1.dp, AppTheme.colors.label.copy(alpha = 0.3f), shape))
                    .selectable(selected, remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Tab) { onSelect(index) }
                    .padding(start = if (selected) 8.dp else 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    AppIcon(AppIcons.Checkmark, contentDescription = null, size = 18.dp, tint = palette.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                }
                AppText(
                    option,
                    AppTheme.typography.footnote.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) palette.onPrimaryContainer else AppTheme.colors.label,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Artist picture cut into the scalloped expressive shape. */
@Composable
internal fun MaterialArtistAvatar(artwork: Artwork, modifier: Modifier) {
    Box(modifier.clip(ExpressiveShapes.Cookie)) {
        AlbumArt(artwork, cornerRadius = 0.dp, modifier = Modifier.fillMaxSize())
    }
}

/** Material search bar: a 56 dp pill in a raised container, search icon, clear button. */
@Composable
internal fun MaterialSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) {
    val palette = currentPalette()
    val colors = AppTheme.colors
    Row(
        modifier.fillMaxWidth().height(56.dp).clip(CircleShape).background(palette.containerHigh).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Search, contentDescription = null, size = 24.dp, tint = colors.label)
        Spacer(Modifier.width(16.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) AppText(placeholder, AppTheme.typography.body, color = colors.secondaryLabel, maxLines = 1)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = AppTheme.typography.body.copy(color = colors.label),
                cursorBrush = SolidColor(palette.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button) { onQueryChange("") }
                    .semantics { contentDescription = clearLabel },
                contentAlignment = Alignment.Center,
            ) {
                AppIcon(AppIcons.Close, contentDescription = null, size = 22.dp, tint = colors.label)
            }
        }
    }
}

/**
 * Material icon button, 48 dp target. Standard: the icon alone. Filled: a tonal circle. Selected
 * (a toggle such as Pin): the primary colour, and the circle morphs into a rounded square, as
 * M3 Expressive toggle buttons change shape with their state.
 */
@Composable
internal fun MaterialIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) {
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val corner by animateDpAsState(if (selected) 14.dp else 24.dp, if (reduceMotion) snap() else spring(dampingRatio = 0.6f, stiffness = 500f), label = "iconButtonShape")
    val container = when {
        selected -> palette.primary
        filled -> palette.primaryContainer
        else -> Color.Transparent
    }
    val tint = when {
        selected -> palette.onPrimary
        filled -> palette.onPrimaryContainer
        else -> LocalContentColor.current
    }
    val size = if (filled || selected) 48.dp else 40.dp
    Box(
        modifier
            .size(48.dp)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .clickable(remember { MutableInteractionSource() }, indication = MaterialRipple, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 24.dp, tint = tint)
        }
    }
}
