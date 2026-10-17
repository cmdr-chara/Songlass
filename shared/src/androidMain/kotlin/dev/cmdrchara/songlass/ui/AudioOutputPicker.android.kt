package dev.cmdrchara.songlass.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaRouter2
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

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
