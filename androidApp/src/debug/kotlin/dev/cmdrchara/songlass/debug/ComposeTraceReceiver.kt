package dev.cmdrchara.songlass.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Trace
import android.util.Log
import androidx.compose.runtime.Composer
import androidx.compose.runtime.CompositionTracer
import androidx.compose.runtime.InternalComposeTracingApi

/**
 * Debug builds only: forwards the composition trace markers the Compose compiler already emits
 * to android.os.Trace, so atrace shows which composables recompose. No extra dependency.
 *
 * adb shell am broadcast -a dev.cmdrchara.songlass.debug.COMPOSE_TRACE \
 *   -n dev.cmdrchara.songlass/.debug.ComposeTraceReceiver --ez on true
 */
@OptIn(InternalComposeTracingApi::class)
class ComposeTraceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val on = intent.getBooleanExtra("on", true)
        Composer.setTracer(if (on) SectionTracer else null)
        Log.i("ComposeTrace", "composition tracing ${if (on) "on" else "off"}")
    }

    private object SectionTracer : CompositionTracer {
        override fun isTraceInProgress(): Boolean = Trace.isEnabled()
        override fun traceEventStart(key: Int, dirty1: Int, dirty2: Int, info: String) {
            // Section names are limited to 127 characters; keep the function name, drop the source path.
            Trace.beginSection(info.substringBefore(" (").take(127))
        }
        override fun traceEventEnd() = Trace.endSection()
    }
}
