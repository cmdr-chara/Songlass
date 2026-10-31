package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalSheetTint
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor

private val SheetShape = RoundedCornerShape(28.dp)

/**
 * iOS-style action sheet: a dimmed backdrop, a card with a header and the actions, and a
 * separate Cancel button. Tapping outside cancels.
 */
@Composable
internal fun LiquidGlassActionSheet(
    visible: Boolean,
    title: String,
    subtitle: String?,
    artwork: Artwork?,
    actions: List<SheetAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    details: List<InfoRow> = emptyList(),
    field: (@Composable () -> Unit)? = null,
    contentKey: String? = null,
    dismissOnAction: Boolean = true,
    headerLeading: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    onBackPress: (() -> Unit)? = null,
) {
    val sheetTint = LocalSheetTint.current
    if (sheetTint == null) {
        ActionSheetContent(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss, details, tinted = false, field = field, contentKey = contentKey, dismissOnAction = dismissOnAction, headerLeading = headerLeading, headerTrailing = headerTrailing, onBackPress = onBackPress)
    } else {
        ProvideArtworkTint(sheetTint) {
            ActionSheetContent(visible, title, subtitle, artwork, actions, cancelLabel, onDismiss, details, tinted = true, field = field, contentKey = contentKey, dismissOnAction = dismissOnAction, headerLeading = headerLeading, headerTrailing = headerTrailing, onBackPress = onBackPress)
        }
    }
}

