package dev.cmdrchara.songlass.playback

import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import dev.cmdrchara.songlass.core.player.FocusChange

/** One native request for the whole session, never one per overlapping decoder. Main thread only. */
class AndroidAudioFocus(
    context: android.content.Context,
    private val handler: Handler,
    private val changed: (FocusChange) -> Unit,
    private val callChanged: (Boolean) -> Unit,
    private val noisy: () -> Unit,
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(AudioManager::class.java)
    private val noisyReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
            if (!released && intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) noisy()
        }
    }
    private var request: AudioFocusRequest? = null
    private var granted = false
    private var released = false
    val callActive: Boolean get() = when (manager.mode) {
        AudioManager.MODE_RINGTONE, AudioManager.MODE_IN_CALL, AudioManager.MODE_IN_COMMUNICATION,
        AudioManager.MODE_CALL_SCREENING -> true
        else -> false
    }
    private val modeListener = AudioManager.OnModeChangedListener {
        if (!released) callChanged(callActive)
    }
    init {
        manager.addOnModeChangedListener({ handler.post(it) }, modeListener)
        // ExoPlayer unregisters its noisy receiver while paused. A disconnect must also invalidate
        // a saved focus/call resume intent during that pause.
        appContext.registerReceiver(noisyReceiver, android.content.IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            android.content.Context.RECEIVER_NOT_EXPORTED)
    }

    fun acquire(): Boolean {
        if (released) return false
        if (granted) return true
        abandon()
        lateinit var candidate: AudioFocusRequest
        candidate = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            // Receive CAN_DUCK ourselves so both voices use the selected player gain exactly once.
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ value ->
                if (!released && request === candidate) {
                    granted = value == AudioManager.AUDIOFOCUS_GAIN
                    val change = when (value) {
                        AudioManager.AUDIOFOCUS_GAIN -> FocusChange.Gain
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> FocusChange.Duck
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> FocusChange.TransientLoss
                        AudioManager.AUDIOFOCUS_LOSS -> FocusChange.PermanentLoss
                        else -> null
                    }
                    if (change != null) changed(change)
                }
            }, handler).build()
        request = candidate
        granted = manager.requestAudioFocus(candidate) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted) abandon()
        return granted
    }

    fun abandon() {
        val old = request
        request = null
        granted = false
        if (old != null) manager.abandonAudioFocusRequest(old)
    }

    fun release() {
        if (released) return
        released = true
        abandon()
        manager.removeOnModeChangedListener(modeListener)
        appContext.unregisterReceiver(noisyReceiver)
    }
}
