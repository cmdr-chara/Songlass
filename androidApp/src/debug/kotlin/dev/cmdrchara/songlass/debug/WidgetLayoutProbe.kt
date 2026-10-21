package dev.cmdrchara.songlass.debug

import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import dev.cmdrchara.songlass.R
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import dev.cmdrchara.songlass.widget.WidgetLayoutSizing
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Actual Android XML/RemoteViews measurement. Execution requires a working Android runtime. */
internal object WidgetLayoutProbe {
    fun run(context: Context) {
        check(Looper.myLooper() == Looper.getMainLooper())
        var checked = 0
        val labels = listOf(
            "Un titolo molto lungo con accenti: È già l’ora della musica" to "Artista e raccolta con un nome molto lungo",
            "音楽の長いタイトル 🎵 Étoile Ångström naïve" to "作曲者と演奏家 🎼",
            "عنوان موسيقي طويل لاختبار النص وإمكانية القراءة" to "اسم الفنان والألبوم الطويل",
        )
        for (scale in listOf(1f, 1.5f, 2f)) for (night in listOf(false, true)) for (rtl in listOf(false, true)) {
            val config = Configuration(context.resources.configuration).apply {
                fontScale = scale
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
                setLayoutDirection(if (rtl) Locale.forLanguageTag("ar") else Locale.ITALIAN)
            }
            val configured = context.createConfigurationContext(config)
            for (style in InterfaceStyle.entries) for ((title, subtitle) in labels) {
                val wide = WidgetLayoutSizing.wideSize(configured, style, title, subtitle)
                for ((size, expanded) in listOf(WidgetLayoutSizing.compactSize to false, wide to true)) {
                    val views = RemoteViews(context.packageName, WidgetLayoutSizing.layout(style, expanded))
                    if (expanded || style == InterfaceStyle.LiquidGlass) views.setTextViewText(R.id.title, title)
                    if (expanded) views.setTextViewText(R.id.subtitle, subtitle)
                    if (!expanded && style == InterfaceStyle.LiquidGlass) {
                        WidgetLayoutSizing.applyCompactLiquid(configured, views, title)
                    }
                    val root = views.apply(configured, FrameLayout(configured)) as ViewGroup
                    root.layoutDirection = config.layoutDirection
                    val density = configured.resources.displayMetrics.density
                    val width = ceil(size.width * density).toInt()
                    val height = ceil(size.height * density).toInt()
                    root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, width, height)
                    val description = "$style font=$scale night=$night rtl=$rtl wide=$expanded size=$size title=$title"
                    val bounds = Rect(0, 0, width, height)
                    val buttons = if (expanded) listOf(R.id.previous, R.id.play, R.id.next) else listOf(R.id.play)
                    for (id in buttons) {
                        val button = checkNotNull(root.findViewById<View>(id))
                        check(button.width >= (48 * density).roundToInt() && button.height >= (48 * density).roundToInt()) {
                            "Small transport target: $description id=$id"
                        }
                        check(bounds.contains(root.boundsOf(button))) { "Clipped transport: $description id=$id" }
                    }
                    for (id in listOf(R.id.title, R.id.subtitle)) {
                        val text = root.findViewById<TextView>(id) ?: continue
                        check(text.width > 0 && bounds.contains(root.boundsOf(text))) { "Text outside widget: $description" }
                        val layout = checkNotNull(text.layout)
                        check(layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom + 1) {
                            "Clipped text height: $description"
                        }
                    }
                    if (!expanded && style == InterfaceStyle.LiquidGlass) {
                        val caption = root.boundsOf(root.findViewById(R.id.title))
                        val play = root.boundsOf(root.findViewById(R.id.play))
                        val scrim = root.boundsOf(root.findViewById(R.id.widget_scrim))
                        check(!Rect.intersects(caption, play)) { "Compact caption overlaps Play: $description" }
                        check(caption.width() >= (86 * density).roundToInt() - 1) { "Compact caption lost its usable width: $description" }
                        check(scrim.contains(caption)) { "Caption outside contrast scrim: $description" }
                    }
                    checked++
                }
                Log.i(TAG, "$style font=$scale night=$night rtl=$rtl wideCutoff=$wide")
            }
        }
        Log.i(TAG, "PASS $checked real RemoteViews layouts: three styles, light/dark, 100/150/200% fonts, LTR/RTL, long/fallback glyphs")
    }

    private fun ViewGroup.boundsOf(child: View): Rect = Rect().also {
        child.getDrawingRect(it)
        offsetDescendantRectToMyCoords(child, it)
    }

    private const val TAG = "SonglassWidgetLayout"
}
