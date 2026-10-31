package dev.cmdrchara.songlass.theme.miuix

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import dev.cmdrchara.songlass.designsystem.foundation.rememberArtworkTint
import dev.cmdrchara.songlass.designsystem.theme.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Always present so switching style keeps the same screens, scroll positions and player morph. */
@Composable
fun MiuixLayer(enabled: Boolean, darkTheme: Boolean, content: @Composable () -> Unit) {
    // Allocate/capture a backdrop only for the active style and supported rendering path; with
    // background blur off the native surfaces are opaque, so no layer is recorded at all.
    val backdrop = if (enabled && LocalBlurStrength.current.blurred && !AppTheme.accessibility.reduceTransparency &&
        isRuntimeShaderSupported()) rememberLayerBackdrop() else null
    val artwork = if (enabled) LocalAmbientArtwork.current else null
    val tint = rememberArtworkTint(artwork, darkTheme)
    val accent = if (artwork == null) if (darkTheme) Color(0xFFB49AFF) else Color(0xFF7146D8) else tint.activeFill
    val onAccent = if (artwork == null) if (darkTheme) Color(0xFF20152E) else Color.White else tint.onActiveFill
    val scheme = remember(darkTheme, accent, onAccent) {
        val base = if (darkTheme) darkColorScheme(primary = accent, primaryVariant = accent, onPrimary = onAccent, background = Color(0xFF101011), surface = Color(0xFF202023), surfaceContainer = Color(0xFF27272B))
        else lightColorScheme(primary = accent, primaryVariant = accent, onPrimary = onAccent, background = Color(0xFFF6F6F8), surface = Color.White, surfaceContainer = Color.White)
        // Follow Miuix's custom-palette mapping instead of retaining its fixed blue disabled roles.
        val disabledFill = base.primary.copy(alpha = 0.38f).compositeOver(base.surface)
        base.copy(
            disabledPrimary = disabledFill,
            disabledOnPrimary = base.onPrimary.copy(alpha = 0.38f).compositeOver(disabledFill),
        )
    }
    val colors = remember(scheme) {
        AppColors(darkTheme, scheme.background, scheme.surface, scheme.onBackground, scheme.onSurfaceVariantSummary,
            scheme.onSurfaceVariantSummary.copy(alpha = 0.65f), scheme.dividerLine,
            if (darkTheme) Color(0xFF303035) else Color(0xFFEAEAEE), accent)
    }
    val parentIndication = LocalIndication.current
    val parentOverscroll = LocalOverscrollFactory.current
    val parentColors = LocalAppColors.current
    val parentTypography = LocalAppTypography.current
    val parentContent = LocalContentColor.current
    val parentLook = LocalGlassLook.current
    val parentArtworkPageTint = LocalArtworkPageUsesTint.current
    MiuixTheme(colors = scheme) {
        CompositionLocalProvider(
            LocalAppColors provides if (enabled) colors else parentColors,
            LocalAppTypography provides if (enabled) nativeTypography() else parentTypography,
            LocalContentColor provides if (enabled) colors.label else parentContent,
            LocalGlassLook provides if (enabled) MiuixLook else parentLook,
            LocalMiuixBackdrop provides backdrop,
            LocalArtworkPageUsesTint provides if (enabled) false else parentArtworkPageTint,
            // Keep Miuix's indication scoped; use platform edge feedback consistently across looks.
            LocalIndication provides if (enabled) LocalIndication.current else parentIndication,
            LocalOverscrollFactory provides parentOverscroll,
        ) {
            // Keep the same host for every style: native overlays need it and screen state survives switches.
            top.yukonga.miuix.kmp.basic.Scaffold(Modifier.fillMaxSize(), containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0)) { content() }
        }
    }
}

@Composable
private fun nativeTypography(): AppTypography {
    val styles = MiuixTheme.textStyles
    return AppTypography(largeTitle = styles.title1, title1 = styles.title2, title2 = styles.title3,
        title3 = styles.title4, headline = styles.headline1, body = styles.body1, callout = styles.main,
        subheadline = styles.body2, footnote = styles.footnote1, caption1 = styles.footnote2, caption2 = styles.footnote2)
}
