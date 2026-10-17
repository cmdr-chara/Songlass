package dev.cmdrchara.songlass.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AudioFormatInfoTest {
    @Test
    fun sourceFlacRetainsKnownResolution() {
        assertEquals(
            AudioFormatInfo("FLAC", 96_000, 24, 2_100_000),
            AudioFormatInfo.fromSource("audio/flac", 96_000, 24, 2_100_000),
        )
    }

    @Test
    fun lossyCodecsNeverInheritDecoderBitDepth() {
        for ((mime, codec) in listOf(
            "audio/mpeg" to "MP3", "audio/mp4a-latm" to "AAC",
            "audio/opus" to "Opus", "audio/vorbis" to "Vorbis",
        )) {
            assertEquals(
                AudioFormatInfo(codec, 48_000, null, 256_000),
                AudioFormatInfo.fromSource(mime, 48_000, 32, 256_000),
            )
        }
    }

    @Test
    fun unknownDepthRemainsUnknownForLossless() {
        assertEquals(AudioFormatInfo("ALAC", 44_100), AudioFormatInfo.fromSource("audio/alac", 44_100, -1, -1))
    }

    @Test
    fun missingAndInvalidFieldsNeverProduceResolutionClaims() {
        assertNull(AudioFormatInfo.fromSource(null, -1, -1, -1))
        assertNull(AudioFormatInfo.fromSource("audio/unrecognized", 0, 16, 0))
        assertEquals(AudioFormatInfo("PCM"), AudioFormatInfo.fromSource("audio/raw", 0, 0, 0))
    }

    @Test
    fun unrecognizedCodecCanStillExposeKnownSourceSampleRate() {
        assertEquals(AudioFormatInfo(sampleRateHz = 48_000), AudioFormatInfo.fromSource("audio/unrecognized", 48_000, 16, null))
    }

    @Test
    fun stateDefaultsToUnknownFormatAndClearsOnReset() {
        val playing = PlayerState(audioFormat = AudioFormatInfo("FLAC", 44_100, 16))
        assertEquals("FLAC", playing.audioFormat?.codec)
        assertNull(PlayerState().audioFormat)
    }

    @Test
    fun modelRejectsInvalidKnownValues() {
        assertFailsWith<IllegalArgumentException> { AudioFormatInfo(codec = " ") }
        assertFailsWith<IllegalArgumentException> { AudioFormatInfo(sampleRateHz = 0) }
        assertFailsWith<IllegalArgumentException> { AudioFormatInfo(bitDepth = -1) }
        assertFailsWith<IllegalArgumentException> { AudioFormatInfo(bitRate = 0) }
    }
}
