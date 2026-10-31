package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.theme.liquidglass.miniPlayerTitleMotion
import dev.cmdrchara.songlass.theme.liquidglass.MiniPlayerProgress
import dev.cmdrchara.songlass.theme.liquidglass.miniPlayerFrame
import dev.cmdrchara.songlass.theme.liquidglass.miniPlayerCover
import dev.cmdrchara.songlass.theme.liquidglass.trackSwipe
import dev.cmdrchara.songlass.theme.liquidglass.followTrackSwipe
import dev.cmdrchara.songlass.theme.liquidglass.trackSwipeActions
import dev.cmdrchara.songlass.theme.liquidglass.rememberTrackSwipe
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button as NativeButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card as NativeCard
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.IconButton as NativeIconButton
import top.yukonga.miuix.kmp.basic.RadioButton as NativeRadioButton
import top.yukonga.miuix.kmp.basic.Slider as NativeSlider
import top.yukonga.miuix.kmp.basic.Switch as NativeSwitch
import top.yukonga.miuix.kmp.basic.TextField as NativeField
import top.yukonga.miuix.kmp.basic.TextFieldDefaults as NativeFieldDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme as NativeMiuixTheme

@Composable
internal fun MiuixAction(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, primary: Boolean = false, enabled: Boolean = true, maxLines: Int = Int.MAX_VALUE, accessibilityLabel: String? = null) {
    val palette = AppTheme.colors
    val background = if (primary) palette.accent else palette.fill
    val foreground = if (!enabled) palette.tertiaryLabel else if (primary) if (background.luminance() > 0.179f) Color.Black else Color.White else palette.label
    NativeButton(onClick = onClick, modifier = modifier.heightIn(min = 48.dp), cornerRadius = 18.dp, enabled = enabled,
        colors = ButtonDefaults.buttonColors(color = background, contentColor = foreground)) {
        icon?.let { AppIcon(it, null, size = 20.dp, tint = foreground); Spacer(Modifier.width(8.dp)) }
        AppText(text, AppTheme.typography.headline, color = foreground, maxLines = maxLines, textAlign = TextAlign.Center,
            modifier = if (accessibilityLabel == null) Modifier else Modifier.clearAndSetSemantics { contentDescription = accessibilityLabel })
    }
}

@Composable
internal fun MiuixGroup(content: @Composable () -> Unit, modifier: Modifier) {
    NativeCard(modifier.fillMaxWidth(), cornerRadius = 24.dp, insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) { content() }
}

