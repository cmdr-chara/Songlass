package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf

/** Effective accommodations, combining the app preferences with platform settings. */
@Immutable
data class UiAccessibility(
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
)

val LocalUiAccessibility = compositionLocalOf { UiAccessibility() }
