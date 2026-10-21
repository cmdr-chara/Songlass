package dev.cmdrchara.songlass.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import dev.cmdrchara.songlass.core.player.AudioFormatInfo

/** Accepts an input track Format, not the renderer's decoded PCM format. */
@androidx.annotation.OptIn(UnstableApi::class)
internal fun Format.sourceAudioFormat(): AudioFormatInfo? = AudioFormatInfo.fromSource(
    sampleMimeType = sampleMimeType,
    sampleRateHz = sampleRate,
    bitDepth = sourceBitDepth(sampleMimeType, pcmEncoding),
    // Format.bitrate prefers peakBitrate. Only the reported average describes the usual file rate.
    averageBitRate = averageBitrate,
    channelCount = channelCount,
)

@androidx.annotation.OptIn(UnstableApi::class)
internal fun sourceBitDepth(sampleMimeType: String?, pcmEncoding: Int): Int? {
    if (sampleMimeType != MimeTypes.AUDIO_RAW && sampleMimeType != MimeTypes.AUDIO_FLAC &&
        sampleMimeType != MimeTypes.AUDIO_ALAC
    ) return null
    return when (pcmEncoding) {
        C.ENCODING_PCM_8BIT -> 8
        C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
        C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 24
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
        // The UI's integer bit-depth label cannot accurately describe floating-point precision.
        C.ENCODING_PCM_FLOAT, C.ENCODING_PCM_FLOAT_BIG_ENDIAN,
        C.ENCODING_PCM_DOUBLE, C.ENCODING_PCM_DOUBLE_BIG_ENDIAN -> null
        else -> null
    }
}
