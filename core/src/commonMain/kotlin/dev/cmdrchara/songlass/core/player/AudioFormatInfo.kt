package dev.cmdrchara.songlass.core.player

/** Input track format exposed by the platform extractor, never audio output/device capabilities. */
data class AudioFormatInfo(
    val codec: String? = null,
    val sampleRateHz: Int? = null,
    val bitDepth: Int? = null,
    /** Average encoded bit rate in bits per second, when the source reports it. */
    val bitRate: Int? = null,
    val channelCount: Int? = null,
) {
    init {
        require(codec == null || codec.isNotBlank())
        require(sampleRateHz == null || sampleRateHz > 0)
        require(bitDepth == null || bitDepth > 0)
        require(bitRate == null || bitRate > 0)
        require(channelCount == null || channelCount > 0)
    }

    companion object {
        /** Maps input metadata conservatively; extractors can already transform uncommon codecs. */
        fun fromSource(
            sampleMimeType: String?,
            sampleRateHz: Int?,
            bitDepth: Int?,
            averageBitRate: Int?,
            channelCount: Int? = null,
        ): AudioFormatInfo? {
            val mime = sampleMimeType?.lowercase()
            val codec = when (mime) {
                "audio/flac" -> "FLAC"
                "audio/alac" -> "ALAC"
                "audio/mpeg", "audio/mp3" -> "MP3"
                "audio/mpeg-l1" -> "MP1"
                "audio/mpeg-l2" -> "MP2"
                "audio/mp4a-latm", "audio/aac" -> "AAC"
                "audio/opus" -> "Opus"
                "audio/vorbis" -> "Vorbis"
                "audio/raw" -> "PCM"
                "audio/ac3" -> "AC-3"
                "audio/eac3", "audio/eac3-joc" -> "E-AC-3"
                "audio/ac4" -> "AC-4"
                "audio/true-hd" -> "TrueHD"
                "audio/vnd.dts" -> "DTS"
                "audio/vnd.dts.hd", "audio/vnd.dts.hd;profile=lbr" -> "DTS-HD"
                "audio/3gpp" -> "AMR"
                "audio/amr-wb" -> "AMR-WB"
                "audio/g711-alaw" -> "G.711 A-law"
                "audio/g711-mlaw" -> "G.711 mu-law"
                else -> null
            }
            // Lossy codecs have no source PCM word length. A decoder's output is not file depth.
            val depth = bitDepth?.takeIf { it > 0 && mime in losslessPcmMimes }
            val result = AudioFormatInfo(
                codec = codec,
                sampleRateHz = sampleRateHz?.takeIf { it > 0 },
                bitDepth = depth,
                bitRate = averageBitRate?.takeIf { it > 0 },
                channelCount = channelCount?.takeIf { it > 0 },
            )
            return result.takeIf {
                it.codec != null || it.sampleRateHz != null || it.bitDepth != null || it.bitRate != null || it.channelCount != null
            }
        }

        private val losslessPcmMimes = setOf("audio/flac", "audio/alac", "audio/raw")
    }
}
