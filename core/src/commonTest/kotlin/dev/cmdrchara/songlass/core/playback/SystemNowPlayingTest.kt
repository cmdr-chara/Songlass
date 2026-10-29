package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SystemNowPlayingTest {
    private val track = Track("one", "A song", "file:///private/music/one.m4a", durationMs = 90_000)
    private fun snapshot(state: PlayerState) = assertNotNull(SystemNowPlaying.from(track, state, 2, 5))

    @Test fun stopRemovesRetainedQueueMetadata() {
        assertNull(SystemNowPlaying.from(track, PlayerState(status = PlayerStatus.Idle), 0, 1))
    }

    @Test fun errorRemovesRetainedQueueMetadata() {
        assertNull(SystemNowPlaying.from(track, PlayerState(status = PlayerStatus.Error), 0, 1))
    }

    @Test fun missingSelectionCannotPublishAnItem() {
        assertNull(SystemNowPlaying.from(null, PlayerState(status = PlayerStatus.Ready), 0, 1))
    }

    @Test fun playingPublishesActualSpeedAndSeconds() {
        val value = snapshot(PlayerState(PlayerStatus.Ready, true, positionMs = 12_500, durationMs = 80_000, playbackSpeed = 1.25f))
        assertEquals(12.5, value.elapsedSeconds)
        assertEquals(80.0, value.durationSeconds)
        assertEquals(1.25, value.playbackRate)
        assertEquals(2, value.queueIndex)
        assertEquals(5, value.queueCount)
    }

    @Test fun pausedProgressDoesNotKeepAdvancing() {
        assertEquals(0.0, snapshot(PlayerState(PlayerStatus.Ready, false, positionMs = 1200)).playbackRate)
    }

    @Test fun stalledProgressDoesNotKeepAdvancing() {
        assertEquals(0.0, snapshot(PlayerState(PlayerStatus.Buffering, true)).playbackRate)
    }

    @Test fun endedProgressDoesNotKeepAdvancing() {
        assertEquals(0.0, snapshot(PlayerState(PlayerStatus.Ended, true)).playbackRate)
    }

    @Test fun unknownNativeDurationUsesTheFileDuration() {
        assertEquals(90.0, snapshot(PlayerState(PlayerStatus.Ready, durationMs = -1)).durationSeconds)
    }

    @Test fun seekBeyondEndIsClamped() {
        assertEquals(90.0, snapshot(PlayerState(PlayerStatus.Ready, positionMs = Long.MAX_VALUE)).elapsedSeconds)
    }

    @Test fun negativePositionIsClamped() {
        assertEquals(0.0, snapshot(PlayerState(PlayerStatus.Ready, positionMs = -10)).elapsedSeconds)
    }

    @Test fun invalidSpeedNeverReachesTheSystem() {
        for (speed in listOf(Float.NaN, Float.POSITIVE_INFINITY, 0f, -1f)) {
            assertEquals(1.0, snapshot(PlayerState(PlayerStatus.Ready, true, playbackSpeed = speed)).playbackRate)
        }
    }

    @Test fun emptyQueueNeverPublishesNegativeIndices() {
        val value = assertNotNull(SystemNowPlaying.from(track, PlayerState(PlayerStatus.Ready), -1, -1))
        assertEquals(0, value.queueIndex)
        assertEquals(0, value.queueCount)
    }

    @Test fun mediaExtensionUsesProtocolThreeAndNewer() {
        assertTrue(supportsXiaomiMediaIsland("Xiaomi", 3))
        assertTrue(supportsXiaomiMediaIsland("xiaomi", 4))
        assertFalse(supportsXiaomiMediaIsland("Xiaomi", 2))
        assertFalse(supportsXiaomiMediaIsland("Xiaomi", 0))
        assertFalse(supportsXiaomiMediaIsland("Google", 4))
    }

    @Test fun sharePayloadEscapesTagsAndNeverIncludesThePrivateFile() {
        val title = "A \"song\"\n雪"
        val json = assertNotNull(xiaomiMediaIslandPayload(title, "Artist", "Album"))
        val share = Json.parseToJsonElement(json).jsonObject.getValue("param_v2").jsonObject
            .getValue("param_island").jsonObject.getValue("shareData").jsonObject
        assertEquals(title, share.getValue("title").jsonPrimitive.content)
        assertEquals("Artist · Album", share.getValue("content").jsonPrimitive.content)
        assertEquals("$title — Artist · Album", share.getValue("shareContent").jsonPrimitive.content)
        assertFalse(json.contains(track.uri))
        assertFalse(share.containsKey("pic"))
    }

    @Test fun absentTagsAreNotInvented() {
        val json = assertNotNull(xiaomiMediaIslandPayload(" Song ", null, " "))
        val share = Json.parseToJsonElement(json).jsonObject.getValue("param_v2").jsonObject
            .getValue("param_island").jsonObject.getValue("shareData").jsonObject
        assertEquals("Song", share.getValue("shareContent").jsonPrimitive.content)
        assertEquals("", share.getValue("content").jsonPrimitive.content)
        assertNull(xiaomiMediaIslandPayload(null, "Artist", "Album"))
        assertNull(xiaomiMediaIslandPayload(" ", "Artist", "Album"))
    }
}
