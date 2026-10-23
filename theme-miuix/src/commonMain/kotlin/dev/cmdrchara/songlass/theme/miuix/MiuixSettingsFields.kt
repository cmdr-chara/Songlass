package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.designsystem.SettingsGroupPosition
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults

/** Separate lazy items can share one continuous native group without adding intermediate pills. */
@Composable
internal fun MiuixSettingsGroupFragment(position: SettingsGroupPosition, modifier: Modifier, content: @Composable () -> Unit) {
    val top = position == SettingsGroupPosition.Single || position == SettingsGroupPosition.First
    val bottom = position == SettingsGroupPosition.Single || position == SettingsGroupPosition.Last
    val shape = RoundedCornerShape(
        topStart = if (top) 22.dp else 0.dp,
        topEnd = if (top) 22.dp else 0.dp,
        bottomStart = if (bottom) 22.dp else 0.dp,
        bottomEnd = if (bottom) 22.dp else 0.dp,
    )
    Card(modifier.fillMaxWidth().clip(shape), cornerRadius = 0.dp, insideMargin = PaddingValues(0.dp),
        colors = CardDefaults.defaultColors(color = AppTheme.colors.secondaryBackground)) { content() }
}

/** Embedded value choice aligns with the native toggle rows and owns only its picker. */
@Composable
internal fun MiuixSettingsValueChoice(title: String, labels: List<String>, selectedIndex: Int,
    onSelect: (Int) -> Unit, closeLabel: String, modifier: Modifier) =
    AppearancePreference(title, labels, selectedIndex, onSelect, closeLabel,
        icon = null, firstInGroup = false, lastInGroup = false, description = null,
        modifier = modifier, contentInset = 18.dp)
