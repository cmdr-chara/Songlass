package dev.cmdrchara.songlass.designsystem.foundation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayerArtworkFrameTest {
    private val thumb = Rect(24f, 820f, 72f, 868f)
    private val window = Size(430f, 956f)

    @Test
    fun endsAtTheActualThumbnailAndOriginalFullScreenLayout() {
        val closed = playerArtworkFrame(thumb, window, 0f)
        assertEquals(thumb, closed.cover)
        assertEquals(thumb, closed.surface)
        val open = playerArtworkFrame(thumb, window, 1f)
        assertEquals(Rect(0f, 0f, 430f, 956f), open.surface)
        assertEquals(window.height * 0.64f, open.cover.height, 0.001f)
        assertEquals(1f, open.bodyReveal)
    }

    @Test
    fun neverLeavesABlankStripBesideTheCover() {
        for (size in listOf(window, Size(956f, 430f), Size(320f, 540f))) for (step in 0..100) {
            val frame = playerArtworkFrame(thumb, size, step / 100f)
            assertEquals(frame.surface.left, frame.cover.left)
            assertEquals(frame.surface.right, frame.cover.right)
            assertEquals(frame.surface.top, frame.cover.top)
            assertTrue(frame.cover.height > 0f)
            assertTrue(frame.cover.bottom <= frame.surface.bottom)
        }
    }

    @Test
    fun noEmptyControlPanelRemainsAsTheCoverApproachesTheMiniPlayer() {
        for (step in 0..60) {
            val frame = playerArtworkFrame(thumb, window, step / 100f)
            assertEquals(frame.cover, frame.surface)
            assertEquals(0f, frame.bodyReveal)
        }
    }

    @Test
    fun shrinkingPlayerStaysInsideTheWindowIncludingItsControlBody() {
        for (step in 0..100) {
            val frame = playerArtworkFrame(thumb, window, step / 100f)
            assertTrue(frame.surface.bottom <= window.height + 0.001f)
            val controlsHeight = window.height * 0.36f * (frame.surface.width / window.width) * frame.bodyReveal
            assertEquals(frame.surface.bottom, frame.cover.bottom + controlsHeight, 0.001f)
        }
    }

    @Test
    fun overshootIsClampedToTheMatchingEndpoint() {
        assertEquals(playerArtworkFrame(thumb, window, 0f), playerArtworkFrame(thumb, window, -0.1f))
        assertEquals(playerArtworkFrame(thumb, window, 1f), playerArtworkFrame(thumb, window, 1.1f))
    }
    @Test
    fun squareUsesTheActualRestingCoverAndReturnsToTheSameThumbnail() {
        val target = Rect(40f, 140f, 390f, 490f)
        val closed = squarePlayerArtworkFrame(thumb, window, target, 0f)
        assertEquals(thumb, closed.cover)
        assertEquals(thumb, closed.surface)
        val open = squarePlayerArtworkFrame(thumb, window, target, 1f)
        assertEquals(target, open.cover)
        assertEquals(Rect(0f, 0f, window.width, window.height), open.surface)
        for (step in 0..100) {
            val frame = squarePlayerArtworkFrame(thumb, window, target, step / 100f)
            assertEquals(frame.surface.center.x, frame.cover.center.x, 0.001f,
                "The cover must not protrude towards the left of its player panel")
            assertTrue(frame.surface.left <= frame.cover.left + 0.001f)
            assertTrue(frame.surface.top <= frame.cover.top + 0.001f)
            assertTrue(frame.surface.right >= frame.cover.right - 0.001f)
            assertTrue(frame.surface.bottom >= frame.cover.bottom - 0.001f)
            if (step <= 60) assertEquals(frame.cover, frame.surface)
        }
    }

    @Test
    fun squareClosesWithoutReversingItsSideEdgesNearTheMiniPlayer() {
        val thumbnail = Rect(60f, 2388f, 204f, 2532f)
        val screen = Size(1280f, 2856f)
        val target = Rect(84f, 447f, 1196f, 1559f)
        var previous = squarePlayerArtworkFrame(thumbnail, screen, target, 1f)
        for (step in 999 downTo 0) {
            val t = step / 1000f
            val frame = squarePlayerArtworkFrame(thumbnail, screen, target, t)
            assertTrue(frame.cover.left <= previous.cover.left + 0.001f,
                "The cover's left edge must not reverse direction")
            assertTrue(frame.cover.right <= previous.cover.right + 0.001f)
            assertTrue(frame.cover.center.x <= previous.cover.center.x + 0.001f)
            assertTrue(frame.surface.left >= previous.surface.left - 0.001f,
                "The panel must not protrude left again as it closes")
            assertTrue(frame.surface.right <= previous.surface.right + 0.001f)
            if (t <= 0.6f) {
                assertEquals(thumbnail.left, frame.cover.left, 0.001f)
                assertEquals(frame.cover, frame.surface)
            }
            previous = frame
        }
        assertEquals(thumbnail, previous.cover)
    }

}