/** Native ordering capsules keep one line and one height; a long label never resizes its row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MiuixSortOptions(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    FlowRow(modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, option ->
            MiuixAction(option, null, { onSelect(index) },
                Modifier.semantics { selected = index == selectedIndex },
                primary = index == selectedIndex, maxLines = 1)
        }
    }
}

@Composable
internal fun MiuixRow(title: String, subtitle: String?, leading: (@Composable () -> Unit)?, trailing: (@Composable () -> Unit)?, highlighted: Boolean, onClick: (() -> Unit)?, modifier: Modifier, titleMaxLines: Int = 2, fullText: Boolean = false) {
    BasicComponent(
        modifier = modifier.fillMaxWidth().then(if (highlighted) Modifier.background(AppTheme.colors.accent.copy(alpha = 0.10f)) else Modifier),
        startAction = leading?.let { { Box(Modifier.padding(end = 14.dp)) { it() } } },
        endActions = trailing?.let { { Box(Modifier.padding(start = 12.dp)) { it() } } },
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp), onClick = onClick,
    ) {
        AppText(title, AppTheme.typography.body, color = if (highlighted) AppTheme.colors.accent else AppTheme.colors.label,
            maxLines = if (fullText) Int.MAX_VALUE else titleMaxLines)
        subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel,
            maxLines = if (fullText) Int.MAX_VALUE else 2) }
    }
}

@Composable
internal fun MiuixChoice(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    MiuixRow(title, null, null, { NativeRadioButton(selected = selected, onClick = null) }, false, onClick,
        modifier.semantics { this.selected = selected; role = Role.RadioButton }, titleMaxLines = Int.MAX_VALUE)
}

@Composable
internal fun MiuixToggle(title: String, subtitle: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean, modifier: Modifier) {
    BasicComponent(modifier = modifier.fillMaxWidth().semantics { role = Role.Switch; toggleableState = if (checked) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off },
        enabled = enabled, onClick = if (enabled) ({ onCheckedChange(!checked) }) else null,
        // The labeled row owns the switch action; the native thumb only renders its state.
        endActions = { NativeSwitch(checked, onCheckedChange = null,
            modifier = Modifier.clearAndSetSemantics {}, enabled = enabled) },
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
    ) {
        AppText(title, AppTheme.typography.body, color = AppTheme.colors.label.copy(alpha = if (enabled) 1f else 0.45f))
        subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel) }
    }
}

@Composable
internal fun MiuixSegments(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit, modifier: Modifier) {
    MiuixContourSegments(options, selectedIndex, onSelect, modifier)

}

@Composable
internal fun MiuixIconAction(icon: ImageVector, label: String, onClick: () -> Unit, selected: Boolean, filled: Boolean, modifier: Modifier) {
    NativeIconButton(onClick, modifier.semantics { contentDescription = label; if (selected) this.selected = true },
        backgroundColor = if (selected) AppTheme.colors.accent.copy(alpha = 0.16f) else if (filled) AppTheme.colors.fill else Color.Transparent,
        cornerRadius = 16.dp, minHeight = 48.dp, minWidth = 48.dp) {
        AppIcon(icon, null, size = 24.dp, tint = if (selected) AppTheme.colors.accent else AppTheme.colors.label)
    }
}

@Composable
internal fun MiuixBack(label: String, onClick: () -> Unit, modifier: Modifier) = MiuixIconAction(AppIcons.ArrowBack, label, onClick, false, true, modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))

/** Preserve native chrome/states; enabled labels need readable roles on both field surfaces. */
@Composable
internal fun readableMiuixFieldColors(enabled: Boolean = true): top.yukonga.miuix.kmp.basic.TextFieldColors {
    val palette = AppTheme.colors
    val nativeScheme = NativeMiuixTheme.colorScheme
    val defaults = NativeFieldDefaults.textFieldColors()
    return if (enabled) {
        val label = if (palette.isDark) nativeScheme.onSurface else nativeScheme.onSurfaceVariantSummary
        defaults.copy(labelColor = label)
    } else defaults
}

