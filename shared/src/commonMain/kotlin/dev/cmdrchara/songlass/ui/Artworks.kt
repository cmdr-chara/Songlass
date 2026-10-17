package dev.cmdrchara.songlass.ui

import androidx.compose.ui.graphics.Color
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.Artwork
import kotlin.math.absoluteValue

// Placeholder artwork until cover art is read from files: a stable gradient per album.

private val palettes = listOf(
    0xFFFF9A8B to 0xFFFF6A88,
    0xFF8EC5FC to 0xFFE0C3FC,
    0xFF43E97B to 0xFF38F9D7,
    0xFFFA709A to 0xFFFEE140,
    0xFF30CFD0 to 0xFF330867,
    0xFFA18CD1 to 0xFFFBC2EB,
    0xFFF6D365 to 0xFFFDA085,
    0xFF5EE7DF to 0xFFB490CA,
    0xFF667EEA to 0xFF764BA2,
    0xFFFF758C to 0xFFFF7EB3,
)

private fun artworkFor(key: String): Artwork {
    val (a, b) = palettes[key.hashCode().absoluteValue % palettes.size]
    return Artwork(listOf(Color(a), Color(b)))
}

fun Album.artwork(): Artwork = artworkFor("$title|$artist")

fun Track.artwork(): Artwork = artworkFor("$album|$groupingArtist")
