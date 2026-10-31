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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
import kotlin.math.roundToInt

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
    titleMaxLines: Int = 2,
    fullText: Boolean = false,
) {
    val colors = AppTheme.colors
    val inset = LocalGlassRowHorizontalInset.current ?: LocalGlassLook.current.contentHorizontalInset
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
            .liquidFocusIndicator(interaction, enabled = onClick != null)
            .then(if (onClick != null) Modifier.clickable(interaction, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(start = inset),
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
                    drawLine(colors.label.copy(alpha = if (colors.isDark) 0.14f else 0.10f), Offset(0f, y), Offset(size.width, y), strokeWidth = 0.5.dp.toPx())
                }
                .padding(end = inset, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                val content = LocalContentColor.current
                AppText(title, AppTheme.typography.body, color = if (highlighted) colors.accent else content,
                    maxLines = if (fullText) Int.MAX_VALUE else titleMaxLines)
                if (subtitle != null) {
                    AppText(subtitle, AppTheme.typography.subheadline, color = colors.secondaryLabel,
                        modifier = Modifier.padding(top = 2.dp), maxLines = if (fullText) Int.MAX_VALUE else 1)
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
    val labelStyle = AppTheme.typography.footnote.copy(fontWeight = FontWeight.Medium)
    val selectedLabelStyle = labelStyle.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val horizontalPadding = 12.dp
    // Reserve both font states so changing selection never truncates or resizes a label.
    val widestLabelPx = options.maxOfOrNull { option ->
        maxOf(
            measurer.measure(option, labelStyle, softWrap = false, maxLines = 1).size.width,
            measurer.measure(option, selectedLabelStyle, softWrap = false, maxLines = 1).size.width,
        )
    } ?: 0
    val minimumSegmentWidthPx = widestLabelPx + with(density) { horizontalPadding.roundToPx() * 2 }
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { maxOf(36.dp, AppTheme.typography.footnote.lineHeight.toDp() + 18.dp) })
            .let { LocalGlassLook.current.controlSurface(it, CircleShape, colors.fill, prominent = false) }
            .padding(2.dp),
    ) {
        if (options.isNotEmpty()) {
            val viewportWidthPx = constraints.maxWidth
            val rowWidthPx = maxOf(viewportWidthPx, minimumSegmentWidthPx * options.size)
            val rowWidth = with(density) { rowWidthPx.toDp() }
            val segmentWidth = rowWidth / options.size
            val scroll = rememberScrollState()
            LaunchedEffect(selectedIndex, options.size, rowWidthPx, viewportWidthPx, scroll.maxValue) {
                if (selectedIndex in options.indices) {
                    // Scroll values and relative placement both run from logical start in RTL.
                    val start = (rowWidthPx.toFloat() * selectedIndex / options.size).roundToInt()
                    val end = (rowWidthPx.toFloat() * (selectedIndex + 1) / options.size).roundToInt()
                    val target = when {
                        end - start > viewportWidthPx -> start
                        start < scroll.value -> start
                        end > scroll.value + viewportWidthPx -> end - viewportWidthPx
                        else -> scroll.value
                    }
                    scroll.scrollTo(target.coerceAtLeast(0))
                }
            }
            val indicatorOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex.coerceIn(options.indices),
                animationSpec = if (reduceMotion) snap() else spring(dampingRatio = 1f, stiffness = 500f),
                label = "segmentSelection",
            )
            Box(Modifier.fillMaxSize().horizontalScroll(scroll)) {
                // The lens and labels share one content space, including while scrolling.
                Box(Modifier.width(rowWidth).fillMaxHeight()) {
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
                            val interaction = remember { MutableInteractionSource() }
                            val pressed by interaction.collectIsPressedAsState()
                            val pressedColor by animateColorAsState(
                                targetValue = if (pressed) colors.fill else Color.Transparent,
                                animationSpec = if (reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
                                label = "segmentPress",
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(pressedColor)
                                    .liquidFocusIndicator(interaction, CircleShape)
                                    .clickable(interaction, indication = null) { onSelect(index) }
                                    .semantics { role = Role.Tab; this.selected = selected }
                                    .padding(horizontal = horizontalPadding),
                                contentAlignment = Alignment.Center,
                            ) {
                                AppText(
                                    text = option,
                                    style = if (selected) selectedLabelStyle else labelStyle,
                                    color = colors.label,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun LiquidGlassActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier, enabled: Boolean) {
    val colors = AppTheme.colors
    val foreground = if (enabled) colors.accent else colors.tertiaryLabel
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressedAlpha by animateFloatAsState(
        targetValue = if (enabled && pressed) 0.6f else 1f,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "actionButtonPress",
    )
    Row(
        modifier = modifier
            .heightIn(min = 48.dp)
            .liquidFocusIndicator(interaction, RoundedCornerShape(percent = 50), enabled = enabled)
            .graphicsLayer { alpha = pressedAlpha }
            .let { LocalGlassLook.current.controlSurface(it, RoundedCornerShape(percent = 50), colors.fill, prominent = false) }
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, size = 18.dp, tint = foreground)
            Spacer(Modifier.width(8.dp))
        }
        AppText(text, AppTheme.typography.headline, color = foreground, maxLines = 1)
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
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val minimumHeight = with(LocalDensity.current) { maxOf(44.dp, AppTheme.typography.body.lineHeight.toDp() + 20.dp) }
    BasicTextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = AppTheme.typography.body.copy(color = colors.label),
        cursorBrush = SolidColor(colors.accent),
        modifier = modifier.fillMaxWidth().heightIn(min = minimumHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) colors.accent.copy(alpha = 0.08f) else colors.fill)
            .semantics { contentDescription = placeholder },
        decorationBox = { innerTextField ->
            Row(Modifier.fillMaxWidth().heightIn(min = minimumHeight).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.Search, null, size = 18.dp, tint = if (focused) colors.accent else colors.secondaryLabel)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f).padding(vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) AppText(placeholder, AppTheme.typography.body, color = colors.secondaryLabel,
                        maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
                    innerTextField()
                }
                if (query.isNotEmpty()) {
                    val clearInteraction = remember { MutableInteractionSource() }
                    Box(
                        Modifier.size(44.dp).liquidFocusIndicator(clearInteraction, CircleShape)
                            .clickable(clearInteraction, indication = null,
                                role = Role.Button, onClickLabel = clearLabel) { onQueryChange("") }
                            .semantics { contentDescription = clearLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(18.dp).clip(CircleShape).background(colors.secondaryLabel.copy(alpha = 0.6f)),
                            contentAlignment = Alignment.Center) {
                            AppIcon(AppIcons.Close, null, size = 12.dp, tint = colors.background)
                        }
                    }
                } else Spacer(Modifier.width(12.dp))
            }
        },
    )
}

/** iOS back link: chevron plus the previous page's title in the accent colour, 44 dp tall. */
@Composable
internal fun LiquidGlassBackButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val iconScale by animateFloatAsState(if (pressed && !reduceMotion) 0.9f else 1f,
        if (reduceMotion) snap() else spring(dampingRatio = 0.7f, stiffness = 700f), label = "backIconPress")
    val pressedColor by animateColorAsState(
        targetValue = if (pressed) AppTheme.colors.fill else Color.Transparent,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "backButtonPress",
    )
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(pressedColor)
            .liquidFocusIndicator(interaction, shape)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = 8.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(AppIcons.ChevronLeft, contentDescription = null, size = 26.dp, tint = AppTheme.colors.accent,
            modifier = Modifier.graphicsLayer { scaleX = iconScale; scaleY = iconScale })
        AppText(label, AppTheme.typography.body, color = AppTheme.colors.accent, maxLines = 1)
    }
}
