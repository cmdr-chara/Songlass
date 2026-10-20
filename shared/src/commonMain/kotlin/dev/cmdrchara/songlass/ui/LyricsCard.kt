package dev.cmdrchara.songlass.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.cmdrchara.songlass.designsystem.LyricsCardStyle
import dev.cmdrchara.songlass.designsystem.theme.AppColors

internal const val LYRICS_CARD_WIDTH = 1080
internal const val LYRICS_CARD_HEIGHT = 1350

/** Fixed-resolution original artwork. Preview and share use this same bitmap. Never clips lyrics. */
internal fun renderLyricsCard(
    title: String,
    artist: String,
    lines: List<String>,
    cover: ImageBitmap?,
    colors: AppColors,
    look: LyricsCardStyle,
    textStyle: TextStyle,
    measurer: TextMeasurer,
    label: String,
): ImageBitmap? {
    val ink = if (colors.isDark) Color.White else Color(0xFF19191D)
    val inset = 76f
    val width = 928f
    val textWidth = (width - look.verseInset * 2).toInt()
    var verses: List<TextLayoutResult>? = null
    val length = lines.sumOf { it.length }
    val preferred = look.fontSize * when { length < 80 -> 1.45f; length < 160 -> 1.18f; else -> 1f }
    for (size in listOf(preferred, (preferred - 8f).coerceAtLeast(look.fontSize), look.fontSize, 60f, 54f, 48f, 42f).distinct()) {
        val measured = lines.map { line -> measurer.measure(
            AnnotatedString(line),
            style = textStyle.copy(fontSize = size.sp, lineHeight = (size * 1.26f).sp,
                fontWeight = FontWeight.Bold, color = ink),
            constraints = Constraints(maxWidth = textWidth),
            density = Density(1f),
        ) }
        if (measured.sumOf { it.size.height } + (measured.size - 1) * 26 <= 680) {
            verses = measured; break
        }
    }
    val measured = verses ?: return null
    val bitmap = ImageBitmap(LYRICS_CARD_WIDTH, LYRICS_CARD_HEIGHT)
    CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(bitmap), Size(1080f, 1350f)) {
        val outline = Path().apply { addRoundRect(RoundRect(Rect(0f, 0f, 1080f, 1350f), CornerRadius(look.corner))) }
        clipPath(outline) {
            drawRect(colors.background)
            drawRect(Brush.linearGradient(listOf(colors.accent.copy(alpha = .10f), Color.Transparent), Offset.Zero, Offset(1080f, 1350f)))
            val coverOutline = Path().apply { addRoundRect(RoundRect(Rect(inset, inset, inset + 136f, inset + 136f), CornerRadius(look.coverCorner))) }
            clipPath(coverOutline) {
                if (cover != null) {
                    val side = minOf(cover.width, cover.height)
                    drawImage(cover, srcOffset = IntOffset((cover.width - side) / 2, (cover.height - side) / 2),
                        srcSize = IntSize(side, side), dstOffset = IntOffset(76, 76), dstSize = IntSize(136, 136))
                } else {
                    drawRect(colors.accent.copy(alpha = .18f), Offset(inset, inset), Size(136f, 136f))
                    for (i in 0..2) drawRoundRect(colors.accent, Offset(112f + i * 22, 118f - (if (i == 1) 14f else 0f)),
                        Size(10f, if (i == 1) 64f else 36f), CornerRadius(5f))
                }
            }
            val heading = measurer.measure(AnnotatedString(title), textStyle.copy(fontSize = 38.sp, lineHeight = 46.sp,
                color = ink, fontWeight = FontWeight.Bold), maxLines = 2, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = 680), density = Density(1f))
            drawText(heading, topLeft = Offset(244f, 78f))
            val artistLayout = measurer.measure(AnnotatedString(artist), textStyle.copy(fontSize = 30.sp,
                color = colors.secondaryLabel), maxLines = 1, overflow = TextOverflow.Ellipsis,
                constraints = Constraints(maxWidth = 680), density = Density(1f))
            drawText(artistLayout, topLeft = Offset(244f, 90f + heading.size.height))
            if (look.verseInset > 0) drawRoundRect(colors.secondaryBackground, Offset(inset, 308f),
                Size(width, 792f), CornerRadius(look.verseCorner))
            if (look.accentMark) drawRoundRect(colors.accent, Offset(inset + look.verseInset, 344f), Size(72f, 12f), CornerRadius(6f))
            val height = measured.sumOf { it.size.height } + (measured.size - 1) * 26
            var y = 386f + (680 - height) / 2f
            for (verse in measured) {
                drawText(verse, topLeft = Offset(inset + look.verseInset, y))
                y += verse.size.height + 26f
            }
            drawLine(colors.separator, Offset(inset, 1150f), Offset(1004f, 1150f), 2f)
            val brand = measurer.measure(AnnotatedString("Songlass"), textStyle.copy(fontSize = 38.sp,
                color = colors.accent, fontWeight = FontWeight.Bold), density = Density(1f))
            drawText(brand, topLeft = Offset(inset, 1196f))
            val credit = measurer.measure(AnnotatedString(label), textStyle.copy(fontSize = 28.sp, color = colors.secondaryLabel), density = Density(1f))
            drawText(credit, topLeft = Offset(1004f - credit.size.width, 1206f))
        }
    }
    return bitmap
}
