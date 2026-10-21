package dev.cmdrchara.songlass.artwork

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.ParcelFileDescriptor
import dev.cmdrchara.songlass.SonglassApplication
import dev.cmdrchara.songlass.ui.artwork
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        if (mode != "r" || uri.authority != AUTHORITY || uri.pathSegments.size != 2 || uri.pathSegments.first() != "track") {
            throw FileNotFoundException(uri.toString())
        }
        val id = uri.lastPathSegment ?: throw FileNotFoundException(uri.toString())
        val app = context.applicationContext as SonglassApplication
        val availability = app.graph.artworkAvailability
        // Application IDs survive platform reindexing. Never reinterpret a historical ID as
        // a raw MediaStore row, which may now belong to an unrelated file.
        val permit = availability.permitForId(id) ?: throw FileNotFoundException(uri.toString())
        if (!app.artworkLoader.canRead(permit.key)) throw FileNotFoundException(uri.toString())
        val revision = uri.getQueryParameter("revision")
        if (revision != null && revision.toLongOrNull() != permit.track.artworkRevision) throw FileNotFoundException(uri.toString())
        val bitmap = runBlocking(Dispatchers.IO) {
            app.artworkLoader.loadBitmap(permit.key, SIZE_PX) ?: CoverImages.generative(permit.track.artwork().seed, SIZE_PX)
        }
        if (!availability.isCurrent(permit) || !app.artworkLoader.canRead(permit.key)) throw FileNotFoundException(uri.toString())
        val (read, write) = ParcelFileDescriptor.createPipe()
        thread(name = "artwork-pipe") {
            try {
                ParcelFileDescriptor.AutoCloseOutputStream(write).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            } catch (_: IOException) {
                // A media browser can close a pipe when it scrolls away; that is not an app crash.
            }
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

        fun uriFor(trackId: String, revision: Long = 0): Uri = baseUri.buildUpon().appendPath(trackId).build().let { uri ->
            if (revision == 0L) uri else uri.buildUpon().appendQueryParameter("revision", revision.toString()).build()
        }
    }
}
