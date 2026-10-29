package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
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
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val labelStyle = AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold)
        val buttonHeight = with(density) { maxOf(48.dp, labelStyle.lineHeight.toDp() + 20.dp) }
        val requiredWidth = with(density) {
            maxOf(measurer.measure(playLabel, labelStyle).size.width,
                measurer.measure(shuffleLabel, labelStyle).size.width).toDp() + 20.dp + 6.dp + 24.dp
        }
        // Move the pin to metadata before stacking the two actions. Only widen on press
        // when the narrower neighbour still has enough space for its complete label.
        val inlineAction = (maxWidth - 48.dp - 16.dp) / 2 >= requiredWidth
        val buttonRoom = maxWidth - if (inlineAction) 48.dp + 16.dp else 8.dp
        val compact = buttonRoom / 2 >= requiredWidth
        val canWiden = buttonRoom * (0.9f / 2.05f) >= requiredWidth
        Column(Modifier.fillMaxWidth()) {
            MaterialTag(kind, Modifier)
            Spacer(Modifier.height(10.dp))
            AppText(title, AppTheme.typography.title2.copy(fontWeight = FontWeight.Bold), maxLines = 3)
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(meta, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.weight(1f).padding(top = 2.dp))
                if (!inlineAction) action()
            }
            Spacer(Modifier.height(12.dp))
            val playInteraction = remember { MutableInteractionSource() }
            val shuffleInteraction = remember { MutableInteractionSource() }
            val playPressed by playInteraction.collectIsPressedAsState()
            val shufflePressed by shuffleInteraction.collectIsPressedAsState()
            if (compact) {
                Row(Modifier.fillMaxWidth().height(buttonHeight), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GroupButton(AppIcons.Play, playLabel, palette.primary, palette.onPrimary, playInteraction, onPlay,
                        widen = canWiden && playPressed, giveWay = canWiden && shufflePressed)
                    GroupButton(AppIcons.Shuffle, shuffleLabel, palette.primaryContainer, palette.onPrimaryContainer, shuffleInteraction, onShuffle,
                        widen = canWiden && shufflePressed, giveWay = canWiden && playPressed)
                    if (inlineAction) action()
                }
            } else {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        GroupButton(AppIcons.Play, playLabel, palette.primary, palette.onPrimary, playInteraction, onPlay,
                            widen = false, giveWay = false, compact = false)
                    }
                    Row(Modifier.fillMaxWidth()) {
                        GroupButton(AppIcons.Shuffle, shuffleLabel, palette.primaryContainer, palette.onPrimaryContainer, shuffleInteraction, onShuffle,
                            widen = false, giveWay = false, compact = false)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
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
    compact: Boolean = true,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val weight by animateFloatAsState(
        when {
            reduceMotion -> 1f
            widen -> 1.15f
            giveWay -> 0.9f
            else -> 1f
        },
        if (reduceMotion) snap() else MaterialMotion.press(),
        label = "groupWeight",
    )
    // Pressed, the pill squares off a little: shape follows state in M3 Expressive.
    val corner by animateDpAsState(if (widen && !reduceMotion) MaterialShapes.Large else MaterialShapes.ExtraLarge, if (reduceMotion) snap() else MaterialMotion.press(), label = "groupCorner")
    Row(
        Modifier
            .weight(weight)
            .then(if (compact) Modifier.fillMaxHeight() else Modifier.heightIn(min = 48.dp))
            .clip(RoundedCornerShape(corner))
            .background(container)
            .clickable(interaction, indication = MaterialRipple, role = Role.Button, onClick = onClick)
            .then(if (compact) Modifier else Modifier.padding(horizontal = 16.dp, vertical = 12.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, contentDescription = null, size = 20.dp, tint = content)
        Spacer(Modifier.width(6.dp))
        AppText(label, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold), color = content,
            maxLines = if (compact) 1 else Int.MAX_VALUE)
    }
}

