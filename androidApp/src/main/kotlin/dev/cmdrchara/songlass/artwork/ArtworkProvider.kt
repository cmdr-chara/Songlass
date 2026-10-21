package dev.cmdrchara.songlass.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.ui.artwork
import java.io.FileNotFoundException
import kotlin.concurrent.thread

/**
 * Serves song covers as images to other apps that show our music, such as Android Auto, which
 * load artwork by URI and cannot read covers embedded in audio files. Not exported: a browser
 * gets read access to these URIs only when it connects to the media library.
 */
class ArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "image/jpeg"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException(uri.toString())
        val id = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
        val graph = (context.applicationContext as SonglassApplication).graph
        // Application IDs survive platform reindexing. Never reinterpret a historical ID as
        // a raw MediaStore row, which may now belong to an unrelated file.
        val track = graph.allTracks.value.firstOrNull { it.id == id }
            ?: throw FileNotFoundException(uri.toString())
        val audioUri = track.uri.toUri()
        val bitmap = CoverImages.load(context, audioUri, SIZE_PX, track.artwork().seed)
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "artwork-pipe") {
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        }
        return read
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "dev.cmdrchara.songlass.artwork"
        private const val SIZE_PX = 512

        /** Every cover URI starts with this, so one prefix grant covers them all. */
        val baseUri: Uri = Uri.parse("content://$AUTHORITY/track/")

        fun uriFor(trackId: String, revision: Long = 0): Uri = Uri.withAppendedPath(baseUri, trackId).let { uri ->
            if (revision == 0L) uri else uri.buildUpon().appendQueryParameter("revision", revision.toString()).build()
        }
    }
}
