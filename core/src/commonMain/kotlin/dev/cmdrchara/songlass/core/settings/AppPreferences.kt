package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.playback.HeadphoneSettings

import dev.cmdrchara.songlass.core.player.AudioFocusSettings

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
enum class SettingsArea { Appearance, Sound, Player, Queue, Library }

/**
 * Strength of the blurred backdrop behind glass navigation surfaces. Every style scales its own
 * radii with it; [Off] uses the same opaque, readable fallback as Reduce Transparency instead of a
 * zero-radius blur, which would leave sharp text under a translucent bar.
 */
enum class BlurIntensity(val storageKey: String) {
    Off("off"),
    Low("low"),

    /** The reference rendering: existing installations keep exactly the same optics. */
    Default("default"),
    High("high");

    companion object {
        fun fromStorageKey(key: String?): BlurIntensity = entries.firstOrNull { it.storageKey == key } ?: Default
    }
}

/**
 * Resolves the font scale the app applies on top of the platform one. Both values multiply, then
 * the result is clamped to [MIN]..[MAX]: below the smallest size the platform offers text becomes
 * unreadable, while above twice the default the fixed-size chrome (44 dp targets, seek bars) no
 * longer fits. Density is never changed, so every dp keeps its physical size.
 */
object AppFontScale {
    const val MIN = 0.85f
    const val MAX = 2f

    fun resolve(systemFontScale: Float, preferenceScale: Float): Float {
        val system = if (systemFontScale.isFinite() && systemFontScale > 0f) systemFontScale else 1f
        val preference = if (preferenceScale.isFinite() && preferenceScale > 0f) preferenceScale else 1f
        return (system * preference).coerceIn(MIN, MAX)
    }
}

/** A fixed legacy icon, an explicit finish, or a finish that follows the interface. */
enum class AppIconChoice(val storageKey: String) {
    /** Kept as the default so existing installations retain their chosen identity. */
    Violet("violet"),
    Night("night"),
    Automatic("automatic"),
    LiquidGlass("liquid"),
    MaterialGlass("material"),
    Miuix("miuix");

    companion object {
        fun fromStorageKey(key: String?): AppIconChoice = entries.firstOrNull { it.storageKey == key } ?: Violet
    }
}

data class AppPreferences(
    val appearance: AppearanceMode = AppearanceMode.System,
    val reduceMotion: Boolean = false,
    val reduceTransparency: Boolean = false,
    val artworkStyle: ArtworkStyle = ArtworkStyle.FullBleed,
    val interfaceStyle: InterfaceStyle = InterfaceStyle.LiquidGlass,
    /** App text size multiplier, one of [AppSettings.TEXT_SCALE_CHOICES]; 1.0 preserves the current size. */
    val textScale: Float = 1f,
    /** Strength of the blurred backdrop; the default reproduces the current rendering exactly. */
    val blurIntensity: BlurIntensity = BlurIntensity.Default,
    /** Off by default: changing loudness on its own would surprise. */
    val replayGain: ReplayGainMode = ReplayGainMode.Off,
    val replayGainSettings: dev.cmdrchara.songlass.core.player.ReplayGainSettings = dev.cmdrchara.songlass.core.player.ReplayGainSettings(),
    val equalizerTuning: dev.cmdrchara.songlass.core.equalizer.EqualizerTuning = dev.cmdrchara.songlass.core.equalizer.EqualizerTuning(),
    /**
     * Keeps the graphic equalizer bands read-only so scrolling or brushing over the curve cannot
     * change the sound by accident. Presets, tone controls and headphone corrections still apply;
     * unlocking is a deliberate one-tap choice, stored and restored with the Sound page.
     */
    val equalizerLocked: Boolean = true,
    val audioEffects: dev.cmdrchara.songlass.core.player.AudioEffectsSettings = dev.cmdrchara.songlass.core.player.AudioEffectsSettings(),
    /** Optional overlap at the natural end of a track; zero preserves gapless playback. */
    val crossfadeDurationMs: Long = 0,
    /** Independent overlap for explicit track changes. Off preserves the existing immediate change. */
    val manualCrossfadeDurationMs: Long = 0,
    /** Independent pause/resume, stop and seek envelopes; zero keeps immediate transport. */
    val transportFades: dev.cmdrchara.songlass.core.player.TransportFadeSettings = dev.cmdrchara.songlass.core.player.TransportFadeSettings(),
    /** Per-track progress is distinct from Android session resumption; opt in explicitly. */
    val resumePerTrack: Boolean = false,
    /** Long tracks offer ±10 s and (when enabled) independent resume points. */
    val longTrackThresholdMs: Long = 10 * 60_000L,
    val folderSort: FolderSort = FolderSort.Name,
    /** Play again when headphones or a car reconnect after cutting playback off. Off by default. */
    val headphones: HeadphoneSettings = HeadphoneSettings(),
    /** Additional word-timed lyrics provider; users may disable it without disabling LRCLIB. */
    val amllEnabled: Boolean = true,
    /** The fallback online provider; off keeps lyrics to the file's own tags and imported files. */
    val lrclibEnabled: Boolean = true,
    /** Snapshotted for each new playback occurrence; existing statistics are unchanged. */
    val playCountRule: PlayCountRule = PlayCountRule.ThirtySecondsOrHalf,
    /** A comfortable single-column alternative to the cover grid, useful with large text. */
    val albumLayout: AlbumLayout = AlbumLayout.Grid,
    val libraryFilter: dev.cmdrchara.songlass.core.library.LibraryFilter = dev.cmdrchara.songlass.core.library.LibraryFilter(),
    val trackClickAction: TrackClickAction = TrackClickAction.PlayList,
    val queueStart: QueueStart = QueueStart.AfterCurrent,
    val queueEnd: QueueEnd = QueueEnd.ReturnToCollection,
    val queueAddOrder: QueueAddOrder = QueueAddOrder.End,
    /** Each "Add to queue" replaces the additions still waiting instead of joining them. */
    val queueReplaces: Boolean = false,
    val shuffleBias: ShuffleBias = ShuffleBias.Random,
    /** Picking a song while shuffle is on keeps shuffling the list, with that song first. */
    val keepShuffleOnSelection: Boolean = false,
    val appIcon: AppIconChoice = AppIconChoice.Violet,
    /** Pause for calls and let other apps take over; off plays over everything else. */
    val handleAudioFocus: Boolean = true,
    val audioFocus: AudioFocusSettings = AudioFocusSettings(),
    /** Pause when headphones are unplugged or Bluetooth drops, so the speaker does not start. */
    val pauseOnDisconnect: Boolean = true,
    /** Two headset presses skip forward and three go back; off makes every press play/pause at once. */
    val headsetMultiPress: Boolean = true,
    /** Album cover lookup over the network; off by default, so nothing leaves the device unasked. */
    val onlineArtworkEnabled: Boolean = false,
    /** With online covers on, downloads wait for an unmetered connection unless turned off. */
    val onlineArtworkWifiOnly: Boolean = true,
    /** Last.fm scrobbling is opt-in; without an API key pair the feature stays inactive. */
    val lastFmEnabled: Boolean = false,
    /** Last.fm API credentials, configured on this device: the repository ships without a key. */
    val lastFmApiKey: String = "",
    val lastFmApiSecret: String = "",
) {
    /** Compatibility summary for callers that still ask whether both connection types resume. */
    val resumeOnReconnect: Boolean get() = headphones.resumeWired && headphones.resumeBluetooth
}
