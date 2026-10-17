package dev.cmdrchara.songlass.core

import dev.cmdrchara.songlass.core.model.Track

fun track(
    id: String,
    title: String = "Track $id",
    artist: String? = "Artist",
    album: String? = "Album",
    albumArtist: String? = null,
    durationMs: Long = 180_000,
    trackNumber: Int? = null,
    discNumber: Int? = null,
    folder: String? = null,
) = Track(
    id = id,
    title = title,
    uri = "file:///music/$id.flac",
    artist = artist,
    album = album,
    albumArtist = albumArtist,
    durationMs = durationMs,
    trackNumber = trackNumber,
    discNumber = discNumber,
    folder = folder,
)

fun tracks(count: Int) = (1..count).map { track(it.toString()) }
