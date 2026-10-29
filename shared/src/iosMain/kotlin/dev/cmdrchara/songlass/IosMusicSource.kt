@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.cmdrchara.songlass

import dev.cmdrchara.songlass.core.model.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import platform.Foundation.*
import platform.MediaPlayer.*
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationOpenSettingsURLString

/** User-authorized, locally playable Music library items; cloud-only/DRM items are not playable files. */
internal class IosMusicSource(private val scope: CoroutineScope) : MusicSource {
    private val access = MutableStateFlow(authorized())
    override val hasAccess = access.asStateFlow()
    override val hasStableTrackIds = true
    var onLibraryChanged: (() -> Unit)? = null
    private var observingLibrary = false

    init {
        refreshAccess()
        listOf(MPMediaLibraryDidChangeNotification, UIApplicationDidBecomeActiveNotification).forEach { name ->
            NSNotificationCenter.defaultCenter.addObserverForName(name, null, NSOperationQueue.mainQueue) {
                refreshAccess()
                onLibraryChanged?.invoke()
            }
        }
    }

    private fun refreshAccess() {
        val allowed = authorized()
        // Register with the library service only after consent; constructing the welcome must
        // never implicitly request Music access or query the user's library.
        if (allowed && !observingLibrary) {
            MPMediaLibrary.defaultMediaLibrary().beginGeneratingLibraryChangeNotifications()
            observingLibrary = true
        } else if (!allowed && observingLibrary) {
            MPMediaLibrary.defaultMediaLibrary().endGeneratingLibraryChangeNotifications()
            observingLibrary = false
        }
        access.value = allowed
    }

    fun requestAccess() {
        when (MPMediaLibrary.authorizationStatus()) {
            MPMediaLibraryAuthorizationStatusNotDetermined -> MPMediaLibrary.requestAuthorization {
                scope.launch {
                    refreshAccess()
                    onLibraryChanged?.invoke()
                }
            }
            MPMediaLibraryAuthorizationStatusDenied, MPMediaLibraryAuthorizationStatusRestricted ->
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let {
                    UIApplication.sharedApplication.openURL(it, emptyMap<Any?, Any>(), null)
                }
            else -> {
                refreshAccess()
                onLibraryChanged?.invoke()
            }
        }
    }

    override suspend fun loadTracks(): List<Track> = withContext(Dispatchers.Default) {
        check(authorized()) { "Music library access is unavailable" }
        val tracks = MPMediaQuery.songsQuery().items.orEmpty().mapNotNull { entry ->
            val item = entry as? MPMediaItem ?: return@mapNotNull null
            if (item.cloudItem || item.protectedAsset) return@mapNotNull null
            val uri = item.assetURL?.absoluteString ?: return@mapNotNull null
            val title = item.title?.takeIf { it.isNotBlank() } ?: item.assetURL?.lastPathComponent ?: return@mapNotNull null
            Track(
                id = "ios-music:${item.persistentID}", title = title, uri = uri,
                artist = item.artist, album = item.albumTitle, albumArtist = item.albumArtist,
                durationMs = (item.playbackDuration * 1000).toLong().coerceAtLeast(0),
                trackNumber = item.albumTrackNumber.toInt().takeIf { it > 0 },
                discNumber = item.discNumber.toInt().takeIf { it > 0 },
                dateAddedMs = (item.dateAdded.timeIntervalSince1970 * 1000).toLong(),
                genre = item.genre, composers = listOfNotNull(item.composer),
            )
        }
        check(authorized()) { "Music library access changed during the scan" }
        tracks.distinctBy { it.id }
    }

    /** Resolve the current permission and ID each time; do not retain artwork after access loss. */
    fun artwork(track: Track): MPMediaItemArtwork? {
        if (!authorized()) return null
        val id = track.id.removePrefix("ios-music:").toULongOrNull() ?: return null
        val query = MPMediaQuery()
        query.addFilterPredicate(MPMediaPropertyPredicate.predicateWithValue(NSNumber(unsignedLongLong = id), MPMediaItemPropertyPersistentID))
        return (query.items?.singleOrNull() as? MPMediaItem)?.takeIf { it.assetURL?.absoluteString == track.uri }?.artwork
    }

    private fun authorized() = MPMediaLibrary.authorizationStatus() == MPMediaLibraryAuthorizationStatusAuthorized
}
