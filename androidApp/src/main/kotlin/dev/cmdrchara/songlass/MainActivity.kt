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
import dev.cmdrchara.songlass.playback.awaitBrowsableLibrary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    private var voiceRequest: Job? = null
    private var musicAccessRecovery: MusicAccessRecovery? = null

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
        // A restored Activity must not replay its original launch query over newer playback.
        // New voice intents continue through onNewIntent, independently of saved state.
        if (savedInstanceState == null) handleVoiceRequest(intent)
        enableEdgeToEdge()
        setContent {
            App(
                graph = app.graph,
                onRequestLibraryAccess = ::requestLibraryAccess,
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
        voiceRequest?.cancel()
        app.musicSource.refreshAccess()
        val graph = app.graph
        val acceptedRevision = graph.controller.beginAsyncPlaybackRequest()
        voiceRequest = lifecycleScope.launch {
            try {
                // Share the process-owned scan used by browser controls. An empty filtered
                // result is valid; a retained snapshot from a failed scan is not playable.
                awaitBrowsableLibrary(graph)
                ensureActive()
                if (graph.controller.transportIntentRevision != acceptedRevision) return@launch
                browseTree(this@MainActivity, graph).resolveQuery(query)?.let(graph.controller::play)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: LibrarySnapshotUnavailableException) {
                // The normal library page already offers access/retry recovery. Do not log
                // the voice query, which can contain private music or collection names.
                android.util.Log.w("VoicePlayback", "Library unavailable for requested playback")
            }
        }
    }

    private fun requestLibraryAccess() {
        val recovery = musicAccessRecovery ?: MusicAccessRecovery(
            this,
            launchRequest = { requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
            refreshAccess = app.musicSource::refreshAccess,
        ).also { musicAccessRecovery = it }
        recovery.requestAccess()
    }

    override fun onStart() {
        super.onStart()
        // Binding a controller starts the session service, which then shows the media
        // notification and keeps playback alive in the background.
        controllerFuture = MediaController.Builder(this, SessionToken(this, ComponentName(this, PlaybackService::class.java))).buildAsync()
    }

    override fun onStop() {
        controllerFuture?.let(MediaController::releaseFuture)
        controllerFuture = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Returning from app settings, permission UI or a multi-window pause must sample
        // actual access again. The result callback also refreshes it immediately.
        app.musicSource.refreshAccess()
    }

    override fun onDestroy() {
        musicAccessRecovery?.close()
        musicAccessRecovery = null
        super.onDestroy()
    }

    private companion object {
        const val SPLASH_EXIT_MS = 260L
    }
}