@Composable
internal fun MiuixField(
    value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, modifier: Modifier,
    keyboardOptions: androidx.compose.foundation.text.KeyboardOptions = androidx.compose.foundation.text.KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions = androidx.compose.foundation.text.KeyboardActions.Default,
) {
    val colors = readableMiuixFieldColors(enabled)
    NativeField(value, onValueChange, modifier.fillMaxWidth().padding(vertical = 6.dp).semantics { contentDescription = label }, label = label, cornerRadius = 18.dp, singleLine = true, enabled = enabled,
        colors = colors, keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
}

@Composable
internal fun MiuixVolume(value: Float, onValueChange: (Float) -> Unit, label: String, modifier: Modifier) {
    NativeSlider(value.coerceIn(0f, 1f), onValueChange, modifier.fillMaxWidth().heightIn(min = 44.dp).semantics { contentDescription = label }, height = 26.dp)
}

@Composable
internal fun MiuixSeek(positionMs: Long, durationMs: Long, quality: String?, onSeek: (Long) -> Unit, modifier: Modifier) {
    val duration = durationMs.coerceAtLeast(0)
    val currentOnSeek by rememberUpdatedState(onSeek)
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(duration) { dragging = null }
    val fraction = dragging ?: if (duration == 0L) 0f else (positionMs.toDouble() / duration).toFloat().coerceIn(0f, 1f)
    val shownPosition = (fraction.toDouble() * duration).toLong().coerceIn(0L, duration)
    val timeStyle = AppTheme.typography.caption1.copy(fontFeatureSettings = "tnum")
    val secondary = AppTheme.colors.secondaryLabel
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val qualityText = quality?.takeIf { it.isNotBlank() }
    val elapsedText = time(shownPosition)
    val remainingText = "−" + time(duration - shownPosition)
    val textWidths = remember(elapsedText, remainingText, qualityText, timeStyle, textMeasurer, density) {
        val time = maxOf(textMeasurer.measure(elapsedText, timeStyle).size.width,
            textMeasurer.measure(remainingText, timeStyle).size.width)
        val qualityWidth = textMeasurer.measure(qualityText.orEmpty(), timeStyle).size.width
        with(density) { maxOf(48.dp, time.toDp()) to qualityWidth.toDp() }
    }
    Column(modifier.fillMaxWidth()) {
        NativeSlider(fraction, { value ->
            if (duration > 0L && value.isFinite()) dragging = value.coerceIn(0f, 1f)
        }, Modifier.fillMaxWidth().heightIn(min = 44.dp),
            enabled = duration > 0, onValueChangeFinished = {
                dragging?.let { value ->
                    if (duration > 0L) currentOnSeek((value.toDouble() * duration).toLong().coerceIn(0L, duration))
                }
                dragging = null
            }, height = 10.dp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val timeWidth = textWidths.first.coerceAtMost(maxWidth / 2)
            val stacked = qualityText != null &&
                (density.fontScale > 1.3f || timeWidth * 2 + textWidths.second + 16.dp > maxWidth)
            Column {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppText(elapsedText, timeStyle, Modifier.width(timeWidth), color = secondary, maxLines = 1)
                    if (qualityText == null || stacked) Spacer(Modifier.weight(1f))
                    else AppText(qualityText, timeStyle, Modifier.weight(1f).padding(horizontal = 8.dp),
                        color = secondary, textAlign = TextAlign.Center, maxLines = 1)
                    AppText(remainingText, timeStyle, Modifier.width(timeWidth),
                        color = secondary, textAlign = TextAlign.End, maxLines = 1)
                }
                if (stacked) AppText(qualityText.orEmpty(), timeStyle, Modifier.fillMaxWidth().padding(top = 4.dp),
                    color = secondary, textAlign = TextAlign.Center)
            }
        }
    }
}
private fun time(ms: Long): String = (ms.coerceAtLeast(0) / 60_000).toString() + ":" + ((ms.coerceAtLeast(0) / 1000) % 60).toString().padStart(2, '0')

@Composable
internal fun MiuixTransport(playing: Boolean, labels: PlaybackLabels, previous: () -> Unit, playPause: () -> Unit, next: () -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val clusterWidth = minOf(196.dp, maxWidth)
        val gap = ((clusterWidth - 176.dp) / 2).coerceIn(0.dp, 4.dp)
        val buttonsWidth = clusterWidth - gap * 2
        val sideWidth = (buttonsWidth * (52f / 188f)).coerceAtLeast(48.dp)
        val playWidth = (buttonsWidth - sideWidth * 2).coerceAtLeast(48.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
            MiuixIconAction(AppIcons.Previous, labels.previous, previous, false, false, Modifier.size(sideWidth))
            NativeButton(playPause, Modifier.size(width = playWidth, height = 60.dp).semantics { contentDescription = if (playing) labels.pause else labels.play }, cornerRadius = 24.dp,
                colors = ButtonDefaults.buttonColors(color = AppTheme.colors.accent, contentColor = if (AppTheme.colors.accent.luminance() > 0.179f) Color.Black else Color.White)) {
                AppIcon(if (playing) AppIcons.Pause else AppIcons.Play, null, size = 28.dp, tint = if (AppTheme.colors.accent.luminance() > 0.179f) Color.Black else Color.White)
            }
            MiuixIconAction(AppIcons.Next, labels.next, next, false, false, Modifier.size(sideWidth))
        }
    }
}

