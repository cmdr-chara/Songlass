package dev.cmdrchara.songlass.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import dev.cmdrchara.songlass.SonglassApplication

/**
 * Debug builds only: shows the first-run welcome again without clearing any data.
 *
 * adb shell am broadcast -a dev.cmdrchara.songlass.debug.WELCOME \
 *   -n dev.cmdrchara.songlass/.debug.WelcomeReceiver
 */
class WelcomeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = (context.applicationContext as SonglassApplication).graph
        Handler(Looper.getMainLooper()).post { graph.onboarding.restart() }
    }
}
