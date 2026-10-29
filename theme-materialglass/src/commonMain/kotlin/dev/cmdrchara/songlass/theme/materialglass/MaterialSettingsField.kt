package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Numeric presentation only: the caller keeps ownership of validation and submission. */
@Composable
internal fun MaterialSettingsDurationField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    onSubmit: () -> Unit,
    modifier: Modifier,
) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val outline by animateColorAsState(if (focused) palette.primary else palette.secondaryLabel.copy(alpha = 0.45f),
        if (reduceMotion) snap() else MaterialMotion.effects(), label = "settingsFieldOutline")
    val shape = RoundedCornerShape(MaterialShapes.Large)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        interactionSource = interaction,
        textStyle = AppTheme.typography.body.copy(color = palette.label),
        cursorBrush = SolidColor(palette.primary),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(palette.containerHigh)
            .border(if (focused) 2.dp else 1.dp, outline, shape)
            .semantics { contentDescription = label },
        decorationBox = { inner ->
            Column(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)) {
                AppText(label, AppTheme.typography.footnote,
                    color = if (focused) palette.primary else palette.secondaryLabel,
                    modifier = Modifier.clearAndSetSemantics { })
                inner()
            }
        },
    )
}

/** A tonal Material action shared by settings and other existing ActionButton consumers. */
@Composable
internal fun MaterialActionButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier) {
    val palette = currentPalette()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val scale by animateFloatAsState(if (pressed && !reduceMotion) 0.98f else 1f,
        if (reduceMotion) snap() else MaterialMotion.press(), label = "materialActionPress")
    Row(modifier.heightIn(min = 48.dp)
        .graphicsLayer { scaleX = scale.coerceAtMost(1f); scaleY = scale.coerceAtMost(1f) }
        .clip(RoundedCornerShape(20.dp)).background(palette.secondaryContainer)
        .clickable(interaction, MaterialRipple, role = Role.Button, onClick = onClick)
        .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically) {
        icon?.let { AppIcon(it, null, size = 20.dp, tint = palette.onSecondaryContainer) }
        AppText(text, AppTheme.typography.body.copy(fontWeight = FontWeight.Medium), color = palette.onSecondaryContainer,
            textAlign = TextAlign.Center)
    }
}
