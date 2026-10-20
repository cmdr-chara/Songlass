package dev.cmdrchara.songlass

import android.Manifest
import android.animation.ValueAnimator
import android.content.ComponentName
import android.app.SearchManager
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import androidx.lifecycle.lifecycleScope
import dev.cmdrchara.songlass.core.browse.play
import dev.cmdrchara.songlass.playback.browseTree
import kotlinx.coroutines.launch
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.cmdrchara.songlass.playback.PlaybackService

class MainActivity : ComponentActivity() {
    private val app get() = application as SonglassApplication
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val requestAudioPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { app.musicSource.refreshAccess() }

    private val recoveryConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) lifecycleScope.launch { recoverTagSave(requestConsent = false) }
    }

    private suspend fun recoverTagSave(requestConsent: Boolean) {
        val editor = app.graph.tagEditor as? dev.cmdrchara.songlass.library.TagLibFileTagEditor ?: return
        try {
            if (editor.pendingRecoveryUri() == null) return
            editor.recoverInterruptedSave()
            app.graph.refreshLibrary()
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (denied: SecurityException) {
            if (requestConsent) editor.pendingRecoveryUri()?.let { uri ->
                try {
                    val sender = MediaStore.createWriteRequest(contentResolver, listOf(uri)).intentSender
                    recoveryConsent.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
                } catch (failure: Exception) { android.util.Log.e("TagEditor", "Recovery permission unavailable", failure) }
            }
        } catch (failure: Exception) { android.util.Log.e("TagEditor", "Interrupted save recovery pending", failure) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hand over from the launch splash: the icon swells and fades while the splash dissolves
        // into the first screen, which plays its own entrance. Instant when animations are off.
        splashScreen.setOnExitAnimationListener { view ->
            if (!ValueAnimator.areAnimatorsEnabled()) return@setOnExitAnimationListener view.remove()
            view.iconView?.animate()?.scaleX(1.18f)?.scaleY(1.18f)?.alpha(0f)?.setDuration(SPLASH_EXIT_MS)?.start()
            view.animate().alpha(0f).setDuration(SPLASH_EXIT_MS).withEndAction(view::remove).start()
        }
        lifecycleScope.launch { recoverTagSave(requestConsent = true) }
        handleVoiceRequest(intent)
        enableEdgeToEdge()
        setContent {
            App(
                graph = app.graph,
                onRequestLibraryAccess = { requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                artworkLoader = app.artworkLoader,
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceRequest(intent)
    }

    /** "Play … on Songlass" from an assistant: the query goes through the same rules as the car's. */
    private fun handleVoiceRequest(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        val query = intent.getStringExtra(SearchManager.QUERY).orEmpty()
        lifecycleScope.launch {
            if (app.graph.library.value.tracks.isEmpty()) app.graph.refreshLibrary()
            browseTree(this@MainActivity, app.graph).resolveQuery(query)?.let(app.graph.controller::play)
        }
    }

    override fun onStart() {
        super.onStart()
        app.musicSource.refreshAccess()
        // Binding a controller starts the session service, which then shows the media
        // notification and keeps playback alive in the background.
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    private companion object {
        const val SPLASH_EXIT_MS = 260L
    }
}
