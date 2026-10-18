package dev.cmdrchara.songlass.designsystem.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArtworkTintTest {

    private val seeds = listOf(
        Color(0xFFFF6A88), Color(0xFFFFE140), Color(0xFF330867), Color(0xFF38F9D7),
        Color.White, Color.Black, Color(0xFF808080), Color(0xFFB5A642),
    )

    @Test
    fun contentAlwaysMeetsMinimumContrastInBothModes() {
        for (seed in seeds) for (dark in listOf(false, true)) {
            val tint = ArtworkTint.from(seed, dark)
            val ratio = ArtworkTint.contrast(tint.content, tint.background)
            assertTrue(ratio >= ArtworkTint.MIN_CONTENT_CONTRAST, "seed $seed dark=$dark contrast $ratio")
        }
    }

    @Test
    fun lightTintIsPaleWithDeepContentAndDarkTintTheOpposite() {
        val light = ArtworkTint.from(Color(0xFFFF6A88), dark = false)
        val dark = ArtworkTint.from(Color(0xFFFF6A88), dark = true)
        assertTrue(Hsl.of(light.background).l > 0.8f)
        assertTrue(Hsl.of(light.content).l < 0.35f)
        assertTrue(Hsl.of(dark.background).l < 0.25f)
        assertTrue(Hsl.of(dark.content).l > 0.7f)
    }

    @Test
    fun tintKeepsTheSeedHue() {
        val seed = Color(0xFF38F9D7)
        val hue = Hsl.of(seed).h
        for (dark in listOf(false, true)) {
            val tint = ArtworkTint.from(seed, dark)
            assertTrue(abs(Hsl.of(tint.content).h - hue) < 8f)
            assertTrue(abs(Hsl.of(tint.background).h - hue) < 8f)
        }
    }

    @Test
    fun graySeedGivesNeutralTint() {
        val tint = ArtworkTint.from(Color(0xFF7A7C7E), dark = true)
        assertEquals(0f, Hsl.of(tint.background).s, 0.02f)
        assertEquals(0f, Hsl.of(tint.content).s, 0.02f)
    }

    @Test
    fun activeStatesInvertContentAndBackground() {
        val tint = ArtworkTint.from(Color(0xFFFFE140), dark = true)
        assertEquals(tint.content, tint.activeFill)
        assertEquals(tint.background, tint.onActiveFill)
    }

    @Test
    fun seedPrefersASmallVividAreaOverALargeDullOne() {
        val dull = List(80) { Color(0xFF6B6B70) }
        val vivid = List(12) { Color(0xFFFF2D55) }
        val seed = ArtworkTint.seedOf(dull + vivid)
        assertTrue(Hsl.of(seed).s > 0.8f)
        assertTrue(Hsl.of(seed).h < 20f || Hsl.of(seed).h > 330f)
    }

    @Test
    fun seedOfGrayImageStaysGray() {
        val seed = ArtworkTint.seedOf(List(50) { Color(0xFF444444) } + List(50) { Color(0xFFBBBBBB) })
        assertTrue(Hsl.of(seed).s < 0.05f)
    }

    @Test
    fun hslRoundTrips() {
        for (seed in seeds) {
            val back = Hsl.of(seed).toColor()
            assertEquals(seed.red, back.red, 0.01f)
            assertEquals(seed.green, back.green, 0.01f)
            assertEquals(seed.blue, back.blue, 0.01f)
        }
    }

    @Test
    fun darkCoverEdgeGivesDarkPageEvenInLightMode() {
        val tint = ArtworkTint.from(Color(0xFFFF7A1A), dark = false, edge = Color(0xFF0A0604))
        assertTrue(tint.isDark)
        assertTrue(Hsl.of(tint.background).l <= 0.2f)
        assertTrue(ArtworkTint.contrast(tint.content, tint.background) >= ArtworkTint.MIN_CONTENT_CONTRAST)
    }

    @Test
    fun lightCoverEdgeGivesLightPageEvenInDarkMode() {
        val tint = ArtworkTint.from(Color(0xFFE8B04A), dark = true, edge = Color(0xFFF2E8D8))
        assertTrue(!tint.isDark)
        assertTrue(Hsl.of(tint.background).l >= 0.82f)
        assertTrue(ArtworkTint.contrast(tint.content, tint.background) >= ArtworkTint.MIN_CONTENT_CONTRAST)
    }

    @Test
    fun midToneEdgeKeepsTheAppearancePreference() {
        val edge = Color(0xFF8A7F86)
        assertTrue(ArtworkTint.from(Color(0xFFFF6A88), dark = true, edge = edge).isDark)
        assertTrue(!ArtworkTint.from(Color(0xFFFF6A88), dark = false, edge = edge).isDark)
    }

    @Test
    fun backgroundFollowsEdgeLightnessWithinItsRange() {
        val nearBlack = ArtworkTint.from(Color(0xFF3366FF), dark = true, edge = Color(0xFF050505))
        val charcoal = ArtworkTint.from(Color(0xFF3366FF), dark = true, edge = Color(0xFF2A2A2A))
        assertTrue(Hsl.of(nearBlack.background).l < Hsl.of(charcoal.background).l)
    }

    @Test
    fun artworkHeaderChoosesContrastingChromeForBlackAndWhiteCovers() {
        assertTrue(ArtworkTint.prefersLightContent(Color.Black))
        assertTrue(!ArtworkTint.prefersLightContent(Color.White))
        assertTrue(ArtworkTint.prefersLightContent(Color(0xFF10001F)))
    }

    @Test
    fun artworkHeaderChromeMeetsContrastForEveryGrayTone() {
        for (channel in 0..255) {
            val gray = channel / 255f
            val background = Color(gray, gray, gray)
            val content = if (ArtworkTint.prefersLightContent(background)) Color.White else Color.Black
            assertTrue(ArtworkTint.contrast(content, background) >= 4.5f, "gray=$channel")
        }
    }

}
