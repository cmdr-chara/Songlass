package dev.cmdrchara.songlass.library

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import dev.cmdrchara.songlass.MusicSource
import dev.cmdrchara.songlass.core.model.TagNumbers
import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** Reads the device's music from MediaStore. Needs READ_MEDIA_AUDIO (Android 13+). */
class MediaStoreMusicSource(private val context: Context) : MusicSource {
    private val _hasAccess = MutableStateFlow(checkAccess())
    override val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    /** Call after the permission dialog returns. */
    fun refreshAccess() {
        _hasAccess.value = checkAccess()
    }

    private fun checkAccess(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED

    override suspend fun loadTracks(): List<Track> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.CD_TRACK_NUMBER,
            MediaStore.Audio.Media.DISC_NUMBER,
            MediaStore.Audio.Media.RELATIVE_PATH,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.SIZE,
        )
        val tracks = mutableListOf<Track>()
        val extraTags = ExtraAudioTags(context)
        context.contentResolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            null,
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumArtist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val legacyTrack = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val cdTrack = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER)
            val disc = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER)
            val relativePath = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
            val dateAdded = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val displayName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val year = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val modified = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
            val size = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            while (cursor.moveToNext()) {
                val mediaId = cursor.getLong(id)
                val (legacyDisc, legacyNumber) = TagNumbers.splitLegacyTrack(
                    if (cursor.isNull(legacyTrack)) null else cursor.getInt(legacyTrack),
                )
                val artistTag = cursor.getString(artist).cleanTag()
                val folderPath = cursor.getString(relativePath)?.trim('/')
                val folder = folderPath?.substringAfterLast('/')
                // Without an album tag MediaStore uses the folder name; without an artist either,
                // that is almost certainly such a fallback rather than a real album.
                val albumTag = cursor.getString(album).cleanTag()
                    ?.takeUnless { artistTag == null && it == folder }
                val uri = ContentUris.withAppendedId(collection, mediaId)
                val extra = extraTags.read(uri, "$mediaId:${cursor.getLong(modified)}:${cursor.getLong(size)}")
                tracks += Track(
                    id = mediaId.toString(),
                    title = cursor.getString(title)?.takeIf { it.isNotBlank() } ?: continue,
                    uri = uri.toString(),
                    artists = extra.artists,
                    releaseDate = extra.date ?: dev.cmdrchara.songlass.core.model.ReleaseDate.parse(cursor.getString(year)),
                    artist = artistTag,
                    album = albumTag,
                    albumArtist = cursor.getString(albumArtist).cleanTag(),
                    durationMs = cursor.getLong(duration),
                    trackNumber = TagNumbers.parse(cursor.getString(cdTrack)) ?: legacyNumber,
                    discNumber = TagNumbers.parse(cursor.getString(disc)) ?: legacyDisc,
                    folder = folderPath,
                    // DATE_ADDED is in seconds.
                    dateAddedMs = if (cursor.isNull(dateAdded)) null else cursor.getLong(dateAdded) * 1000,
                    fileName = cursor.getString(displayName)?.takeIf { it.isNotBlank() },
                )
            }
        }
        extraTags.save()
        tracks
    }
}

/** MediaStore reports missing tags as blank or "<unknown>". */
private fun String?.cleanTag(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
