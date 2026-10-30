package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Use the same compact value picker as the other appearance preferences. */
@Composable
internal fun LiquidMaterialOptions(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    closeLabel: String,
    modifier: Modifier,
) = LiquidSettingsValueChoice(title, labels, selectedIndex, onSelect, closeLabel, modifier)
