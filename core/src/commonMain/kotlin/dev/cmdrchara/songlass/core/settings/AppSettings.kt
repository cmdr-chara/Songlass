package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.library.LibraryFilter
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import dev.cmdrchara.songlass.core.library.FolderSort
import dev.cmdrchara.songlass.core.player.ReplayGainMode
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Persistent preferences shared by all interfaces and platforms. */
class AppSettings(private val store: KeyValueStore) {
    private val _state = MutableStateFlow(readPreferences())

    private fun readPreferences(): AppPreferences = AppPreferences(
        appearance = when (store.getString(APPEARANCE_KEY)) {
            "light" -> AppearanceMode.Light
            "dark" -> AppearanceMode.Dark
            else -> AppearanceMode.System
        },
        reduceMotion = store.getString(REDUCE_MOTION_KEY)?.toBooleanStrictOrNull() ?: false,
        reduceTransparency = store.getString(REDUCE_TRANSPARENCY_KEY)?.toBooleanStrictOrNull() ?: false,
        artworkStyle = when (store.getString(ARTWORK_STYLE_KEY)) {
            "square" -> ArtworkStyle.Square
            else -> ArtworkStyle.FullBleed
        },
        interfaceStyle = when (store.getString(INTERFACE_STYLE_KEY)) {
            "material_glass" -> InterfaceStyle.MaterialGlass
            "miuix" -> InterfaceStyle.Miuix
            else -> InterfaceStyle.LiquidGlass
        },
        replayGain = when (store.getString(REPLAY_GAIN_KEY)) {
            "track" -> ReplayGainMode.Track
            "album" -> ReplayGainMode.Album
            else -> ReplayGainMode.Off
        },
        folderSort = when (store.getString(FOLDER_SORT_KEY)) {
            "recent" -> FolderSort.RecentlyAdded
            "most_played" -> FolderSort.MostPlayed
            else -> FolderSort.Name
        },
        resumeOnReconnect = store.getString(RESUME_KEY)?.toBooleanStrictOrNull() ?: false,
        crossfadeDurationMs = store.getString(CROSSFADE_KEY)?.toLongOrNull()?.takeIf { it in 0L..12_000L } ?: 0L,
        amllEnabled = store.getString(AMLL_KEY)?.toBooleanStrictOrNull() ?: true,
        libraryFilter = LibraryFilter(
            excludedFolders = store.getStringList(EXCLUDED_FOLDERS_KEY).map { it.trim('/') }
                .filter { it.isNotBlank() }.toSet(),
            minimumDurationMs = store.getString(MIN_DURATION_KEY)?.toLongOrNull()
                ?.takeIf { it in LibraryFilter.durationChoicesMs } ?: 0,
        ),
    )

    val state: StateFlow<AppPreferences> = _state.asStateFlow()

    /** Refreshes the existing observable preferences after a validated backup restore. */
    fun reload() { _state.value = readPreferences() }

    fun setCrossfadeDuration(durationMs: Long) {
        require(durationMs in 0L..12_000L)
        store.putString(CROSSFADE_KEY, durationMs.toString())
        _state.update { it.copy(crossfadeDurationMs = durationMs) }
    }

    fun setAppearance(appearance: AppearanceMode) {
        val storedValue = when (appearance) {
            AppearanceMode.System -> "system"
            AppearanceMode.Light -> "light"
            AppearanceMode.Dark -> "dark"
        }
        store.putString(APPEARANCE_KEY, storedValue)
        _state.update { it.copy(appearance = appearance) }
    }

    fun setReduceMotion(reduceMotion: Boolean) {
        store.putString(REDUCE_MOTION_KEY, reduceMotion.toString())
        _state.update { it.copy(reduceMotion = reduceMotion) }
    }

    fun setReduceTransparency(reduceTransparency: Boolean) {
        store.putString(REDUCE_TRANSPARENCY_KEY, reduceTransparency.toString())
        _state.update { it.copy(reduceTransparency = reduceTransparency) }
    }

    fun setArtworkStyle(style: ArtworkStyle) {
        store.putString(
            ARTWORK_STYLE_KEY,
            when (style) {
                ArtworkStyle.FullBleed -> "full_bleed"
                ArtworkStyle.Square -> "square"
            },
        )
        _state.update { it.copy(artworkStyle = style) }
    }

    fun setInterfaceStyle(style: InterfaceStyle) {
        store.putString(
            INTERFACE_STYLE_KEY,
            when (style) {
                InterfaceStyle.LiquidGlass -> "liquid_glass"
                InterfaceStyle.MaterialGlass -> "material_glass"
                InterfaceStyle.Miuix -> "miuix"
            },
        )
        _state.update { it.copy(interfaceStyle = style) }
    }

    fun setReplayGain(mode: ReplayGainMode) {
        store.putString(
            REPLAY_GAIN_KEY,
            when (mode) {
                ReplayGainMode.Off -> "off"
                ReplayGainMode.Track -> "track"
                ReplayGainMode.Album -> "album"
            },
        )
        _state.update { it.copy(replayGain = mode) }
    }

    fun setFolderSort(sort: FolderSort) {
        store.putString(
            FOLDER_SORT_KEY,
            when (sort) {
                FolderSort.Name -> "name"
                FolderSort.RecentlyAdded -> "recent"
                FolderSort.MostPlayed -> "most_played"
            },
        )
        _state.update { it.copy(folderSort = sort) }
    }

    fun setResumeOnReconnect(enabled: Boolean) {
        store.putString(RESUME_KEY, enabled.toString())
        _state.update { it.copy(resumeOnReconnect = enabled) }
    }

    fun setAmllEnabled(enabled: Boolean) {
        store.putString(AMLL_KEY, enabled.toString())
        _state.update { it.copy(amllEnabled = enabled) }
    }

    fun setMinimumDuration(durationMs: Long) {
        require(durationMs in LibraryFilter.durationChoicesMs)
        store.putString(MIN_DURATION_KEY, durationMs.toString())
        _state.update { it.copy(libraryFilter = it.libraryFilter.copy(minimumDurationMs = durationMs)) }
    }

    fun setFolderExcluded(path: String, excluded: Boolean) {
        val folder = path.trim('/')
        if (folder.isBlank()) return
        val current = state.value.libraryFilter
        val folders = if (excluded) current.excludedFolders + folder else current.excludedFolders - folder
        store.putStringList(EXCLUDED_FOLDERS_KEY, folders.sorted())
        _state.update { it.copy(libraryFilter = it.libraryFilter.copy(excludedFolders = folders)) }
    }

    private companion object {
        const val EXCLUDED_FOLDERS_KEY = "library.excluded_folders"
        const val MIN_DURATION_KEY = "library.minimum_duration_ms"
        const val APPEARANCE_KEY = "settings.appearance"
        const val REDUCE_MOTION_KEY = "settings.reduce_motion"
        const val REDUCE_TRANSPARENCY_KEY = "settings.reduce_transparency"
        const val ARTWORK_STYLE_KEY = "settings.artwork_style"
        const val INTERFACE_STYLE_KEY = "settings.interface_style"
        const val REPLAY_GAIN_KEY = "settings.replay_gain"
        const val FOLDER_SORT_KEY = "library.folder_sort"
        const val RESUME_KEY = "settings.resume_on_reconnect"
        const val CROSSFADE_KEY = "settings.crossfade_duration_ms"
        const val AMLL_KEY = "settings.amll_enabled"
    }
}
