package dev.cmdrchara.songlass.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import dev.cmdrchara.songlass.R

/**
 * The system media notification for [PlaybackService]: the app's own status icon and, as the
 * secondary line, "artist · album" when both are known. Everything else is unchanged: the large
 * cover through [EmbeddedArtworkBitmapLoader], previous / play-pause / next, the progress bar the
 * system draws from the session, and public visibility on the lock screen.
 *
 * Android 13+ draws the shade and lock-screen card itself from the session metadata (title and
 * artist), so this text reaches the notification template and its accessibility label rather than
 * that card. No background or accent colour is forced: that appearance belongs to the system, and
 * Songlass must not imitate another player's chrome.
 */
@OptIn(UnstableApi::class)
internal class SonglassMediaNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    private val appContext = context.applicationContext

    init {
        // Otherwise the status icon is Media3's generic play glyph; this is the launcher's own
        // heart and ten-bar mark, monochrome so the system tints it.
        setSmallIcon(R.drawable.ic_stat_songlass)
    }

    /** Artist and album together when available; a notification has room for one secondary line. */
    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        val artist = metadata.artist?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val album = metadata.albumTitle?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            artist != null && album != null ->
                appContext.getString(R.string.notification_media_subtitle, artist, album)
            else -> artist ?: album
        }
    }
}
