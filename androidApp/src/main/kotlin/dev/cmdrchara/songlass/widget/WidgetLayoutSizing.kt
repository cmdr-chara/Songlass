package dev.cmdrchara.songlass.widget

import android.content.Context
import android.content.res.Configuration
import android.util.SizeF
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.annotation.MainThread
import dev.cmdrchara.songlass.R
import dev.cmdrchara.songlass.core.settings.InterfaceStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * RemoteViews cutoffs must fit the actual XML, padding, controls and system font metrics.
 * Measure once per labels/style/configuration on Main, not on playback ticks or cover changes.
 * TextViews apply the platform's nonlinear sp scaling; no fontScale multiplication is used.
 */
@MainThread
internal object WidgetLayoutSizing {
    val compactSize = SizeF(110f, 110f)
    private var configuration: Configuration? = null
    private val wideSizes = mutableMapOf<InterfaceStyle, WideSize>()
    private var compactLiquid: CompactLiquid? = null
    private data class WideSize(val title: String, val subtitle: String, val size: SizeF)
    private data class CompactLiquid(val title: String, val titleLines: Int, val scrimHeightPx: Int)

    fun layout(style: InterfaceStyle, wide: Boolean): Int = when (style) {
        InterfaceStyle.LiquidGlass -> if (wide) R.layout.widget_liquid_wide else R.layout.widget_liquid_small
        InterfaceStyle.MaterialGlass -> if (wide) R.layout.widget_material_wide else R.layout.widget_material_small
        InterfaceStyle.Miuix -> if (wide) R.layout.widget_miuix_wide else R.layout.widget_miuix_small
    }

    fun wideSize(context: Context, style: InterfaceStyle, title: String, subtitle: String): SizeF {
        updateConfiguration(context)
        val cached = wideSizes[style]
        if (cached?.title == title && cached.subtitle == subtitle) return cached.size
        return run {
            val root = inflate(context, layout(style, wide = true))
            // Labels ellipsize to one line. Their content must not expand the cutoff to
            // the length of a title; the real transport row determines its minimum width.
            root.findViewById<TextView>(R.id.title).text = "Mg"
            root.findViewById<TextView>(R.id.subtitle).text = "Mg"
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            root.measure(unspecified, unspecified)
            val density = context.resources.displayMetrics.density
            val widthDp = ceil(root.measuredWidth / density)
            // Actual labels can use taller fallback glyphs (emoji, CJK, diacritics).
            // Only height may grow; a long title must still ellipsize within the card.
            root.findViewById<TextView>(R.id.title).text = title
            root.findViewById<TextView>(R.id.subtitle).text = subtitle
            root.measure(View.MeasureSpec.makeMeasureSpec(ceil(widthDp * density).toInt(), View.MeasureSpec.EXACTLY), unspecified)
            SizeF(widthDp, ceil(root.measuredHeight / density)).also {
                wideSizes[style] = WideSize(title, subtitle, it)
            }
        }
    }

    /** Keep both large caption lines over the scrim and clear of the compact play target. */
    fun applyCompactLiquid(context: Context, views: RemoteViews, label: String) {
        updateConfiguration(context)
        val metrics = compactLiquid?.takeIf { it.title == label } ?: run {
            val root = inflate(context, R.layout.widget_liquid_small)
            val title = root.findViewById<TextView>(R.id.title)
            title.text = label
            val density = context.resources.displayMetrics.density
            val width = (compactSize.width * density).roundToInt()
            val height = (compactSize.height * density).roundToInt()
            val unspecified = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            fun measure() = root.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), unspecified)
            measure()
            val margins = title.layoutParams as ViewGroup.MarginLayoutParams
            val play = root.findViewById<View>(R.id.play)
            val playMargins = play.layoutParams as ViewGroup.MarginLayoutParams
            val captionGap = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics).roundToInt()
            val captionHeight = height - playMargins.topMargin - play.measuredHeight - captionGap - margins.bottomMargin
            val fadePadding = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12f, context.resources.displayMetrics).roundToInt()
            if (title.measuredHeight > captionHeight) {
                // Preserve the system font size and clear the upper play target. The root
                // retains the complete accessible label when the visible title ellipsizes.
                title.maxLines = 1
                measure()
            }
            val baseline = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 64f, context.resources.displayMetrics).roundToInt()
            CompactLiquid(label, title.maxLines, maxOf(baseline, title.measuredHeight + margins.bottomMargin + fadePadding))
                .also { compactLiquid = it }
        }
        views.setInt(R.id.title, "setMaxLines", metrics.titleLines)
        views.setViewLayoutHeight(R.id.widget_scrim, metrics.scrimHeightPx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
    }

    private fun inflate(context: Context, layout: Int): View =
        LayoutInflater.from(context).inflate(layout, FrameLayout(context), false).apply {
            layoutDirection = context.resources.configuration.layoutDirection
        }

    private fun updateConfiguration(context: Context) {
        val current = context.resources.configuration
        if (configuration != current) {
            configuration = Configuration(current)
            wideSizes.clear()
            compactLiquid = null
        }
    }
}
