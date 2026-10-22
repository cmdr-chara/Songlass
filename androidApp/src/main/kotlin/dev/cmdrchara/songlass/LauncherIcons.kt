package dev.cmdrchara.songlass

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.cmdrchara.songlass.core.settings.InterfaceStyle

/**
 * Switches the launcher icon between the three activity aliases. Exactly one is enabled: the new one
 * first and the others after, so the app never has a moment without a launcher entry. Aliases that
 * are already in the wanted state are left alone, because every change makes the launcher refresh.
 */
internal object LauncherIcons {
    private val aliases = mapOf(
        InterfaceStyle.LiquidGlass to ".LauncherLiquid",
        InterfaceStyle.MaterialGlass to ".LauncherMaterial",
        InterfaceStyle.Miuix to ".LauncherMiuix",
    )

    /** [style] null means the default icon (Liquid). */
    fun apply(context: Context, style: InterfaceStyle?) {
        val packages = context.packageManager
        val wanted = aliases.getValue(style ?: InterfaceStyle.LiquidGlass)
        fun component(name: String) = ComponentName(context.packageName, context.packageName + name)
        fun isEnabled(name: String): Boolean {
            val state = packages.getComponentEnabledSetting(component(name))
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && name == ".LauncherLiquid")
        }
        if (!isEnabled(wanted)) {
            packages.setComponentEnabledSetting(component(wanted), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        for (name in aliases.values) {
            if (name != wanted && isEnabled(name)) {
                packages.setComponentEnabledSetting(component(name), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
            }
        }
    }
}