@Composable
internal fun MiuixMini(state: MiniPlayerState, labels: PlaybackLabels, onClick: () -> Unit, playPause: () -> Unit, next: () -> Unit, previous: () -> Unit, modifier: Modifier) {
    val swipe = rememberTrackSwipe()
    val surface = MiuixLook.navigationSurface(modifier.fillMaxWidth().heightIn(min = 68.dp).miniPlayerFrame(), RoundedCornerShape(24.dp), null, true)
    Row(surface.trackSwipe(swipe, next, previous).trackSwipeActions(labels, next, previous).clickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).followTrackSwipe(swipe), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).miniPlayerCover()) { AlbumArt(state.artwork, Modifier.fillMaxSize(), cornerRadius = 16.dp) }
            Column(Modifier.padding(start = 10.dp, end = 6.dp).weight(1f)) {
                AppText(state.title, AppTheme.typography.subheadline.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                    modifier = Modifier.miniPlayerTitleMotion(), maxLines = 1)
                AppText(state.subtitle, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel, maxLines = 1)
                Spacer(Modifier.height(3.dp))
                MiniPlayerProgress(state.progress)
            }
        }
        MiuixIconAction(if (state.isPlaying) AppIcons.Pause else AppIcons.Play, if (state.isPlaying) labels.pause else labels.play, playPause, false, true, Modifier)
        MiuixIconAction(AppIcons.Next, labels.next, next, false, false, Modifier)
    }
}

@Composable
internal fun MiuixTag(text: String, modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.fill).padding(horizontal = 10.dp, vertical = 5.dp)) {
        AppText(text, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel)
    }
}

/** Disclosure and exclusion remain separate, accessible hit targets. */
@Composable
internal fun MiuixFolder(choice: FolderChoice, onExpand: () -> Unit, onExcludedChange: (Boolean) -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).then(if (choice.expanded != null) Modifier.clickable(role = Role.Button, onClick = onExpand)
            .semantics { contentDescription = choice.expandLabel; stateDescription = choice.subtitle } else Modifier)
            .heightIn(min = 68.dp).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Folder, null, size = 24.dp, tint = AppTheme.colors.accent)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                AppText(choice.title, AppTheme.typography.body, maxLines = 2)
                AppText(choice.subtitle, AppTheme.typography.caption1, color = AppTheme.colors.secondaryLabel)
            }
            if (choice.expanded != null) AppIcon(if (choice.expanded == true) AppIcons.ChevronDown else AppIcons.ChevronRight,
                null, size = 16.dp, tint = AppTheme.colors.secondaryLabel)
        }
        Box(Modifier.size(48.dp).toggleable(choice.excluded, enabled = choice.enabled, role = Role.Checkbox, onValueChange = onExcludedChange)
            .semantics { contentDescription = choice.excludeLabel }, contentAlignment = Alignment.Center) {
            top.yukonga.miuix.kmp.basic.Checkbox(
                state = if (choice.excluded) androidx.compose.ui.state.ToggleableState.On else androidx.compose.ui.state.ToggleableState.Off,
                onClick = null, enabled = choice.enabled, modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** A single row for title and navigation; no empty expanded-title band. */
@Composable
internal fun MiuixHeading(text: String, backLabel: String? = null, onBack: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, modifier: Modifier = Modifier, maxTitleLines: Int = 2) {
    Row(modifier.fillMaxWidth().background(AppTheme.colors.background)
        .windowInsetsPadding(WindowInsets.statusBars).heightIn(min = 64.dp)
        .padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) MiuixIconAction(AppIcons.ArrowBack, backLabel.orEmpty(), onBack,
            selected = false, filled = true, modifier = Modifier.size(48.dp))
        AppText(text, if (onBack == null) AppTheme.typography.largeTitle
            else AppTheme.typography.largeTitle.copy(fontSize = 26.sp, lineHeight = 32.sp),
            modifier = Modifier.weight(1f).semantics { heading() }, maxLines = maxTitleLines)
        trailing?.invoke()
    }
}
