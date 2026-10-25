package dev.cmdrchara.songlass.core.settings

/** Bundled launcher resources. Automatic is a preference, never a platform resource name. */
enum class LauncherIconVariant {
    Violet, Night, LiquidLight, LiquidDark, MaterialLight, MaterialDark, MiuixLight, MiuixDark,
}

/** Only icon-relevant preferences cross into the platform launcher scheduler. */
data class AppIconSelection(
    val choice: AppIconChoice,
    val interfaceStyle: InterfaceStyle,
    val appearance: AppearanceMode,
) {
    fun resolve(systemDark: Boolean): LauncherIconVariant = choice.resolve(interfaceStyle, when (appearance) {
        AppearanceMode.System -> systemDark
        AppearanceMode.Light -> false
        AppearanceMode.Dark -> true
    })
}

fun AppIconChoice.resolve(style: InterfaceStyle, dark: Boolean): LauncherIconVariant {
    val finish = when (this) {
        AppIconChoice.Automatic -> when (style) {
            InterfaceStyle.LiquidGlass -> AppIconChoice.LiquidGlass
            InterfaceStyle.MaterialGlass -> AppIconChoice.MaterialGlass
            InterfaceStyle.Miuix -> AppIconChoice.Miuix
        }
        else -> this
    }
    return when (finish) {
        AppIconChoice.Violet -> LauncherIconVariant.Violet
        AppIconChoice.Night -> LauncherIconVariant.Night
        AppIconChoice.LiquidGlass -> if (dark) LauncherIconVariant.LiquidDark else LauncherIconVariant.LiquidLight
        AppIconChoice.MaterialGlass -> if (dark) LauncherIconVariant.MaterialDark else LauncherIconVariant.MaterialLight
        AppIconChoice.Miuix -> if (dark) LauncherIconVariant.MiuixDark else LauncherIconVariant.MiuixLight
        AppIconChoice.Automatic -> error("Automatic must resolve to an interface finish")
    }
}
