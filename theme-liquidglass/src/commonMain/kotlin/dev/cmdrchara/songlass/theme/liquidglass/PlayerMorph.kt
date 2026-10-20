package dev.cmdrchara.songlass.theme.liquidglass

import androidx.compose.runtime.State
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect

/**
 * Shared state of the Now Playing transition, as in iOS: the player grows out of the mini
 * player and the page below recedes like a stacked card. The mini player reports where it is,
 * the sheet publishes how open it is, and the scaffold scales and dims the page accordingly.
 * Each theme root owns its state: a disposed/recreated Activity must not affect another root.
 */
internal class PlayerMorph {
    /** Mini player bounds in window coordinates, the shape the player expands from. */
    var source by mutableStateOf<Rect?>(null)

    /** The mini player's cover thumbnail in window coordinates, where the big cover flies from. */
    var thumb by mutableStateOf<Rect?>(null)

    /** The transition owns the image; the real thumbnail stays measured but is not drawn. */
    var artworkOwner by mutableStateOf<State<Boolean>?>(null)
    val artworkInFlight: Boolean get() = artworkOwner?.value == true

    /** 0 = closed, 1 = fully open; lowered while the player is dragged down. */
    var recede by mutableFloatStateOf(0f)
}

/** Shared within one window, never process-global across Activity lifecycles or previews. */
internal val LocalPlayerMorph = staticCompositionLocalOf<PlayerMorph> { error("PlayerMorph requires LiquidGlassTheme") }
