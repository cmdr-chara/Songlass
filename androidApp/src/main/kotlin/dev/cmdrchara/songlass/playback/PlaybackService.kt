package dev.cmdrchara.songlass.playback

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.cmdrchara.songlass.SonglassApplication

/** Hosts the MediaSession so playback continues in the background with system controls. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val graph = (application as SonglassApplication).graph
        val player = (graph.player as Media3AudioPlayer).exoPlayer
        session = MediaSession.Builder(this, QueueForwardingPlayer(player, graph.controller)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        // The player belongs to the application graph, so only the session is released here.
        session?.release()
        session = null
        super.onDestroy()
    }
}
