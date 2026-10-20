package dev.cmdrchara.songlass.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Handler
import android.os.Looper
import dev.cmdrchara.songlass.core.equalizer.EqOutput
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.player.AudioPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Uses the selected media route, never the first merely connected Bluetooth device. */
fun watchOutputEqualizer(context: Context, equalizer: Equalizer, player: AudioPlayer, scope: CoroutineScope) {
    val manager = context.getSystemService(AudioManager::class.java)
    val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    fun refresh() {
        val device = runCatching { manager.getAudioDevicesForAttributes(attributes).singleOrNull() }.getOrNull()
        val output = device?.let {
            val builtin = it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val product = it.productName?.toString()?.trim().orEmpty()
            val identity = if (builtin) "builtin:${it.type}" else "${it.type}:${it.address.ifBlank { product }}"
            // Session ids change on reconnect. Ambiguous unnamed devices use manual sound.
            if (!builtin && it.address.isBlank() && product.isBlank()) null
            else EqOutput(identity, product.ifBlank { identity })
        }
        equalizer.setOutput(output)
    }
    refresh()
    manager.registerAudioDeviceCallback(object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = refresh()
    }, Handler(Looper.getMainLooper()))
    // Manual system-output transfers can leave the connected device list unchanged.
    MediaRouter2.getInstance(context).registerControllerCallback(context.mainExecutor, object : MediaRouter2.ControllerCallback() {
        override fun onControllerUpdated(controller: MediaRouter2.RoutingController) = refresh()
    })
    scope.launch {
        while (isActive) {
            delay(2000)
            if (player.state.value.playWhenReady) refresh()
        }
    }
}
