package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.designsystem.theme.LocalSheetTint

/** A basic Material dialog, independent of the navigation glass and artwork colors. */
@Composable
internal fun MaterialPromptSheet(
    visible: Boolean,
    title: String,
    initialText: String,
    placeholder: String,
    confirmLabel: String,
    cancelLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    palette: SeaPalette,
) {
    if (!visible) return
    var text by remember(initialText) { mutableStateOf(initialText) }
    var submitted by remember(initialText) { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val outline by animateColorAsState(if (focused) palette.primary else palette.secondaryLabel.copy(alpha = 0.5f),
        if (reduceMotion) snap() else MaterialMotion.effects(), label = "promptOutline")
    val enabled = text.isNotBlank() && !submitted
    fun confirm() {
        if (text.isNotBlank() && !submitted) {
            submitted = true
            // Keep request/restore guards live while the confirmation callback runs.
            onConfirm(text.trim())
            onDismiss()
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(Unit) {
            focus.requestFocus()
            keyboard?.show()
        }
        CompositionLocalProvider(LocalContentColor provides palette.label, LocalSheetTint provides null) {
            BoxWithConstraints(Modifier.fillMaxWidth().imePadding().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.widthIn(min = minOf(280.dp, maxWidth), max = minOf(560.dp, maxWidth))
                        .fillMaxWidth().heightIn(max = maxHeight)
                        .clip(RoundedCornerShape(28.dp)).background(palette.containerHigh)
                        .verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    AppText(title, AppTheme.typography.title2.copy(fontSize = 24.sp, lineHeight = 32.sp),
                        color = palette.label, modifier = Modifier.semantics { heading() })
                    val fieldShape = RoundedCornerShape(MaterialShapes.Medium)
                    BasicTextField(
                        value = text,
                        onValueChange = { if (!submitted) text = it },
                        singleLine = true,
                        interactionSource = interaction,
                        textStyle = AppTheme.typography.body.copy(color = palette.label),
                        cursorBrush = SolidColor(palette.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { confirm() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus)
                            .heightIn(min = 56.dp).clip(fieldShape)
                            .background(palette.container)
                            .border(if (focused) 2.dp else 1.dp, outline, fieldShape)
                            .semantics { contentDescription = placeholder },
                        decorationBox = { inner ->
                            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart) {
                                if (text.isEmpty()) AppText(placeholder, AppTheme.typography.body,
                                    color = palette.secondaryLabel, maxLines = 1, modifier = Modifier.clearAndSetSemantics { })
                                inner()
                            }
                        },
                    )
                    BoxWithConstraints(Modifier.fillMaxWidth()) {
                        val density = LocalDensity.current
                        val measurer = rememberTextMeasurer()
                        val style = AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium)
                        val required = with(density) {
                            measurer.measure(cancelLabel, style).size.width.toDp() +
                                measurer.measure(confirmLabel, style).size.width.toDp() + 72.dp
                        }
                        if (required <= maxWidth) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                                PromptButton(cancelLabel, true, false, onDismiss, palette)
                                PromptButton(confirmLabel, enabled, true, { confirm() }, palette)
                            }
                        } else {
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.End) {
                                PromptButton(cancelLabel, true, false, onDismiss, palette, Modifier.fillMaxWidth())
                                PromptButton(confirmLabel, enabled, true, { confirm() }, palette, Modifier.fillMaxWidth())
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromptButton(label: String, enabled: Boolean, filled: Boolean, onClick: () -> Unit,
    palette: SeaPalette, modifier: Modifier = Modifier) {
    val background = when {
        !filled -> Color.Transparent
        enabled -> palette.primary
        else -> palette.label.copy(alpha = 0.12f)
    }
    val content = when {
        !enabled -> palette.label.copy(alpha = 0.38f)
        filled -> palette.onPrimary
        else -> palette.primary
    }
    Box(modifier.widthIn(min = 48.dp).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp))
        .background(background)
        .clickable(remember { MutableInteractionSource() }, MaterialRipple, enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
        AppText(label, AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium), color = content)
    }
}
