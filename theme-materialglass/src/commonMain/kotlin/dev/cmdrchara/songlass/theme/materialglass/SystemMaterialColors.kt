package dev.cmdrchara.songlass.theme.materialglass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/** Semantic colors supplied by the OS, kept intact instead of rebuilding them from one hue. */
@Immutable
internal data class SystemMaterialColors(
    val background: Color,
    val container: Color,
    val containerHigh: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val secondaryContainer: Color,
    val onSecondaryContainer: Color,
    val label: Color,
    val secondaryLabel: Color,
)

/** Reads configuration-aware native resources; null where dynamic colors are unavailable. */
@Composable
internal expect fun platformMaterialColors(dark: Boolean): SystemMaterialColors?