/** Material assist chip: outlined, slightly rounded, label in the secondary colour. */
@Composable
internal fun MaterialTag(text: String, modifier: Modifier) {
    Box(
        modifier
            .heightIn(min = 28.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, AppTheme.colors.label.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        AppText(text, AppTheme.typography.caption1.copy(fontWeight = FontWeight.SemiBold), color = AppTheme.colors.secondaryLabel, maxLines = 1)
    }
}

/** One line, one chip height: a long label scrolls the row instead of growing just that chip. */
@Composable
internal fun MaterialSortOptions(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val shape = RoundedCornerShape(MaterialShapes.Medium)
    val scroll = rememberScrollState()
    Row(modifier.fillMaxWidth().horizontalScroll(scroll).selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        options.forEachIndexed { index, option ->
            val selected = selectedIndex == index
            val interaction = remember { MutableInteractionSource() }
            Box(Modifier.heightIn(min = 48.dp)
                .selectable(selected, interaction, indication = null, role = Role.RadioButton) { onSelect(index) }
                .padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.heightIn(min = 40.dp).clip(shape)
                    .background(if (selected) palette.primaryContainer else palette.container)
                    .indication(interaction, MaterialRipple)
                    .padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    AppText(option, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) palette.onPrimaryContainer else palette.label, maxLines = 1)
                }
            }
        }
    }
}

/** Material filter chips: compact tonal surfaces inside full-height selectable targets. */
@Composable
internal fun MaterialFilterChips(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    LazyRow(modifier.selectableGroup(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { index, option ->
            val selected = index == selectedIndex
            val interaction = remember { MutableInteractionSource() }
            val fill by animateColorAsState(if (selected) palette.primaryContainer else palette.container,
                if (reduceMotion) snap() else MaterialMotion.effects(), label = "chipFill")
            val shape = RoundedCornerShape(MaterialShapes.Medium)
            Box(
                Modifier.heightIn(min = 48.dp).widthIn(min = 48.dp)
                    .selectable(selected, interaction, indication = null, role = Role.Tab) { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.heightIn(min = 32.dp)
                        .clip(shape)
                        .background(fill)
                        .then(if (selected) Modifier else Modifier.border(1.dp, palette.secondaryLabel.copy(alpha = 0.35f), shape))
                        .indication(interaction, MaterialRipple)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (selected) {
                        AppIcon(AppIcons.Checkmark, contentDescription = null, size = 18.dp, tint = palette.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                    }
                    AppText(option, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        color = if (selected) palette.onPrimaryContainer else palette.label, maxLines = 1)
                }
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

/** Tonal outlined search field; its minimum height leaves room for larger text. */
@Composable
internal fun MaterialSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) {
    val palette = currentPalette()
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline by animateColorAsState(if (focused) palette.primary else palette.secondaryLabel.copy(alpha = 0.4f),
        if (reduceMotion) snap() else MaterialMotion.effects(), label = "searchOutline")
    val shape = RoundedCornerShape(MaterialShapes.LargeIncreased)
    Row(
        modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(palette.containerHigh)
            .border(if (focused) 2.dp else 1.dp, outline, shape)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Search, contentDescription = null, size = 24.dp, tint = if (focused) palette.primary else colors.secondaryLabel)
        Spacer(Modifier.width(16.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = AppTheme.typography.body.copy(color = colors.label),
            cursorBrush = SolidColor(palette.primary),
            interactionSource = interaction,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                .semantics { contentDescription = placeholder },
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) AppText(placeholder, AppTheme.typography.body,
                        color = colors.secondaryLabel, maxLines = 1,
                        modifier = Modifier.clearAndSetSemantics { })
                    innerTextField()
                }
            },
        )
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
    val interaction = remember { MutableInteractionSource() }
    val corner by animateDpAsState(if (selected) 14.dp else 24.dp, if (reduceMotion) snap() else MaterialMotion.spatial(), label = "iconButtonShape")
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
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                if (selected) this.selected = true
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(container)
                .indication(interaction, MaterialCenteredRipple),
            contentAlignment = Alignment.Center,
        ) {
            AppIcon(icon, contentDescription = null, size = 24.dp, tint = tint)
        }
    }
}
