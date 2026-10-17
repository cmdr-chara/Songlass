package dev.cmdrchara.songlass.core.settings

enum class AppearanceMode { System, Light, Dark }

/** How covers are shown in Now Playing and on album/playlist pages. */
enum class ArtworkStyle {
    /** Edge to edge, fading into the page like a background. */
    FullBleed,

    /** A rounded square card on the page. */
    Square,
}

data class AppPreferences(
    val appearance: AppearanceMode = AppearanceMode.System,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val artworkStyle: ArtworkStyle = ArtworkStyle.FullBleed,
)
