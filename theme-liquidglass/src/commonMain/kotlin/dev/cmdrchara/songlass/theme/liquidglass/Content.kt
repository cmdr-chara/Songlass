package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.GenerativeCover
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkImage
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

// Content-layer components: standard surfaces, no Liquid Glass (HIG: "Don't use Liquid Glass
// in the content layer").

@Composable
internal fun LiquidGlassListRow(
    title: String,
    subtitle: String?,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    highlighted: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedColor by animateColorAsState(
        targetValue = if (pressed) colors.fill else Color.Transparent,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "listRowPress",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(pressedColor)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, onClick = onClick) else Modifier)
            .padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = if (subtitle != null) 60.dp else 44.dp)
                .drawBehind {
                    val y = size.height - 0.5.dp.toPx() / 2
                    drawLine(colors.separator, Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                }
                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val content = LocalContentColor.current
                AppText(title, AppTheme.typography.body, color = if (highlighted) colors.accent else content, maxLines = 1)
                if (subtitle != null) {
                    AppText(subtitle, AppTheme.typography.subheadline, color = content.copy(alpha = 0.6f), maxLines = 1)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

@Composable
internal fun LiquidGlassSegmentedControl(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val selectedBackground = if (colors.isDark) Color(0xFF636366) else Color.White
    val reduceMotion = AppTheme.accessibility.reduceMotion
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .let { LocalGlassLook.current.controlSurface(it, CircleShape, colors.fill, prominent = false) }
            .padding(2.dp),
    ) {
        if (options.isNotEmpty()) {
            val segmentWidth = maxWidth / options.size
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex.coerceIn(options.indices),
                animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 500f),
                label = "segmentSelection",
            )
            if (selectedIndex in options.indices) {
                Box(
                    Modifier.offset(x = indicatorOffset)
                        .width(segmentWidth)
                        .fillMaxHeight()
                        .shadow(2.dp, CircleShape)
                        .background(selectedBackground, CircleShape),
                )
            }
            Row(Modifier.fillMaxSize().selectableGroup()) {
                options.forEachIndexed { index, option ->
                    val selected = index == selectedIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(remember { MutableInteractionSource() }, indication = null) { onSelect(index) }
                            .semantics { role = Role.Tab; this.selected = selected },
                        contentAlignment = Alignment.Center,
                    ) {
                        AppText(
                            text = option,
                            style = AppTheme.typography.footnote.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium),
                            color = colors.label,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiquidGlassActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedAlpha by animateFloatAsState(
        targetValue = if (pressed) 0.6f else 1f,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "actionButtonPress",
    )
    Row(
        modifier = modifier
            .height(48.dp)
            .graphicsLayer { alpha = pressedAlpha }
            .let { LocalGlassLook.current.controlSurface(it, RoundedCornerShape(percent = 50), colors.fill, prominent = false) }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, size = 18.dp, tint = colors.accent)
            Spacer(Modifier.width(8.dp))
        }
        AppText(text, AppTheme.typography.headline, color = colors.accent, maxLines = 1)
    }
}

/** Cover art: the real image when available, otherwise a gradient with a faint note. */
@Composable
internal fun LiquidGlassAlbumArt(artwork: Artwork, cornerRadius: Dp, modifier: Modifier) {
    val colors = artwork.colors.ifEmpty { listOf(Color.Gray, Color.DarkGray) }
    BoxWithConstraints(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(if (colors.size == 1) Brush.linearGradient(colors + colors) else Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        val sizePx = with(LocalDensity.current) { maxWidth.roundToPx() }
        val image = rememberArtworkImage(artwork, sizePx)
        if (image != null) {
            Image(bitmap = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            GenerativeCover(artwork, Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun LiquidGlassSearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, clearLabel: String, modifier: Modifier) {
    val colors = AppTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .let { LocalGlassLook.current.controlSurface(it, RoundedCornerShape(LocalGlassLook.current.corner(12.dp)), colors.fill, prominent = false) }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.Search, contentDescription = null, size = 18.dp, tint = colors.secondaryLabel)
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                AppText(placeholder, AppTheme.typography.body, color = colors.secondaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = AppTheme.typography.body.copy(color = colors.label),
                cursorBrush = SolidColor(colors.accent),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            // iOS clear button: a small grey circle with a cross.
            Box(
                Modifier.size(36.dp).clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClickLabel = clearLabel) { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(colors.secondaryLabel.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
                    AppIcon(AppIcons.Close, contentDescription = clearLabel, size = 12.dp, tint = colors.background)
                }
            }
        }
    }
}

/** iOS back link: chevron plus the previous page's title in the accent colour, 44 dp tall. */
@Composable
internal fun LiquidGlassBackButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.ChevronLeft, contentDescription = null, size = 26.dp, tint = AppTheme.colors.accent)
        AppText(label, AppTheme.typography.body, color = AppTheme.colors.accent, maxLines = 1)
    }
}
