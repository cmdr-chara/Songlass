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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.InfoRow
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
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
) {
    val colors = AppTheme.colors
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val surface = if (colors.isDark) Color(0xFF2C2C2E) else Color.White
    // The host may first enter composition with visible=true (the first track menu).
    // Start hidden explicitly so that opening always has the same entrance animation.
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val transition = rememberTransition(visibility, label = "actionSheet")
    val fadeDuration = if (reduceMotion) LiquidGlassMotion.ReducedMillis else LiquidGlassMotion.FadeMillis

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
                    .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
                    .semantics { onClick(label = cancelLabel) { onDismiss(); true } },
            )
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
                Column(
                    Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.fillMaxWidth().heightIn(max = cardMaxHeight).shadow(16.dp, SheetShape).clip(SheetShape).background(surface).verticalScroll(rememberScrollState())) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (artwork != null) {
                                LiquidGlassAlbumArt(artwork, cornerRadius = 8.dp, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.width(12.dp))
                            }
                            Column {
                                AppText(title, AppTheme.typography.headline, maxLines = 1)
                                if (subtitle != null) {
                                    AppText(subtitle, AppTheme.typography.subheadline, color = colors.secondaryLabel, maxLines = 1)
                                }
                            }
                        }
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
                                    onDismiss()
                                    action.onClick()
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().shadow(16.dp, SheetShape).clip(SheetShape).background(surface)) {
                        SheetRow(cancelLabel, icon = null, bold = true, onClick = onDismiss)
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
private fun SheetRow(label: String, icon: ImageVector?, bold: Boolean = false, onClick: () -> Unit) {
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
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = AppTheme.typography.body.let { if (bold) it.copy(fontWeight = FontWeight.SemiBold) else it }
        AppText(label, style, color = AppTheme.colors.accent, modifier = Modifier.weight(1f), maxLines = 1)
        if (icon != null) AppIcon(icon, contentDescription = null, size = 22.dp, tint = AppTheme.colors.accent)
    }
}
