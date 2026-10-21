package dev.cmdrchara.songlass.lyrics

import android.os.Handler
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import dev.cmdrchara.songlass.core.lyrics.Lyrics
import dev.cmdrchara.songlass.core.lyrics.LyricsOrigin
import dev.cmdrchara.songlass.core.lyrics.LyricsParser
import dev.cmdrchara.songlass.core.lyrics.LyricsSource
import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Reads the already prepared track. It never opens another decoder or changes playback.
 * [currentPlayer] returns the ExoPlayer that plays the current track; with crossfade that is not
 * always the same instance, so it is asked again at every step.
 */
@androidx.annotation.OptIn(UnstableApi::class)
class EmbeddedLyricsSource(private val currentPlayer: () -> ExoPlayer) : LyricsSource {
    constructor(player: ExoPlayer) : this({ player })

    // Both players are built on the main thread and share its looper.
    private val dispatcher = Handler(currentPlayer().applicationLooper).asCoroutineDispatcher("Lyrics metadata")

    override suspend fun load(track: Track): Lyrics? = withContext(dispatcher) {
        currentCoroutineContext().ensureActive()
        val prepared = currentPlayer()
        // A verified reindex can change the library URI while the old decoder finishes playing.
        // That decoder has no usable metadata for this selection; ordinary fallback may continue.
        if (!isCurrentTrack(prepared, track)) return@withContext null
        readMetadata(prepared)?.let { return@withContext it }
        if (prepared.playbackState == Player.STATE_BUFFERING) awaitPreparation(prepared, track)
        currentCoroutineContext().ensureActive()
        // The current track may have moved to the other player while waiting.
        val player = currentPlayer()
        if (!isCurrentTrack(player, track)) return@withContext null
        readMetadata(player)
    }

    private suspend fun awaitPreparation(player: ExoPlayer, track: Track) {
        withTimeoutOrNull(5_000L) {
            var listener: Player.Listener? = null
            try {
                suspendCancellableCoroutine { continuation ->
                    listener = object : Player.Listener {
                        override fun onEvents(player: Player, events: Player.Events) {
                            if (continuation.isActive &&
                                (!isCurrentTrack(player, track) || player.playbackState != Player.STATE_BUFFERING)
                            ) continuation.resume(Unit)
                        }
                    }
                    player.addListener(listener!!)
                }
            } finally {
                // This coroutine runs on the player's looper, including cancellation cleanup.
                listener?.let(player::removeListener)
            }
        }
    }

    private fun isCurrentTrack(player: Player, track: Track): Boolean =
        player.currentMediaItem?.mediaId == track.id &&
            player.currentMediaItem?.localConfiguration?.uri?.toString() == track.uri

    private fun readMetadata(player: ExoPlayer): Lyrics? {
        var plain: Lyrics? = null
        for (group in player.currentTracks.groups) {
            for (index in 0 until group.length) {
                val metadata = group.getTrackFormat(index).metadata ?: continue
                for (entryIndex in 0 until metadata.length()) {
                    val lyrics = decode(metadata[entryIndex]) ?: continue
                    if (lyrics.isSynced) return lyrics
                    if (plain == null) plain = lyrics
                }
            }
        }
        return plain
    }

    private fun decode(entry: Metadata.Entry): Lyrics? = when (entry) {
        is BinaryFrame -> Id3LyricsDecoder.decode(entry.id, entry.data)
        is VorbisComment -> if (isLyricsKey(entry.key)) parse(entry.value) else null
        // Media3 maps the M4A copyright-lyr atom to a USLT TextInformationFrame.
        is TextInformationFrame -> if (entry.id == "USLT" || entry.id == "ULT" ||
            (entry.id == "TXXX" && isLyricsKey(entry.description.orEmpty()))
        ) {
            val length = entry.values.sumOf { it.length.toLong() }
            if (length > MAX_TEXT_LENGTH) null else parse(entry.values.joinToString("\n"))
        } else null
        else -> null
    }

    private fun parse(text: String): Lyrics? =
        if (text.length > MAX_TEXT_LENGTH) null else LyricsParser.parse(text, LyricsOrigin.Embedded)

    private fun isLyricsKey(key: String): Boolean =
        key.equals("LYRICS", ignoreCase = true) || key.equals("UNSYNCEDLYRICS", ignoreCase = true) ||
            key.equals("SYNCEDLYRICS", ignoreCase = true)

    private companion object {
        const val MAX_TEXT_LENGTH = 512 * 1024
    }
}
