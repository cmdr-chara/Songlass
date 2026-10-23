package dev.cmdrchara.songlass.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

enum class SettingsVisualChoiceKind { InterfaceStyle, Artwork, AppIcon }

/** The active look owns arrangement while the screen owns values and callbacks. */
@Immutable
data class SettingsVisualChoice(
    val title: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val preview: @Composable () -> Unit,
)
