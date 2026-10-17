package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Semantic colors every UI style provides, named after their role, not their value. */
@Immutable
data class AppColors(
    val isDark: Boolean,
    /** Main screen background. */
    val background: Color,
    /** Grouped content and cards on top of [background]. */
    val secondaryBackground: Color,
    /** Primary text and icons. */
    val label: Color,
    /** Subtitles and metadata. */
    val secondaryLabel: Color,
    /** Placeholders and disabled content. */
    val tertiaryLabel: Color,
    /** Hairlines between list rows. */
    val separator: Color,
    /** Neutral fill for controls such as search fields and secondary buttons. */
    val fill: Color,
    /** Brand color for interactive elements. */
    val accent: Color,
)

val LocalAppColors = staticCompositionLocalOf<AppColors> { error("No AppColors provided: wrap the UI in a theme") }
