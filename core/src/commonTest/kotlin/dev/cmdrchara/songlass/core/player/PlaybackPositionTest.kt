package dev.cmdrchara.songlass.core.player

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackPositionTest {
    private val playing = PlayerState(PlayerStatus.Ready, true, 10_000, 30_000,
        positionSampledAtMs = 1_000)

    @Test fun `display advances between native samples without a new frame anchor delay`() {
        assertEquals(10_016L, playing.presentationPositionAt(1_016))
        assertEquals(10_249L, playing.presentationPositionAt(1_249))
        val next = playing.copy(positionMs = 10_250, positionSampledAtMs = 1_250)
        assertEquals(10_266L, next.presentationPositionAt(1_266))
    }

    @Test fun `speed duration and stale sampling are bounded`() {
        assertEquals(10_400L, playing.copy(playbackSpeed = 2f).presentationPositionAt(1_200))
        assertEquals(10_500L, playing.presentationPositionAt(5_000))
        assertEquals(30_000L, playing.copy(positionMs = 29_950).presentationPositionAt(1_200))
        assertEquals(10_000L, playing.presentationPositionAt(999))
    }

    @Test fun `pause buffering and pending transport do not invent movement`() {
        assertEquals(10_000L, playing.copy(playWhenReady = false).presentationPositionAt(1_200))
        assertEquals(10_000L, playing.copy(status = PlayerStatus.Buffering).presentationPositionAt(1_200))
        assertEquals(10_000L, playing.copy(positionAdvancing = false).presentationPositionAt(1_200))
        assertEquals(10_000L, playing.copy(positionSampledAtMs = null).presentationPositionAt(1_200))
    }

    @Test fun `backward seek replaces the old sample and never smooths across the seek`() {
        val seek = playing.copy(positionMs = 2_000, positionSampledAtMs = 1_250)
        assertEquals(2_016L, seek.presentationPositionAt(1_266))
        assertEquals(Long.MAX_VALUE, playing.copy(positionMs = Long.MAX_VALUE, durationMs = 0).presentationPositionAt(1_200))
    }
}
