package dev.cmdrchara.songlass.core.settings

enum class AppearanceMode { System, Light, Dark }

data class AppPreferences(
    val appearance: AppearanceMode = AppearanceMode.System,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
)
