package dev.cmdrchara.songlass.playback

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import dev.cmdrchara.songlass.core.playback.supportsXiaomiMediaIsland
import dev.cmdrchara.songlass.core.playback.xiaomiMediaIslandPayload

/**
 * MediaStyle/MediaSession is Xiaomi's documented automatic music-island integration. Keep that
 * provider intact, decorating both its immediate result and later artwork callbacks with the
 * media-only drag/share extension on ROMs advertising protocol 3+. No overlay or focus permission.
 */
@UnstableApi
internal class XiaomiMediaNotificationProvider(context: Context) : MediaNotification.Provider {
    private val delegate = SonglassMediaNotificationProvider(context)
    private val resolver = context.applicationContext.contentResolver
    private var creationRevision = 0L

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification {
        // Capture the metadata belonging to this notification. Reading the live session in a
        // delayed cover callback could attach the next song's share data to the previous cover.
        val revision = ++creationRevision
        val player = mediaSession.player
        val payload = if (supportsExtension() && player.isCommandAvailable(Player.COMMAND_GET_METADATA)) {
            player.mediaMetadata.let { xiaomiMediaIslandPayload(it.title?.toString(), it.artist?.toString(), it.albumTitle?.toString()) }
        } else null
        fun decorate(value: MediaNotification): MediaNotification {
            val extras = value.notification.extras ?: Bundle().also { value.notification.extras = it }
            if (payload == null) extras.remove(MEDIA_ISLAND_EXTRA) else extras.putString(MEDIA_ISLAND_EXTRA, payload)
            return value
        }
        return decorate(delegate.createNotification(mediaSession, mediaButtonPreferences, actionFactory) {
            // A newer notification also invalidates an old cover callback when the new item has no cover.
            if (creationRevision == revision) onNotificationChangedCallback.onNotificationChanged(decorate(it))
        })
    }

    private fun supportsExtension(): Boolean {
        if (!Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) return false
        val protocol = try {
            Settings.System.getInt(resolver, "notification_focus_protocol", 0)
        } catch (_: SecurityException) { 0 }
        return supportsXiaomiMediaIsland(Build.MANUFACTURER, protocol)
    }

    override fun handleCustomCommand(session: MediaSession, action: String, extras: Bundle): Boolean =
        delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo = delegate.notificationChannelInfo

    companion object {
        const val MEDIA_ISLAND_EXTRA = "miui.focus.param.media"
    }
}
