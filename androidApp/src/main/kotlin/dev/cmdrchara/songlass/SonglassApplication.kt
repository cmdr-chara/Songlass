package dev.cmdrchara.songlass

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dev.cmdrchara.songlass.playback.EqualizerProcessor
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.library.MediaStoreArtworkLoader
import dev.cmdrchara.songlass.library.MediaStoreMusicSource
import dev.cmdrchara.songlass.lyrics.EmbeddedLyricsSource
import dev.cmdrchara.songlass.lyrics.LrcLibLyricsSource
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

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val scope = MainScope()
        val equalizer = EqualizerProcessor()
        // The equalizer runs inside ExoPlayer's audio sink, so it works the same on every device
        // (android.media.audiofx.Equalizer bands and quality depend on the manufacturer).
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(equalizer))
                    .build()
        }
        val exoPlayer = ExoPlayer.Builder(this, renderers)
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
        graph = AppGraph(
            Media3AudioPlayer(exoPlayer, scope, equalizer), musicSource, SharedPreferencesStore(this), scope,
            localLyrics = EmbeddedLyricsSource(exoPlayer),
            remoteLyrics = LrcLibLyricsSource(this),
        )
    }
}
