package dev.cmdrchara.songlass.core.settings

import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Persistent preferences shared by all interfaces and platforms. */
class AppSettings(private val store: KeyValueStore) {
    private val _state = MutableStateFlow(
        AppPreferences(
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
                else -> InterfaceStyle.LiquidGlass
            },
        ),
    )

    val state: StateFlow<AppPreferences> = _state.asStateFlow()

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
            },
        )
        _state.update { it.copy(interfaceStyle = style) }
    }

    private companion object {
        const val APPEARANCE_KEY = "settings.appearance"
        const val REDUCE_MOTION_KEY = "settings.reduce_motion"
        const val REDUCE_TRANSPARENCY_KEY = "settings.reduce_transparency"
        const val ARTWORK_STYLE_KEY = "settings.artwork_style"
        const val INTERFACE_STYLE_KEY = "settings.interface_style"
    }
}
