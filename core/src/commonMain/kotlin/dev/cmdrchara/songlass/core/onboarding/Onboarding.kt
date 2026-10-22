package dev.cmdrchara.songlass.core.onboarding

import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The first-run welcome. It is shown once, to someone who has just installed the app; a person who
 * already has data (an update from an earlier version, a restored backup) never sees it.
 *
 * Its two keys use their own prefix, so they stay out of backups: whether this phone has been
 * welcomed says nothing about the data a backup carries.
 *
 * Pass a null [device] to switch it off (tests, probes and tools that build a graph without a UI).
 */
class Onboarding(private val store: KeyValueStore, device: DeviceInfo?) {
    /** The style that suits this phone, for the welcome to name; null when the welcome is off. */
    val recommendedStyle = device?.let(StyleRecommendation::forDevice)

    private val _visible = MutableStateFlow(false)
    val visible: StateFlow<Boolean> = _visible.asStateFlow()

    init {
        if (device != null) {
            when {
                store.getString(DONE_KEY) == "true" -> Unit
                // Started before and not finished: the welcome carries on where a restart left it.
                store.getString(STARTED_KEY) == "true" -> _visible.value = true
                hasData() -> store.putString(DONE_KEY, "true")
                else -> {
                    // A fresh install starts in its own platform's style, before anything is drawn.
                    store.updateStrings(emptySet(), buildMap {
                        put(STARTED_KEY, "true")
                        if (store.getString(AppSettings.INTERFACE_STYLE_KEY) == null) {
                            put(AppSettings.INTERFACE_STYLE_KEY, styleKey(StyleRecommendation.forDevice(device)))
                        }
                    })
                    _visible.value = true
                }
            }
        }
    }

    fun complete() {
        store.putString(DONE_KEY, "true")
        _visible.value = false
    }

    /** Shows the welcome again (debug tool); the next launch treats it as unfinished too. */
    fun restart() {
        store.updateStrings(removing = setOf(DONE_KEY), writing = mapOf(STARTED_KEY to "true"))
        _visible.value = true
    }

    private fun hasData(): Boolean {
        val keys = store.snapshot()?.keys
        // A store that cannot list its keys falls back to the entries every real user has by now.
        return keys?.any { !it.startsWith(PREFIX) } ?: (store.getString(AppSettings.INTERFACE_STYLE_KEY) != null)
    }

    private fun styleKey(style: dev.cmdrchara.songlass.core.settings.InterfaceStyle) = when (style) {
        dev.cmdrchara.songlass.core.settings.InterfaceStyle.LiquidGlass -> "liquid_glass"
        dev.cmdrchara.songlass.core.settings.InterfaceStyle.MaterialGlass -> "material_glass"
        dev.cmdrchara.songlass.core.settings.InterfaceStyle.Miuix -> "miuix"
    }

    companion object {
        private const val PREFIX = "onboarding."
        const val STARTED_KEY = "onboarding.started"
        const val DONE_KEY = "onboarding.done"
    }
}
