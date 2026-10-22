package dev.cmdrchara.songlass.theme.materialglass

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.colorResource

/** Android 14+ exposes complete roles, including OEM theme and contrast adjustments. */
@Composable
internal actual fun platformMaterialColors(dark: Boolean): SystemMaterialColors? {
    if (Build.VERSION.SDK_INT >= 34) {
        return SystemMaterialColors(
            background = colorResource(if (dark) android.R.color.system_surface_dark else android.R.color.system_surface_light),
            container = colorResource(if (dark) android.R.color.system_surface_container_dark else android.R.color.system_surface_container_light),
            containerHigh = colorResource(if (dark) android.R.color.system_surface_container_high_dark else android.R.color.system_surface_container_high_light),
            primary = colorResource(if (dark) android.R.color.system_primary_dark else android.R.color.system_primary_light),
            onPrimary = colorResource(if (dark) android.R.color.system_on_primary_dark else android.R.color.system_on_primary_light),
            primaryContainer = colorResource(if (dark) android.R.color.system_primary_container_dark else android.R.color.system_primary_container_light),
            onPrimaryContainer = colorResource(if (dark) android.R.color.system_on_primary_container_dark else android.R.color.system_on_primary_container_light),
            secondary = colorResource(if (dark) android.R.color.system_secondary_dark else android.R.color.system_secondary_light),
            secondaryContainer = colorResource(if (dark) android.R.color.system_secondary_container_dark else android.R.color.system_secondary_container_light),
            onSecondaryContainer = colorResource(if (dark) android.R.color.system_on_secondary_container_dark else android.R.color.system_on_secondary_container_light),
            label = colorResource(if (dark) android.R.color.system_on_surface_dark else android.R.color.system_on_surface_light),
            secondaryLabel = colorResource(if (dark) android.R.color.system_on_surface_variant_dark else android.R.color.system_on_surface_variant_light),
        )
    }
    // Android 13: use the public system tonal palettes (tone = 100 - suffix / 10).
    return SystemMaterialColors(
        background = colorResource(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10),
        container = colorResource(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_50),
        containerHigh = colorResource(if (dark) android.R.color.system_neutral1_800 else android.R.color.system_neutral1_100),
        primary = colorResource(if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600),
        onPrimary = colorResource(if (dark) android.R.color.system_accent1_800 else android.R.color.system_accent1_0),
        primaryContainer = colorResource(if (dark) android.R.color.system_accent1_700 else android.R.color.system_accent1_100),
        onPrimaryContainer = colorResource(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_900),
        secondary = colorResource(if (dark) android.R.color.system_accent2_200 else android.R.color.system_accent2_600),
        secondaryContainer = colorResource(if (dark) android.R.color.system_accent2_700 else android.R.color.system_accent2_100),
        onSecondaryContainer = colorResource(if (dark) android.R.color.system_accent2_100 else android.R.color.system_accent2_900),
        label = colorResource(if (dark) android.R.color.system_neutral1_100 else android.R.color.system_neutral1_900),
        secondaryLabel = colorResource(if (dark) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700),
    )
}
