package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.HistorySnapshot
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.library.Artist
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.library.Playlist
import dev.cmdrchara.songlass.core.model.Track
import kotlin.random.Random

/** Something the Home can show as a tile. */
sealed interface HomeItem {
    data class AlbumItem(val album: Album) : HomeItem
    data class PlaylistItem(val playlist: Playlist) : HomeItem
    data class ArtistItem(val artist: Artist) : HomeItem
    data class TrackItem(val track: Track) : HomeItem
    data class FavoritesItem(val tracks: List<Track>) : HomeItem
}

/** Why "Change of pace" suggests these songs; the UI turns it into a specific sentence. */
sealed interface ChangeOfPaceReason {
    /** Lately the listening has been mostly [artist]; suggestions avoid that artist. */
    data class MostlyOneArtist(val artist: String) : ChangeOfPaceReason

    /** [count] songs in the library have never been played. */
    data class NeverPlayed(val count: Int) : ChangeOfPaceReason

    /** Songs not played for a while. */
    data object NotLately : ChangeOfPaceReason
}

data class ChangeOfPace(val tracks: List<Track>, val reason: ChangeOfPaceReason)

/** An album to rediscover; [idleDays] since it was last played, null if it never was. */
data class Rediscover(val album: Album, val idleDays: Int?)

/** Everything the Home shows. Empty sections are empty lists / nulls and are not displayed. */
data class HomeFeed(
    val favorites: List<Track> = emptyList(),
    /**
     * Quick way back in at the top of the Home: the latest places playback started from, then
     * favorites and playlists to fill the grid. Empty when there are fewer than two.
     */
    val shortcuts: List<HomeItem> = emptyList(),
    /** An album not heard for a while, picked once per day. */
    val rediscover: Rediscover? = null,
    val recentlyPlayed: List<HomeItem> = emptyList(),
    val recentlyAdded: List<HomeItem> = emptyList(),
    val mostPlayed: List<Track> = emptyList(),
    val changeOfPace: ChangeOfPace? = null,
    val playlists: List<Playlist> = emptyList(),
) {
    companion object {
        const val ROW_SIZE = 12
        const val SHORT_LIST = 5
        /** Songs in a paged shelf: two columns of four. */
        const val SHELF_SIZE = 8
        const val SHORTCUTS = 6
        private const val DAY_MS = 86_400_000L
        private const val REDISCOVER_AFTER_MS = 30 * DAY_MS
        private const val MIN_PLAYS_FOR_SUGGESTIONS = 3
        private const val DOMINANT_WINDOW = 20
        private const val DOMINANT_SHARE = 0.5f
        private const val DOMINANT_MIN_PLAYS = 5

        /**
         * Builds the Home from the library, the listening history and the favorites. Choices that
         * involve chance use a seed derived from the day of [nowMs], so the Home stays the same
         * throughout a day and changes the next.
         */
        fun build(library: MusicLibrary, history: HistorySnapshot, favoriteIds: List<String>, nowMs: Long): HomeFeed {
            val day = nowMs / DAY_MS
            val byId = library.tracks.associateBy { it.id }
            val favorites = favoriteIds.asReversed().mapNotNull { byId[it] }

            fun lastPlayedOf(tracks: List<Track>): Long? = tracks.mapNotNull { history.lastPlayedMs[it.id] }.maxOrNull()
            val stale = library.albums.filter { album ->
                val last = lastPlayedOf(album.tracks)
                last == null || nowMs - last > REDISCOVER_AFTER_MS
            }
            val rediscover = stale.takeIf { it.isNotEmpty() }?.let { it[Random(day).nextInt(it.size)] }?.let { album ->
                Rediscover(album, lastPlayedOf(album.tracks)?.let { ((nowMs - it) / DAY_MS).toInt() })
            }

            val recentlyPlayed = history.recentContexts.mapNotNull { recent ->
                when (val c = recent.context) {
                    is PlayContext.Album -> library.albums.firstOrNull { it.title == c.title && it.artist == c.artist }?.let(HomeItem::AlbumItem)
                    is PlayContext.Playlist -> library.playlists.firstOrNull { it.id == c.id }?.let(HomeItem::PlaylistItem)
                    is PlayContext.Artist -> library.artists.firstOrNull { it.name == c.name }?.let(HomeItem::ArtistItem)
                    PlayContext.Favorites -> favorites.takeIf { it.isNotEmpty() }?.let(HomeItem::FavoritesItem)
                }
            }.take(ROW_SIZE)

            // Newest files first; songs that belong to an album show as that album, once.
            val recentlyAdded = library.tracks.filter { it.dateAddedMs != null }
                .sortedByDescending { it.dateAddedMs }
                .map { track -> library.albumOf(track)?.let(HomeItem::AlbumItem) ?: HomeItem.TrackItem(track) }
                .distinct()
                .take(ROW_SIZE)

            val mostPlayed = library.tracks.filter { (history.playCounts[it.id] ?: 0) > 0 }
                .sortedWith(compareByDescending<Track> { history.playCounts[it.id] ?: 0 }.thenByDescending { history.lastPlayedMs[it.id] ?: 0 })
                .take(SHELF_SIZE)

            val shortcuts = buildList {
                addAll(recentlyPlayed.take(SHORTCUTS))
                if (favorites.isNotEmpty() && none { it is HomeItem.FavoritesItem }) add(HomeItem.FavoritesItem(favorites))
                library.playlists.forEach { playlist ->
                    if (none { it is HomeItem.PlaylistItem && it.playlist.id == playlist.id }) add(HomeItem.PlaylistItem(playlist))
                }
            }.take(SHORTCUTS).takeIf { it.size >= 2 }.orEmpty()

            return HomeFeed(
                favorites = favorites,
                shortcuts = shortcuts,
                rediscover = rediscover,
                recentlyPlayed = recentlyPlayed,
                recentlyAdded = recentlyAdded,
                mostPlayed = mostPlayed,
                changeOfPace = changeOfPace(library, history, day),
                playlists = library.playlists,
            )
        }

        private fun changeOfPace(library: MusicLibrary, history: HistorySnapshot, day: Long): ChangeOfPace? {
            if (history.totalPlays < MIN_PLAYS_FOR_SUGGESTIONS) return null
            val byId = library.tracks.associateBy { it.id }
            val window = history.recentPlays.take(DOMINANT_WINDOW).mapNotNull { byId[it]?.groupingArtist }
            val dominant = window.groupingBy { it }.eachCount().maxByOrNull { it.value }
                ?.takeIf { window.size >= DOMINANT_MIN_PLAYS && it.value >= window.size * DOMINANT_SHARE }?.key

            val recent = history.recentPlays.toSet()
            val candidates = library.tracks
                .filter { it.id !in recent && (dominant == null || it.groupingArtist != dominant) }
                .sortedWith(compareBy<Track> { history.playCounts[it.id] ?: 0 }.thenBy { history.lastPlayedMs[it.id] ?: 0 })
            if (candidates.isEmpty()) return null
            // Least-played pool, shuffled with a daily seed so the pick varies from day to day.
            val pool = candidates.take(SHELF_SIZE * 4).shuffled(Random(day + 1)).take(SHELF_SIZE)
            val neverPlayed = library.tracks.count { (history.playCounts[it.id] ?: 0) == 0 }
            val reason = when {
                dominant != null -> ChangeOfPaceReason.MostlyOneArtist(dominant)
                neverPlayed >= SHORT_LIST -> ChangeOfPaceReason.NeverPlayed(neverPlayed)
                else -> ChangeOfPaceReason.NotLately
            }
            return ChangeOfPace(pool, reason)
        }
    }
}
