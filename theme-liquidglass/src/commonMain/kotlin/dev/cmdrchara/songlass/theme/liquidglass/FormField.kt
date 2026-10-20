package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme

/** Label stays visible when populated; Liquid inset form and Material filled field. */
@Composable
fun GlassFormField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean, material: Boolean, modifier: Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default, keyboardActions: KeyboardActions = KeyboardActions.Default) {
    val colors = AppTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(if (material) 16.dp else 12.dp)
    Column(modifier.fillMaxWidth().padding(vertical = 5.dp)
        .background(colors.label.copy(alpha = if (material) 0.07f else 0.04f), shape)
        .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.label.copy(alpha = 0.12f), shape)
        .padding(horizontal = 16.dp, vertical = 12.dp)) {
        AppText(label, AppTheme.typography.caption1, color = if (focused) colors.accent else colors.secondaryLabel,
            modifier = Modifier.padding(bottom = 6.dp))
        BasicTextField(value, onValueChange, Modifier.fillMaxWidth().semantics { contentDescription = label },
            enabled = enabled, singleLine = true, textStyle = AppTheme.typography.body.copy(color = colors.label),
            cursorBrush = SolidColor(colors.accent), interactionSource = interaction,
            keyboardOptions = keyboardOptions, keyboardActions = keyboardActions)
    }
}
