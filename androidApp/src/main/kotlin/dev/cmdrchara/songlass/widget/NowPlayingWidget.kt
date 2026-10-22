package dev.cmdrchara.songlass.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.scale
import dev.cmdrchara.songlass.MainActivity
import dev.cmdrchara.songlass.R
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.artwork.CoverImages
import dev.cmdrchara.songlass.core.artwork.LatestWidgetRenderer
import dev.cmdrchara.songlass.core.artwork.WidgetPlaybackSnapshot
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import dev.cmdrchara.songlass.designsystem.theme.ArtworkTint
import dev.cmdrchara.songlass.playback.PlaybackService
import dev.cmdrchara.songlass.ui.artwork
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Home-screen widget with what is playing; drawn by [NowPlayingWidgets]. */
class NowPlayingWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val app = context.applicationContext as SonglassApplication
        NowPlayingWidgets.refresh(app)
    }
}

/**
 * Keeps the widgets in step with playback and draws them in the interface style chosen in the
 * app: Liquid Glass as a card in the cover's colour (like the app's tinted pages), Material Glass
 * as a Material You widget in the system's dynamic colours, Miuix as a neutral rounded card.
 * Small and wide layouts are picked by
 * the launcher from the widget's size.
 */
object NowPlayingWidgets {
    private data class Content(
        val title: String?,
        val subtitle: String?,
        val artworkKey: String?,
        val canReadArtwork: Boolean,
        val availabilityRevision: Long,
        val seed: Int,
        val playing: Boolean,
        val style: InterfaceStyle,
        val dark: Boolean,
    )

    private lateinit var scope: CoroutineScope
    private lateinit var renderer: LatestWidgetRenderer<Content, Rendered?>

    private data class Rendered(val ids: IntArray, val views: RemoteViews)

    /** Starts following playback; called once by the application. */
    fun observe(app: SonglassApplication, scope: CoroutineScope) {
        this.scope = scope
        val graph = app.graph
        renderer = LatestWidgetRenderer(scope,
            render = { render(app, it) },
            publish = { captured, rendered ->
                // State can change before its Flow observer runs. Recheck the current snapshot
                // as well as the renderer generation before sending metadata/actions to Android.
                if (rendered != null && captured == content(app)) {
                    AppWidgetManager.getInstance(app).updateAppWidget(rendered.ids, rendered.views)
                }
            },
        )
        scope.launch {
            combine(
                graph.controller.queue.map { it.current }.distinctUntilChanged(),
                graph.player.state.map { it.isPlaying }.distinctUntilChanged(),
                graph.settings.state.map { it.interfaceStyle }.distinctUntilChanged(),
                graph.artworkAvailability.revision,
            ) { _, _, _, _ -> content(app) }
                .distinctUntilChanged()
                .collect { renderer.submit(it) }
        }
    }

    /** Draws the widgets again (added to the home screen, or the system's light/dark mode changed). */
    fun refresh(app: SonglassApplication) {
        if (!::scope.isInitialized) return
        scope.launch { renderer.submit(content(app)) }
    }

