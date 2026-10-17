package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle

/**
 * Text styles named after the Apple HIG Dynamic Type styles. Every UI style maps them
 * to its own fonts and sizes, so screens only pick a role.
 */
@Immutable
data class AppTypography(
    val largeTitle: TextStyle,
    val title1: TextStyle,
    val title2: TextStyle,
    val title3: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val callout: TextStyle,
    val subheadline: TextStyle,
    val footnote: TextStyle,
    val caption1: TextStyle,
    val caption2: TextStyle,
)

val LocalAppTypography = staticCompositionLocalOf<AppTypography> { error("No AppTypography provided: wrap the UI in a theme") }
