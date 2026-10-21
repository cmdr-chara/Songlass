package dev.cmdrchara.songlass.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaRouter2
import android.os.Handler
import android.os.Looper
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.player.AudioPlayer
import dev.cmdrchara.songlass.core.player.AudioOutputDevice
import dev.cmdrchara.songlass.core.player.AudioOutputKind
import dev.cmdrchara.songlass.core.player.AudioOutputRoute
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
        val devices = runCatching { manager.getAudioDevicesForAttributes(attributes) }.getOrDefault(emptyList())
        val route = AudioOutputRoute(devices.map { device ->
            val kind = outputKind(device.type)
            // Preserve the existing persisted keys, including the product-name fallback used
            // by less common built-in routes which do not expose an address.
            val builtin = device.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER || device.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            val product = device.productName?.toString()?.trim().orEmpty()
            val identity = if (builtin) "builtin:${device.type}" else "${device.type}:${device.address.ifBlank { product }}"
            // Session ids change on reconnect. Ambiguous unnamed devices use manual sound.
            AudioOutputDevice(
                identity = identity.takeUnless { !builtin && device.address.isBlank() && product.isBlank() },
                name = product.takeIf(String::isNotBlank),
                kind = kind,
            )
        })
        equalizer.setOutput(route.equalizerOutput)
        // The UI and the equalizer consume this same selected route, including unknown and
        // multiple-route states. Never query the connected-device list again from a composable.
        player.setOutputRoute(route)
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

private fun outputKind(type: Int): AudioOutputKind = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AudioOutputKind.BuiltIn
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_AUX_LINE -> AudioOutputKind.Wired
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_BLE_SPEAKER,
    AudioDeviceInfo.TYPE_HEARING_AID -> AudioOutputKind.Bluetooth
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET -> AudioOutputKind.Usb
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC,
    AudioDeviceInfo.TYPE_HDMI_EARC -> AudioOutputKind.Hdmi
    else -> AudioOutputKind.Other
}
