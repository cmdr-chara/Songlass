package dev.cmdrchara.songlass

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import dev.cmdrchara.songlass.core.settings.AppIconSelection
import dev.cmdrchara.songlass.core.settings.LauncherIconVariant
import android.content.res.Configuration

/**
 * Switches the launcher icon between the activity aliases, one per icon. Exactly one is enabled: the new one
 * first and the others after, so the app never has a moment without a launcher entry. Aliases that
 * are already in the wanted state are left alone, because every change makes the launcher refresh.
 *
 * Changing the enabled alias sends the open app to the home screen, so while a screen is showing the
 * change waits until the last one is hidden.
 */
internal object LauncherIcons {
    private val aliases = mapOf(
        LauncherIconVariant.Violet to ".LauncherViolet",
        LauncherIconVariant.Night to ".LauncherNight",
        LauncherIconVariant.LiquidLight to ".LauncherLiquidLight",
        LauncherIconVariant.LiquidDark to ".LauncherLiquidDark",
        LauncherIconVariant.MaterialLight to ".LauncherMaterialLight",
        LauncherIconVariant.MaterialDark to ".LauncherMaterialDark",
        LauncherIconVariant.MiuixLight to ".LauncherMiuixLight",
        LauncherIconVariant.MiuixDark to ".LauncherMiuixDark",
    )

    private var started = 0
    private var pending: AppIconSelection? = null
    private var latest: AppIconSelection? = null

    fun watch(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) {
                started = (started - 1).coerceAtLeast(0)
                val icon = pending
                if (started == 0 && icon != null) { pending = null; commit(application, resolve(application, icon)) }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }

    fun apply(context: Context, icon: AppIconSelection) {
        latest = icon
        if (started > 0) pending = icon else { pending = null; commit(context, resolve(context, icon)) }
    }

    /** Re-evaluate System appearance without changing the saved icon preference. */
    fun refresh(context: Context) { latest?.let { apply(context, it) } }

    private fun resolve(context: Context, selection: AppIconSelection): LauncherIconVariant = selection.resolve(
        context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
    )

    private fun commit(context: Context, icon: LauncherIconVariant) {
        val packages = context.packageManager
        val wanted = aliases.getValue(icon)
        fun component(name: String) = ComponentName(context.packageName, context.packageName + name)
        fun isEnabled(name: String): Boolean {
            val state = packages.getComponentEnabledSetting(component(name))
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && name == ".LauncherViolet")
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