    private fun content(app: SonglassApplication): Content {
        val graph = app.graph
        val entry = graph.controller.queue.value.current
        val playback = WidgetPlaybackSnapshot.from(entry, graph.player.state.value,
            artworkAvailable = entry?.track?.artworkKey?.let(app.artworkLoader::canRead) == true)
        val track = playback.track
        return Content(
            title = track?.title,
            subtitle = track?.artist,
            artworkKey = track?.artworkKey,
            canReadArtwork = playback.canReadArtwork,
            availabilityRevision = graph.artworkAvailability.revision.value,
            seed = track?.artwork()?.seed ?: 0,
            playing = playback.playing,
            style = graph.settings.state.value.interfaceStyle,
            dark = (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES,
        )
    }

    private suspend fun render(app: SonglassApplication, content: Content): Rendered? {
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, NowPlayingWidget::class.java))
        if (ids.isEmpty()) return null
        val cover = withContext(Dispatchers.IO) {
            content.artworkKey?.let { key ->
                (if (content.canReadArtwork) app.artworkLoader.loadBitmap(key, COVER_PX)
                else app.artworkLoader.cachedBitmap(key, COVER_PX)) ?: CoverImages.generative(content.seed, COVER_PX)
            }
        }
        val tint = if (content.style == InterfaceStyle.LiquidGlass) liquidTint(cover, content.dark) else null
        val small = build(app, content, cover, tint, wide = false)
        val wide = build(app, content, cover, tint, wide = true)
        return Rendered(ids, RemoteViews(mapOf(
            WidgetLayoutSizing.compactSize to small,
            WidgetLayoutSizing.wideSize(app, content.style,
                content.title ?: app.getString(R.string.widget_nothing_playing),
                content.subtitle ?: app.getString(R.string.app_name)) to wide,
        )))
    }

    private fun build(context: Context, content: Content, cover: Bitmap?, tint: ArtworkTint?, wide: Boolean): RemoteViews {
        val layout = WidgetLayoutSizing.layout(content.style, wide)
        val views = RemoteViews(context.packageName, layout)
        if (!wide && content.style == InterfaceStyle.LiquidGlass) WidgetLayoutSizing.applyCompactLiquid(context, views,
            content.title ?: context.getString(R.string.widget_nothing_playing))
        if (cover != null) views.setImageViewBitmap(R.id.cover, cover) else views.setImageViewResource(R.id.cover, R.mipmap.ic_launcher)
        views.setTextViewText(R.id.title, content.title ?: context.getString(R.string.widget_nothing_playing))
        views.setImageViewResource(R.id.play, if (content.playing) R.drawable.widget_pause else R.drawable.widget_play)
        views.setContentDescription(R.id.play, context.getString(if (content.playing) R.string.widget_pause else R.string.widget_play))
        views.setContentDescription(R.id.root, listOfNotNull(content.title ?: context.getString(R.string.widget_nothing_playing), content.subtitle).joinToString(", "))
        views.setOnClickPendingIntent(R.id.root, openApp(context))
        views.setOnClickPendingIntent(R.id.play, mediaKey(context, if (content.playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY))
        if (wide) {
            views.setTextViewText(R.id.subtitle, content.subtitle ?: context.getString(R.string.app_name))
            views.setOnClickPendingIntent(R.id.next, mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT))
            views.setOnClickPendingIntent(R.id.previous, mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        }
        if (tint != null) {
            // Liquid: the card takes the cover's colour; text and buttons the matching contrast.
            views.setColorStateList(R.id.root, "setBackgroundTintList", ColorStateList.valueOf(tint.background.toArgb()))
            views.setColorStateList(R.id.play, "setBackgroundTintList", ColorStateList.valueOf(tint.activeFill.toArgb()))
            views.setColorStateList(R.id.play, "setImageTintList", ColorStateList.valueOf(tint.onActiveFill.toArgb()))
            if (wide) {
                views.setTextColor(R.id.title, tint.content.toArgb())
                views.setTextColor(R.id.subtitle, tint.secondaryContent.toArgb())
                views.setColorStateList(R.id.next, "setImageTintList", ColorStateList.valueOf(tint.content.toArgb()))
                views.setColorStateList(R.id.previous, "setImageTintList", ColorStateList.valueOf(tint.content.toArgb()))
            }
        }
        return views
    }

    /** The app's tint for a cover (see ArtworkTint), from a few samples of the image. */
    private fun liquidTint(cover: Bitmap?, dark: Boolean): ArtworkTint {
        val samples = cover?.scale(8, 8)?.let { small ->
            buildList { for (y in 0 until 8) for (x in 0 until 8) add(Color(small.getPixel(x, y))) }
        }
        val seed = samples?.let(ArtworkTint::seedOf) ?: Color(BRAND_PURPLE)
        return ArtworkTint.from(seed, dark)
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** A media key sent to the playback service, as the media notification does. */
    private fun mediaKey(context: Context, keyCode: Int): PendingIntent = PendingIntent.getForegroundService(
        context, keyCode,
        Intent(Intent.ACTION_MEDIA_BUTTON)
            .putExtra(dev.cmdrchara.songlass.playback.HeadsetButtons.LOCAL_WIDGET, true)
            .setComponent(ComponentName(context, PlaybackService::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Small enough for two layouts in one update (Binder limit), sharp enough for 90 dp. */
    private const val COVER_PX = 256
    private const val BRAND_PURPLE = 0xFF9500FF
}
