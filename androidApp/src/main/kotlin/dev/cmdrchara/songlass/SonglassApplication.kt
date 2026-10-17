package dev.cmdrchara.songlass

import android.app.Application
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.library.MediaStoreArtworkLoader
import dev.cmdrchara.songlass.library.MediaStoreMusicSource
import dev.cmdrchara.songlass.playback.Media3AudioPlayer
import kotlinx.coroutines.MainScope

/** Creates the process-wide [AppGraph] with real playback and the device's music. */
class SonglassApplication : Application() {
    lateinit var graph: AppGraph
        private set
    lateinit var musicSource: MediaStoreMusicSource
        private set
    lateinit var artworkLoader: MediaStoreArtworkLoader
        private set

    override fun onCreate() {
        super.onCreate()
        val scope = MainScope()
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            // Pause when headphones are unplugged (HIG "Playing audio", same rule on Android).
            .setHandleAudioBecomingNoisy(true)
            .build()
        musicSource = MediaStoreMusicSource(this)
        artworkLoader = MediaStoreArtworkLoader(this)
        graph = AppGraph(Media3AudioPlayer(exoPlayer, scope), musicSource, scope)
    }
}
