package dev.cmdrchara.songlass.core.player

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReplayGainTest {
    @Test
    fun parsesCommonFormatsAndIgnoresJunk() {
        val tags = ReplayGainTags.parse(
            mapOf(
                "REPLAYGAIN_TRACK_GAIN" to "-6.02 dB",
                "replaygain_track_peak" to "0.988",
                "ReplayGain_Album_Gain" to "-4,5dB",
                "replaygain_album_peak" to "abc",
            ),
        )!!
        assertEquals(-6.02f, tags.trackGainDb)
        assertEquals(0.988f, tags.trackPeak)
        assertEquals(-4.5f, tags.albumGainDb)
        assertNull(tags.albumPeak)
    }

    @Test
    fun noGainMeansNoTags() = assertNull(ReplayGainTags.parse(mapOf("replaygain_track_peak" to "0.5", "title" to "x")))

    @Test
    fun trackModeAttenuates() {
        val volume = ReplayGain.volumeFor(ReplayGainTags(trackGainDb = -6.0206f), ReplayGainMode.Track)
        assertEquals(0.5f, volume, 0.001f)
    }

    @Test
    fun albumModeFallsBackToTrack() {
        val tags = ReplayGainTags(trackGainDb = -6.0206f)
        assertEquals(0.5f, ReplayGain.volumeFor(tags, ReplayGainMode.Album), 0.001f)
    }

    @Test
    fun positiveGainIsCappedAndPeakPreventsClipping() {
        assertEquals(1f, ReplayGain.volumeFor(ReplayGainTags(trackGainDb = 3f), ReplayGainMode.Track))
        val loudPeak = ReplayGainTags(trackGainDb = 0f, trackPeak = 1.25f)
        assertEquals(0.8f, ReplayGain.volumeFor(loudPeak, ReplayGainMode.Track), 0.001f)
    }

    @Test
    fun offOrMissingTagsKeepFullVolume() {
        assertEquals(1f, ReplayGain.volumeFor(ReplayGainTags(trackGainDb = -10f), ReplayGainMode.Off))
        assertEquals(1f, ReplayGain.volumeFor(null, ReplayGainMode.Track))
    }
}
