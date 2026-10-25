package dev.cmdrchara.songlass.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.settings.LauncherIconVariant
import dev.cmdrchara.songlass.shared.R
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.settings_app_icon_hint
import org.jetbrains.compose.resources.stringResource

private data class NativeIcon(val drawable: Int, val colors: List<Int>)

private val nativeIcons = mapOf(
    LauncherIconVariant.LiquidLight to NativeIcon(R.mipmap.ic_launcher_liquid_light, listOf(
        android.R.color.system_neutral1_0, android.R.color.system_neutral1_50,
        android.R.color.system_accent2_100, android.R.color.system_neutral1_600, android.R.color.system_accent1_600)),
    LauncherIconVariant.LiquidDark to NativeIcon(R.mipmap.ic_launcher_liquid_dark, listOf(
        android.R.color.system_neutral1_700, android.R.color.system_neutral1_800,
        android.R.color.system_neutral1_900, android.R.color.system_neutral1_50, android.R.color.system_accent1_200)),
    LauncherIconVariant.MaterialLight to NativeIcon(R.mipmap.ic_launcher_material_light, listOf(
        android.R.color.system_accent1_100, android.R.color.system_accent1_700, android.R.color.system_accent3_600)),
    LauncherIconVariant.MaterialDark to NativeIcon(R.mipmap.ic_launcher_material_dark, listOf(
        android.R.color.system_accent1_800, android.R.color.system_accent1_100, android.R.color.system_accent3_200)),
    LauncherIconVariant.MiuixLight to NativeIcon(R.mipmap.ic_launcher_miuix_light, listOf(
        android.R.color.system_accent1_600, android.R.color.system_accent1_0, android.R.color.system_accent3_100)),
    LauncherIconVariant.MiuixDark to NativeIcon(R.mipmap.ic_launcher_miuix_dark, listOf(
        android.R.color.system_accent1_300, android.R.color.system_accent1_900, android.R.color.system_accent3_900)),
)

@Composable
internal actual fun platformLauncherIconPainter(variant: LauncherIconVariant): Painter? {
    val icon = nativeIcons[variant] ?: return null
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val size = with(LocalDensity.current) { 36.dp.roundToPx() }.coerceAtLeast(1)
    // Explicit resolved colors invalidate the preview for palette overlays as well as configuration changes.
    val palette = icon.colors.map { colorResource(it) }
    return remember(context, configuration, size, icon.drawable, palette) {
        val drawable = requireNotNull(context.getDrawable(icon.drawable)).mutate()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, size, size)
        drawable.draw(Canvas(bitmap))
        BitmapPainter(bitmap.asImageBitmap())
    }
}

@Composable
internal actual fun launcherIconHint(): String = stringResource(Res.string.settings_app_icon_hint)
