package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.history.PlayCountRule
import dev.cmdrchara.songlass.core.player.ReplayGainMode

enum class AppearanceMode { System, Light, Dark }
enum class AlbumLayout { Grid, List }

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

    /** HyperOS-style components, powered by the official Miuix library. */
    Miuix,
}

/** The preferences one settings page restores together with "Restore defaults". */
enum class SettingsArea { Appearance, Playback, Lyrics, Accessibility, Library }

data class AppPreferences(
    val appearance: AppearanceMode = AppearanceMode.System,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val artworkStyle: ArtworkStyle = ArtworkStyle.FullBleed,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.LiquidGlass,
    /** Off by default: changing loudness on its own would surprise. */
    val replayGain: ReplayGainMode = ReplayGainMode.Off,
    /** Optional overlap at the natural end of a track; zero preserves gapless playback. */
    val crossfadeDurationMs: Long = 0,
    /** Independent overlap for explicit track changes. Off preserves the existing immediate change. */
    val manualCrossfadeDurationMs: Long = 0,
    /** Per-track progress is distinct from Android session resumption; opt in explicitly. */
    val resumePerTrack: Boolean = false,
    /** Long tracks offer ±10 s and (when enabled) independent resume points. */
    val longTrackThresholdMs: Long = 10 * 60_000L,
    val folderSort: FolderSort = FolderSort.Name,
    /** Play again when headphones or a car reconnect after cutting playback off. Off by default. */
    val resumeOnReconnect: Boolean = false,
    /** Additional word-timed lyrics provider; users may disable it without disabling LRCLIB. */
    val amllEnabled: Boolean = true,
    /** Snapshotted for each new playback occurrence; existing statistics are unchanged. */
    val playCountRule: PlayCountRule = PlayCountRule.ThirtySecondsOrHalf,
    /** A comfortable single-column alternative to the cover grid, useful with large text. */
    val albumLayout: AlbumLayout = AlbumLayout.Grid,
    val libraryFilter: dev.cmdrchara.songlass.core.library.LibraryFilter = dev.cmdrchara.songlass.core.library.LibraryFilter(),
)
