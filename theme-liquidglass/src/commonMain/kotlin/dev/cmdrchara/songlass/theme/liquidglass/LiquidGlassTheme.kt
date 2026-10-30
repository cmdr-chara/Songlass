package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.cmdrchara.songlass.designsystem.LocalUiStyle
import dev.cmdrchara.songlass.designsystem.theme.AppColors
import dev.cmdrchara.songlass.designsystem.theme.AppTypography
import dev.cmdrchara.songlass.designsystem.theme.LocalAppColors
import dev.cmdrchara.songlass.designsystem.theme.LocalAppTypography
import dev.cmdrchara.songlass.designsystem.theme.LocalContentColor
import dev.cmdrchara.songlass.designsystem.theme.LocalUiAccessibility
import dev.cmdrchara.songlass.designsystem.theme.UiAccessibility

// Neutral colors follow the iOS system palette. The accent is a placeholder brand color:
// deliberately not Apple Music red, to be replaced when the app identity is defined.
private val Accent = Color(0xFF6644E0)
private val AccentDark = Color(0xFF9F87FF)

internal val LightColors = AppColors(
    isDark = false,
    background = Color.White,
    secondaryBackground = Color(0xFFF2F2F7),
    label = Color.Black,
    secondaryLabel = Color(0xC43C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F787880),
    accent = Accent,
)

internal val DarkColors = AppColors(
    isDark = true,
    background = Color.Black,
    secondaryBackground = Color(0xFF1C1C1E),
    label = Color.White,
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0xA6545458),
    fill = Color(0x3D767680),
    accent = AccentDark,
)

/**
 * HIG "Large (default)" Dynamic Type sizes and leading. The font is the platform default
 * (SF Pro is not redistributable on Android; a free alternative is still to be chosen).
 */
private fun style(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) =
    TextStyle(fontFamily = FontFamily.Default, fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight)

internal val Typography = AppTypography(
    largeTitle = style(34, 41, FontWeight.Bold),
    title1 = style(28, 34),
    title2 = style(22, 28, FontWeight.Bold),
    title3 = style(20, 25, FontWeight.SemiBold),
    headline = style(17, 22, FontWeight.SemiBold),
    body = style(17, 22),
    callout = style(16, 21),
    subheadline = style(15, 20),
    footnote = style(13, 18),
    caption1 = style(12, 16),
    caption2 = style(11, 13),
)

/** Provides the Liquid Glass tokens and components to [content]. */
@Composable
fun LiquidGlassTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accessibility: UiAccessibility = UiAccessibility(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val playerMorph = remember { PlayerMorph() }
    CompositionLocalProvider(
        LocalAppColors provides colors,
        LocalAppTypography provides Typography,
        LocalContentColor provides colors.label,
        LocalUiAccessibility provides accessibility,
        LocalUiStyle provides LiquidGlassStyle,
        LocalPlayerMorph provides playerMorph,
        content = { ProvideLiquidMetalMotion(content) },
    )
}
