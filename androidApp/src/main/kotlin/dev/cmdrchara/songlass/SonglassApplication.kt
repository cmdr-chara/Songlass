package dev.cmdrchara.songlass

import android.app.Application
import android.content.res.Configuration
import dev.cmdrchara.songlass.widget.NowPlayingWidgets
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dev.cmdrchara.songlass.playback.EqualizerProcessor
import dev.cmdrchara.songlass.playback.PlaybackAudioAttributes
import dev.cmdrchara.songlass.playback.PlayerVoice
import androidx.media3.exoplayer.ExoPlayer
import dev.cmdrchara.songlass.library.MediaStoreArtworkLoader
import dev.cmdrchara.songlass.library.MediaStoreMusicSource
import dev.cmdrchara.songlass.lyrics.EmbeddedLyricsSource
import dev.cmdrchara.songlass.lyrics.LrcLibLyricsSource
import dev.cmdrchara.songlass.lyrics.AmllLyricsSource
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
        LauncherIcons.watch(this)
        val scope = MainScope()
        // The equalizer runs inside ExoPlayer's audio sink, so it works the same on every device
        // (android.media.audiofx.Equalizer bands and quality depend on the manufacturer).
        // Crossfade overlaps two tracks on two players, each with its own equalizer instance and
        // the same full-quality processing. Only the first player handles audio focus: two
        // players asking for it would take it from one another in the middle of an overlap.
        fun buildVoice(handleAudioFocus: Boolean): PlayerVoice {
            val equalizer = EqualizerProcessor()
            val renderers = object : DefaultRenderersFactory(this) {
                override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                    DefaultAudioSink.Builder(context)
                        .setEnableFloatOutput(enableFloatOutput)
                        .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                        .setAudioProcessors(arrayOf(equalizer))
                        .build()
            }
            val player = ExoPlayer.Builder(this, renderers)
                .setAudioAttributes(PlaybackAudioAttributes, handleAudioFocus)
                // Pause when headphones are unplugged (HIG "Playing audio", same rule on Android).
                .setHandleAudioBecomingNoisy(true)
                .build()
            return PlayerVoice(player, equalizer, focusOwner = handleAudioFocus)
        }
        val first = buildVoice(handleAudioFocus = true)
        val exoPlayer = first.player
        val store = SharedPreferencesStore(this)
        musicSource = MediaStoreMusicSource(this, dev.cmdrchara.songlass.core.library.TrackIdentityRegistry(store))
        // The second player only exists while crossfade is on: Media3AudioPlayer builds it on demand.
        val audioPlayer = Media3AudioPlayer(exoPlayer, scope, first.equalizer, standbyFactory = { buildVoice(handleAudioFocus = false) })
        val tagEditor = dev.cmdrchara.songlass.library.TagLibFileTagEditor(this,
            withFileWrite = { uri, write ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    graph.controller.prepareForFileWrite()
                    try { audioPlayer.withFileWriteUri(uri, write) }
                    catch (failure: Throwable) {
                        // A rejected witness may mean this URI now names another recording.
                        // Invalidate stale queue/marker opens before exposing the failure to UI.
                        // SourceChanged is notified once by the outer editor operation, after
                        // releasing its mutex. Other guard failures invalidate here immediately.
                        if (failure !is dev.cmdrchara.songlass.core.model.TagEditSourceChangedException) {
                            graph.onFileWriteFailure()
                        }
                        throw failure
                    }
                }
            },
            isCurrentSelection = { selected ->
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    graph.source.hasAccess.value && graph.hasCompleteLibraryScan.value &&
                        !graph.isLibraryLoading.value && graph.libraryRefreshFailure.value == null &&
                        dev.cmdrchara.songlass.core.tags.TagEditSelection.isCurrent(selected, graph.allTracks.value)
                }
            },
            onSourceChanged = {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    graph.onFileWriteFailure()
                }
            },
        ) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { graph.refreshLibrary() } }
        graph = AppGraph(
            audioPlayer, musicSource, store, scope,
            // Reads the ExoPlayer of the current track, which changes when crossfade swaps players.
            localLyrics = EmbeddedLyricsSource { audioPlayer.exoPlayer },
            remoteLyrics = LrcLibLyricsSource(this),
            amllLyrics = AmllLyricsSource(this),
            tagEditor = tagEditor,
            device = dev.cmdrchara.songlass.core.onboarding.DeviceInfo.android(android.os.Build.MANUFACTURER, android.os.Build.BRAND),
            applyLauncherIcon = { icon -> LauncherIcons.apply(this, icon) },
        )
        // The loader uses the graph's verified snapshot and current read readiness.
        artworkLoader = MediaStoreArtworkLoader(this)
        dev.cmdrchara.songlass.playback.watchOutputEqualizer(this, graph.equalizer, graph.player, scope)
        watchAudioOutputs()
        NowPlayingWidgets.observe(this, scope)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Liquid widgets compute their colours for light or dark mode.
        NowPlayingWidgets.refresh(this)
    }

    /**
     * Reports headphones, headsets and car audio (Bluetooth A2DP/LE, wired, USB, hearing aids)
     * as they connect, for resuming playback. The callback first lists the devices already
     * connected; those are not new connections and are skipped.
     */
    private fun watchAudioOutputs() {
        val audio = getSystemService(AudioManager::class.java)
        audio.registerAudioDeviceCallback(object : AudioDeviceCallback() {
            private var initial = true

            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                if (initial) {
                    initial = false
                    return
                }
                if (addedDevices.any { it.isSink && it.type in PERSONAL_OUTPUTS }) graph.onOutputConnected()
            }
        }, Handler(Looper.getMainLooper()))
    }

    private companion object {
        val PERSONAL_OUTPUTS = setOf(
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
