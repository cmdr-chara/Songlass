package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.designsystem.foundation.PrefetchListArtwork
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Folder
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.foundation.generativeCoverColors
import dev.cmdrchara.songlass.designsystem.foundation.monogramOf
import kotlin.math.absoluteValue

/** Shown until, or instead of, the real cover: a generated one, stable for the same [key]. */
private fun artworkFor(key: String, imageKey: String?, title: String?): Artwork {
    val seed = key.hashCode()
    return Artwork(generativeCoverColors(seed), imageKey, seed = seed.absoluteValue, monogram = monogramOf(title))
}

// The image key is a track's URI: the platform loader reads the cover embedded in that file.
// An album uses its first track's cover.

fun Album.artwork(): Artwork = artworkFor("$title|$artist", tracks.firstOrNull()?.artworkKey, title)

fun Playlist.artwork(): Artwork = artworkFor("playlist|$id", tracks.firstOrNull()?.artworkKey, name)

/** Same key as the folder's own playlist, so both share colors and cache. */
fun Folder.artwork(): Artwork = artworkFor("playlist|$id", allTracks.firstOrNull()?.artworkKey, name)

/** Songs of an album share its cover; a lone song gets its own from the title. */
fun Track.artwork(): Artwork =
    if (album != null) artworkFor("$album|$groupingArtist", artworkKey, album) else artworkFor("song|$title|$artist", artworkKey, title)

/**
 * [artwork] plus its album's cover as the stand-in drawn while this file's own cover decodes:
 * tracks opened from an album page show the cover already in memory instead of the placeholder.
 */
fun Track.artwork(library: MusicLibrary): Artwork {
    val own = artwork()
    val albumKey = library.albumOf(this)?.tracks?.firstOrNull()?.artworkKey
    return if (albumKey == null || albumKey == own.imageKey) own else own.copy(fallbackImageKey = albumKey)
}

/** The visible library, for covers that borrow their album's image (see [rememberTrackArtwork]). */
val LocalMusicLibrary = compositionLocalOf<MusicLibrary?> { null }

/** [track]'s cover for rows: its album's cover stands in until the file's own is in memory. */
@Composable
fun rememberTrackArtwork(track: Track): Artwork {
    val library = LocalMusicLibrary.current
    return remember(track, library) { if (library == null) track.artwork() else track.artwork(library) }
}

/** Loads the covers of the song rows (lazy keys = track ids) just beyond the visible ones, album stand-ins first. */
@Composable
fun PrefetchTrackRowArtwork(state: LazyListState, tracks: List<Track>) {
    val library = LocalMusicLibrary.current
    val keys = remember(tracks) { tracks.map<Track, Any> { it.id } }
    PrefetchListArtwork(state, keys, { index ->
        tracks.getOrNull(index)?.let { if (library == null) it.artwork() else it.artwork(library) }
    })
}

/** Empty smart playlists keep their own stable generated cover, just like personal playlists. */
fun dev.cmdrchara.songlass.core.playlists.SmartPlaylist.artwork(tracks: List<Track>): Artwork =
    artworkFor("smart|$id", tracks.firstOrNull()?.artworkKey, name)
