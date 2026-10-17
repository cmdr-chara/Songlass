package dev.cmdrchara.songlass.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.output_headphones
import dev.cmdrchara.songlass.shared.resources.output_this_phone
import org.jetbrains.compose.resources.stringResource

@Composable
actual fun rememberAudioOutputPicker(): (() -> Unit)? {
    val context = LocalContext.current
    return remember(context) {
        {
            val shown = if (Build.VERSION.SDK_INT >= 34) {
                try {
                    MediaRouter2.getInstance(context).showSystemOutputSwitcher()
                } catch (_: RuntimeException) {
                    false
                }
            } else false
            if (!shown && !context.openAudioSettings(Settings.Panel.ACTION_VOLUME)) {
                context.openAudioSettings(Settings.ACTION_SOUND_SETTINGS)
            }
            Unit
        }
    }
}

private fun Context.openAudioSettings(action: String): Boolean = try {
    startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (_: ActivityNotFoundException) {
    false
} catch (_: SecurityException) {
    false
}

/**
 * The device media plays on, from [AudioManager.getAudioDevicesForAttributes] (API 33), kept
 * current with an [AudioDeviceCallback] as headphones and Bluetooth devices come and go.
 */
@Composable
actual fun rememberAudioOutputName(): String? {
    val context = LocalContext.current
    val thisPhone = stringResource(Res.string.output_this_phone)
    val headphones = stringResource(Res.string.output_headphones)
    var device by remember { mutableStateOf<AudioDeviceInfo?>(null) }
    DisposableEffect(context) {
        val audioManager = context.getSystemService(AudioManager::class.java)
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        fun refresh() {
            device = runCatching { audioManager.getAudioDevicesForAttributes(attributes).firstOrNull() }.getOrNull()
        }
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }
        refresh()
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    val current = device ?: return null
    val product = current.productName?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    return when (current.type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> thisPhone
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET -> product ?: headphones
        else -> product ?: thisPhone
    }
}
