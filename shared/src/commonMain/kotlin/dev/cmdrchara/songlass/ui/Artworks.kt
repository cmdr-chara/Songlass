package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Folder
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

fun Album.artwork(): Artwork = artworkFor("$title|$artist", tracks.firstOrNull()?.uri, title)

fun Playlist.artwork(): Artwork = artworkFor("playlist|$id", tracks.firstOrNull()?.uri, name)

/** Same key as the folder's own playlist, so both share colors and cache. */
fun Folder.artwork(): Artwork = artworkFor("playlist|$id", allTracks.firstOrNull()?.uri, name)

/** Songs of an album share its cover; a lone song gets its own from the title. */
fun Track.artwork(): Artwork =
    if (album != null) artworkFor("$album|$groupingArtist", uri, album) else artworkFor("song|$title|$artist", uri, title)
