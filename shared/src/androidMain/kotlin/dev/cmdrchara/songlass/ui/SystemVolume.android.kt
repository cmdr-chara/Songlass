package dev.cmdrchara.songlass.ui

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt

@Composable
actual fun rememberSystemVolume(): SystemVolume? {
    val context = LocalContext.current
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val max = remember(audio) { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    fun read() = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    var value by remember { mutableFloatStateOf(read()) }
    // Hardware buttons and other apps change the volume too; system settings report it.
    DisposableEffect(audio) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                value = read()
            }
        }
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return SystemVolume(value) { fraction ->
        value = fraction
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction * max).roundToInt(), 0)
    }
}
