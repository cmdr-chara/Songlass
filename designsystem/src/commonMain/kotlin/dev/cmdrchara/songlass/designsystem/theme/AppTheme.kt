package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

/** Shortcut to the current style's tokens. */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val typography: AppTypography
        @Composable @ReadOnlyComposable get() = LocalAppTypography.current
}

/** Color for text and icons inside the current container (e.g. white on Now Playing). */
val LocalContentColor = compositionLocalOf { Color.Black }
