package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Connected appearance row; the options still use the compact value picker. */
@Composable
internal fun LiquidMaterialOptions(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    closeLabel: String,
    modifier: Modifier,
) = LiquidAppearanceFinishOptions(title, labels, selectedIndex, onSelect, closeLabel, modifier)
