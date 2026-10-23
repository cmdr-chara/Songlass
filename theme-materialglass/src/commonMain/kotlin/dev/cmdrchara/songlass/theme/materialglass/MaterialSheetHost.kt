package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.dialog
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.designsystem.theme.LocalSheetTint

/** Root overlay: the app owns Back, while the sheet owns only its scrim and explicit controls. */
@Composable
internal fun MaterialSheetHost(
    visible: Boolean,
    title: String,
    subtitle: String?,
    artwork: Artwork? = null,
    closeLabel: String,
    onDismiss: () -> Unit,
    palette: SeaPalette,
    backLabel: String? = null,
    onBack: (() -> Unit)? = null,
    contentKey: String? = title,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val visibility = remember { MutableTransitionState(false) }
    visibility.targetState = visible
    val transition = rememberTransition(visibility, label = "materialSheet")
    val scroll = rememberScrollState()
    LaunchedEffect(visible, contentKey) { if (visible) scroll.scrollTo(0) }
    val shape = RoundedCornerShape(topStart = MaterialShapes.ExtraLarge, topEnd = MaterialShapes.ExtraLarge)
    CompositionLocalProvider(
        LocalAppColors provides palette.appColors(),
        LocalContentColor provides palette.label,
        LocalSheetTint provides null,
    ) {
        Box(Modifier.fillMaxSize()) {
            transition.AnimatedVisibility(
                visible = { it },
                enter = fadeIn(if (reduceMotion) snap() else MaterialMotion.effects()),
                exit = fadeOut(if (reduceMotion) snap() else MaterialMotion.effects()),
            ) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = visible,
                        role = Role.Button, onClickLabel = closeLabel, onClick = onDismiss)
                    .semantics { contentDescription = closeLabel })
            }
            BoxWithConstraints(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 16.dp)) {
                val availableHeight = maxHeight
                transition.AnimatedVisibility(
                    visible = { it },
                    enter = fadeIn(if (reduceMotion) snap() else MaterialMotion.effects()) +
                        slideInVertically(if (reduceMotion) snap() else MaterialMotion.spatial()) { if (reduceMotion) 0 else it },
                    exit = fadeOut(if (reduceMotion) snap() else MaterialMotion.effects()) +
                        slideOutVertically(if (reduceMotion) snap() else MaterialMotion.spatial()) { if (reduceMotion) 0 else it },
                    modifier = Modifier.align(Alignment.BottomCenter).widthIn(max = 640.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.fillMaxWidth().heightIn(max = availableHeight)
                        .shadow(8.dp, shape).clip(shape).background(palette.container)
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                        .pointerInput(Unit) { detectTapGestures { } }
                        .semantics { dialog(); paneTitle = title }) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (onBack != null && backLabel != null) IconButton(AppIcons.ArrowBack, backLabel,
                                { if (visible) onBack() }, Modifier.size(48.dp))
                            if (artwork != null) AlbumArt(artwork, Modifier.size(56.dp), cornerRadius = MaterialShapes.Medium)
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                AppText(title, AppTheme.typography.title3, color = palette.label,
                                    maxLines = 3, modifier = Modifier.semantics { heading() })
                                subtitle?.let { AppText(it, AppTheme.typography.subheadline, color = palette.secondaryLabel, maxLines = 2) }
                            }
                            IconButton(AppIcons.Close, closeLabel, { if (visible) onDismiss() }, Modifier.size(48.dp))
                        }
                        Column(Modifier.weight(1f, fill = false).fillMaxWidth().verticalScroll(scroll)
                            .padding(horizontal = 16.dp).padding(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
                    }
                }
            }
        }
    }
}
