package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.player.ReplayGainMode

enum class AppearanceMode { System, Light, Dark }

/** How covers are shown in Now Playing and on album/playlist pages. */
enum class ArtworkStyle {
    /** Edge to edge, fading into the page like a background. */
    FullBleed,

    /** A rounded square card on the page. */
    Square,
}

/** The interface style ("UI Style"); screens are the same, the look of every component changes. */
enum class InterfaceStyle {
    /** Apple-inspired iOS 26 Liquid Glass (default). */
    LiquidGlass,

    /** Pastel coloured glass after the "Google Sea Glass" Figma file. */
    MaterialGlass,
}

data class AppPreferences(
    val appearance: AppearanceMode = AppearanceMode.System,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val artworkStyle: ArtworkStyle = ArtworkStyle.FullBleed,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.LiquidGlass,
    /** Off by default: changing loudness on its own would surprise. */
    val replayGain: ReplayGainMode = ReplayGainMode.Off,
    val folderSort: FolderSort = FolderSort.Name,
)
