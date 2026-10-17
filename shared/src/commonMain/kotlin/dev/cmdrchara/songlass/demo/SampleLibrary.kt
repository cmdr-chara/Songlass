package dev.cmdrchara.songlass.demo

import dev.cmdrchara.songlass.core.model.Track

/**
 * Fictional music library used until real media scanning exists (step 6 of the plan).
 * All names are invented.
 */
object SampleLibrary {

    private class AlbumSpec(val title: String, val artist: String, val titles: List<String>, val guests: List<String?> = emptyList())

    private val albums = listOf(
        AlbumSpec("Northern Lights", "Aurora Vale", listOf("First Snow", "Polar Night", "Glacier", "Quiet Harbour", "Silver Line", "Afterglow", "Driftwood", "Home Again")),
        AlbumSpec("Glass Rivers", "Aurora Vale", listOf("Clear Water", "Undertow", "Lanterns", "Paper Boats", "Estuary", "Low Tide")),
        AlbumSpec("Paper Skies", "Kite Harbor", listOf("Take Off", "Crosswind", "Balloon Season", "Static", "Open Field", "Weather Report", "Landing")),
        AlbumSpec("Halflight", "Lumen", listOf("Dusk", "Neon Moth", "Long Exposure", "Blue Hour", "Prism", "Night Bus", "Satellites", "Slow Fade", "Dawn")),
        AlbumSpec("Coastline", "Mira Sol", listOf("Salt", "Sunday Drive", "Seagrass", "Postcards", "Lighthouse", "Marea")),
        AlbumSpec("Solstice", "Mira Sol", listOf("Longest Day", "Golden", "Firefly", "Late Swim", "Embers")),
        AlbumSpec("Midnight Radio", "The Night Owls", listOf("Call Sign", "Frequency", "Insomnia", "Last Train", "Streetlights", "Static Love", "3 AM", "Sign Off")),
        AlbumSpec(
            "Summer Sessions", "Various Artists",
            listOf("Heatwave", "Rooftop", "Ice Cream Truck", "Open Windows", "Sunset Strip"),
            guests = listOf("Mira Sol", "Lumen", "Kite Harbor", "Aurora Vale", "The Night Owls"),
        ),
    )

    val tracks: List<Track> = albums.flatMapIndexed { albumIndex, album ->
        album.titles.mapIndexed { i, title ->
            val guest = album.guests.getOrNull(i)
            Track(
                id = "demo-$albumIndex-$i",
                title = title,
                uri = "demo://$albumIndex/$i",
                artist = guest ?: album.artist,
                album = album.title,
                albumArtist = if (guest != null) album.artist else null,
                durationMs = 150_000L + ((albumIndex * 37 + i * 53) % 120) * 1_000L,
                trackNumber = i + 1,
            )
        }
    }
}
