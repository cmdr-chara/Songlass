package dev.cmdrchara.songlass.core.playback

import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.player.PlayerState
import dev.cmdrchara.songlass.core.player.PlayerStatus
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Values for system-owned media surfaces. A stop or failure must remove the old item. */
data class SystemNowPlaying(
    val track: Track,
    val elapsedSeconds: Double,
    val durationSeconds: Double,
    val playbackRate: Double,
    val queueIndex: Int,
    val queueCount: Int,
) {
    companion object {
        fun from(track: Track?, state: PlayerState, queueIndex: Int, queueCount: Int): SystemNowPlaying? {
            if (track == null || state.status == PlayerStatus.Idle || state.status == PlayerStatus.Error) return null
            val duration = state.durationMs.takeIf { it > 0 } ?: track.durationMs.coerceAtLeast(0)
            val position = state.positionMs.coerceAtLeast(0).let { if (duration > 0) it.coerceAtMost(duration) else it }
            val speed = state.playbackSpeed.takeIf { it.isFinite() && it > 0 } ?: 1f
            return SystemNowPlaying(
                track, position / 1000.0, duration / 1000.0,
                if (state.status == PlayerStatus.Ready && state.playWhenReady) speed.toDouble() else 0.0,
                queueIndex.coerceIn(0, (queueCount - 1).coerceAtLeast(0)), queueCount.coerceAtLeast(0),
            )
        }
    }
}

/** Probe a documented capability, rather than maintaining a list of HyperOS marketing versions. */
fun supportsXiaomiMediaIsland(manufacturer: String, focusProtocol: Int): Boolean =
    manufacturer.equals("Xiaomi", ignoreCase = true) && focusProtocol >= 3

/**
 * Xiaomi's media-specific island extension. Share actual tags as text: local content/file URLs
 * cannot be opened on another phone and must never become a public link.
 * The ROM supplies the cover from the standard media notification.
 */
fun xiaomiMediaIslandPayload(title: String?, artist: String?, album: String?): String? {
    val name = title?.trim()?.takeIf(String::isNotEmpty) ?: return null
    val description = listOfNotNull(artist, album).map(String::trim).filter(String::isNotEmpty)
        .distinct().joinToString(" · ")
    val sharedText = listOf(name, description).filter(String::isNotEmpty).joinToString(" — ")
    return buildJsonObject {
        put("param_v2", buildJsonObject {
            put("param_island", buildJsonObject {
                put("shareData", buildJsonObject {
                    put("title", name)
                    put("content", description)
                    put("shareContent", sharedText)
                })
            })
        })
    }.toString()
}
