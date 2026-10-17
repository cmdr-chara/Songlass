package dev.cmdrchara.songlass

import android.Manifest
import android.content.ComponentName
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(
                graph = app.graph,
                onRequestLibraryAccess = { requestAudioPermission.launch(Manifest.permission.READ_MEDIA_AUDIO) },
                artworkLoader = app.artworkLoader,
            )
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
}