/** Asks for a short text (a playlist name): a field above Confirm, in the action sheet's card. */
@Composable
internal fun LiquidGlassPromptSheet(
    visible: Boolean,
    title: String,
    initialText: String,
    placeholder: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember(visible, initialText) { mutableStateOf(initialText) }
    val focus = remember { FocusRequester() }
    val colors = AppTheme.colors
    fun confirm() {
        if (visible && text.isNotBlank()) {
            // The request and its restore ticket must still be live during confirmation.
            onConfirm(text.trim())
            onDismiss()
        }
    }
    LiquidGlassActionSheet(
        visible = visible,
        title = title,
        subtitle = null,
        artwork = null,
        actions = listOf(SheetAction(confirmLabel, null) { confirm() }),
        cancelLabel = cancelLabel,
        onDismiss = onDismiss,
        dismissOnAction = false,
        field = {
            Box(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(12.dp)).background(colors.fill).padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                if (text.isEmpty()) AppText(placeholder, AppTheme.typography.body, color = colors.secondaryLabel, maxLines = 1)
                BasicTextField(
                    value = text,
                    onValueChange = { text = it.take(80) },
                    singleLine = true,
                    textStyle = AppTheme.typography.body.copy(color = colors.label),
                    cursorBrush = SolidColor(colors.accent),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { confirm() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
            LaunchedEffect(visible) { if (visible) runCatching { focus.requestFocus() } }
        },
    )
}

@Composable
private fun ActionSheetContent(
    visible: Boolean,
    title: String,
    subtitle: String?,
    artwork: Artwork?,
    actions: List<SheetAction>,
    cancelLabel: String,
    onDismiss: () -> Unit,
    details: List<InfoRow>,
    tinted: Boolean,
    field: (@Composable () -> Unit)? = null,
    contentKey: String? = null,
    dismissOnAction: Boolean = true,
    headerLeading: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    onBackPress: (() -> Unit)? = null,
) {
    val scroll = rememberScrollState()
    LaunchedEffect(contentKey, title, visible, actions.firstOrNull()?.isBackAction) {
        if (visible) scroll.scrollTo(0)
    }
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val look = LocalGlassLook.current
    val bottomSheet = look.bottomSheets
    // In a tinted context the card is a slightly raised shade of the same hue; otherwise the
    // neutral system surface (a tonal container for bottom sheets).
    val surface = when {
        tinted -> lerp(colors.background, colors.label, if (colors.isDark) 0.1f else 0.05f)
        bottomSheet -> colors.secondaryBackground
        colors.isDark -> Color(0xFF2C2C2E)
        else -> Color.White
    }
    // The host may first enter composition with visible=true (the first track menu).
    // Start hidden explicitly so that opening always has the same entrance animation.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val transition = rememberTransition(visibility, label = "actionSheet")
    val fadeDuration = if (reduceMotion) LiquidGlassMotion.ReducedMillis else LiquidGlassMotion.FadeMillis

    if (!visibility.isIdle || visibility.currentState || visibility.targetState) SheetWindowHost(
        navigationLightIcons = colors.isDark,
        onDismiss = {
            if (visible) {
                val back = onBackPress ?: actions.firstOrNull { it.isBackAction }?.onClick
                if (back != null) back() else onDismiss()
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val cardMaxHeight = maxHeight * 0.78f
            transition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(tween(fadeDuration)),
                exit = fadeOut(tween(if (reduceMotion) fadeDuration else LiquidGlassMotion.NavigationMillis)),
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = visible, onClick = onDismiss)
                        .semantics { onClick(label = cancelLabel) { if (visible) { onDismiss(); true } else false } },
                ) {
                    if (!bottomSheet) {
                        Spacer(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                            .background(surface.copy(alpha = 1f)))
                    }
                }
            }
            transition.AnimatedVisibility(
                visible = { it },
                enter = if (reduceMotion) {
                    fadeIn(tween(fadeDuration))
                } else {
                    slideInVertically(tween(LiquidGlassMotion.SheetMillis, easing = LiquidGlassMotion.Emphasized)) { it } +
                        fadeIn(tween(fadeDuration))
                },
                exit = if (reduceMotion) {
                    fadeOut(tween(fadeDuration))
                } else {
                    slideOutVertically(tween(LiquidGlassMotion.NavigationMillis, easing = LiquidGlassMotion.Emphasized)) { it } +
                        fadeOut(tween(LiquidGlassMotion.NavigationMillis))
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                CompositionLocalProvider(LocalContentColor provides colors.label) {
                    if (bottomSheet) {
                        BottomSheetContent(visible, title, subtitle, artwork, actions, onDismiss, details, field, surface, cardMaxHeight, scroll, dismissOnAction, headerLeading, headerTrailing)
                        return@CompositionLocalProvider
                    }
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.safeDrawing.union(WindowInsets.ime))
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        // Reserve the separate cancel action before bounding the scrollable card.
                        Column(Modifier.weight(1f, fill = false).fillMaxWidth().heightIn(max = cardMaxHeight).shadow(16.dp, SheetShape).clip(SheetShape).background(surface).verticalScroll(scroll)) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (artwork != null) {
                                    LiquidGlassAlbumArt(artwork, cornerRadius = 8.dp, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.width(12.dp))
                                }
                                Column {
                                    AppText(title, AppTheme.typography.headline,
                                        maxLines = if (artwork == null) Int.MAX_VALUE else 1)
                                    if (subtitle != null) {
                                        AppText(subtitle, AppTheme.typography.subheadline, color = colors.secondaryLabel,
                                            maxLines = if (artwork == null) Int.MAX_VALUE else 1)
                                    }
                                }
                            }
                            field?.invoke()
                            details.forEach { detail ->
                                Separator()
                                Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                                    AppText(detail.label, AppTheme.typography.caption1, color = colors.secondaryLabel)
                                    AppText(detail.value, AppTheme.typography.body)
                                }
                            }
                            if (actions.isNotEmpty()) Separator()
                            actions.forEachIndexed { index, action ->
                                if (index > 0) Separator()
                                SheetRow(action.label, action.icon) {
                                    if (visible) {
                                        if (dismissOnAction && action.dismissOnClick) onDismiss()
                                        action.onClick()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.fillMaxWidth().shadow(16.dp, SheetShape).clip(SheetShape).background(surface)) {
                            SheetRow(cancelLabel, icon = null, bold = true, centered = true, onClick = { if (visible) onDismiss() })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Separator() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(AppTheme.colors.separator))
}

@Composable
private fun SheetRow(label: String, icon: ImageVector?, bold: Boolean = false, centered: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        targetValue = if (pressed) AppTheme.colors.fill else Color.Transparent,
        animationSpec = if (AppTheme.accessibility.reduceMotion) snap() else tween(LiquidGlassMotion.FeedbackMillis),
        label = "sheetRowBackground",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .background(background)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = AppTheme.typography.body.let { if (bold) it.copy(fontWeight = FontWeight.SemiBold) else it }
        AppText(label, style, color = AppTheme.colors.accent, modifier = Modifier.weight(1f),
            textAlign = if (centered) TextAlign.Center else null)
        if (icon != null) AppIcon(icon, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent)
    }
}

private val BottomSheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Material bottom sheet: attached to the bottom edge with a drag handle, a header, then the actions
 * as rows with a leading icon. No Cancel button: tapping outside or Back closes it.
 */
@Composable
private fun BottomSheetContent(
    visible: Boolean,
    title: String,
    subtitle: String?,
    artwork: Artwork?,
    actions: List<SheetAction>,
    onDismiss: () -> Unit,
    details: List<InfoRow>,
    field: (@Composable () -> Unit)?,
    surface: Color,
    maxHeight: androidx.compose.ui.unit.Dp,
    scroll: androidx.compose.foundation.ScrollState,
    dismissOnAction: Boolean,
    headerLeading: (@Composable () -> Unit)?,
    headerTrailing: (@Composable () -> Unit)?,
) {
    val colors = AppTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(8.dp, BottomSheetShape)
            .clip(BottomSheetShape)
            .background(surface)
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
            .heightIn(max = maxHeight)
            .verticalScroll(scroll)
            .padding(bottom = 8.dp),
    ) {
        Box(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp, bottom = 6.dp)
                .size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(colors.label.copy(alpha = 0.4f)),
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            headerLeading?.invoke()
            if (artwork != null) {
                LiquidGlassAlbumArt(artwork, cornerRadius = 12.dp, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                AppText(title, AppTheme.typography.title3, maxLines = 2)
                if (subtitle != null) AppText(subtitle, AppTheme.typography.subheadline, color = colors.secondaryLabel, maxLines = 1)
            }
            headerTrailing?.invoke()
        }
        field?.invoke()
        details.forEach { detail ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                AppText(detail.label, AppTheme.typography.caption1, color = colors.secondaryLabel)
                AppText(detail.value, AppTheme.typography.body)
            }
        }
        if (actions.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp).height(1.dp).background(colors.separator))
        }
        actions.forEach { action ->
            BottomSheetRow(action.label, action.icon) {
                if (visible) {
                    if (dismissOnAction && action.dismissOnClick) onDismiss()
                    action.onClick()
                }
            }
        }
    }
}

@Composable
private fun BottomSheetRow(label: String, icon: ImageVector?, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(interaction, indication = LocalGlassLook.current.pressIndication(), role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            AppIcon(icon, contentDescription = null, size = 24.dp, tint = AppTheme.colors.secondaryLabel)
            Spacer(Modifier.width(16.dp))
        }
        AppText(label, AppTheme.typography.body, color = AppTheme.colors.label, modifier = Modifier.weight(1f), maxLines = 1)
    }
}
