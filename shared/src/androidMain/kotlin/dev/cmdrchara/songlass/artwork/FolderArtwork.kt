package dev.cmdrchara.songlass.artwork

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import dev.cmdrchara.songlass.core.artwork.FolderArtworkNames
import dev.cmdrchara.songlass.core.model.Track
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared Android bridge: reads nearby files or a granted tree without broad storage access. */
object FolderArtwork {
    private const val PREFS = "artwork-folders"
    private const val TREE = "tree"
    private const val MAX_BYTES = 10 * 1024 * 1024
    val revision = MutableStateFlow(0L)

    fun selected(context: Context): Uri? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getString(TREE, null)?.let(Uri::parse)

    fun select(context: Context, uri: Uri?): Boolean {
        val resolver = context.contentResolver
        if (uri != null && runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.isFailure) return false
        val old = selected(context)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (uri == null) remove(TREE) else putString(TREE, uri.toString())
        }.apply()
        if (old != null && old != uri) runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        revision.value += 1
        return true
    }

    /** IO callers only. Image decoding stays in the loader's bounded decode semaphore. */
    fun bytes(context: Context, track: Track): ByteArray? {
        val names = FolderArtworkNames.imageNames(track.fileName, track.album, track.title)
        val directory = track.absoluteFilePath?.let(::File)?.parentFile
        if (directory != null) {
            localFile(directory, names)?.let { return it }
            for (name in FolderArtworkNames.artworkDirectories) localFile(File(directory, name), names)?.let { return it }
            if (FolderArtworkNames.isDiscDirectory(directory.name)) directory.parentFile?.let { parent ->
                localFile(parent, names)?.let { return it }
                for (name in FolderArtworkNames.artworkDirectories) localFile(File(parent, name), names)?.let { return it }
            }
        }
        val tree = selected(context) ?: return null
        if (context.contentResolver.persistedUriPermissions.none { it.uri == tree && it.isReadPermission }) return null
        return runCatching {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val rootName = context.contentResolver.query(rootUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
            val genericRoot = FolderArtworkNames.sameName(rootName.orEmpty(), track.album) ||
                (directory != null && FolderArtworkNames.sameName(rootName.orEmpty(), directory.name))
            documentFile(context, tree, rootId,
                FolderArtworkNames.imageNames(track.fileName, track.album, track.title, generic = genericRoot))?.let { return it }
            val roots = children(context, tree, rootId, directoryNames =
                listOfNotNull(track.album, track.groupingArtist) + FolderArtworkNames.artworkDirectories)
            val album = roots.firstOrNull { it.directory && FolderArtworkNames.sameName(it.name, track.album) }
            if (album != null) documentFile(context, tree, album.id, names)?.let { return it }
            val artist = roots.firstOrNull { it.directory && FolderArtworkNames.sameName(it.name, track.groupingArtist) }
            if (artist != null) {
                val artistChildren = children(context, tree, artist.id, directoryNames = listOfNotNull(track.album))
                val nestedAlbum = artistChildren.firstOrNull { it.directory && FolderArtworkNames.sameName(it.name, track.album) }
                if (nestedAlbum != null) documentFile(context, tree, nestedAlbum.id, names)?.let { return it }
                documentFile(context, tree, artist.id,
                    FolderArtworkNames.imageNames(track.fileName, track.album, track.title, generic = false))?.let { return it }
            }
            // Conventional artwork subdirectories of an explicitly selected album folder.
            if (genericRoot) for (name in FolderArtworkNames.artworkDirectories) {
                val child = roots.firstOrNull { it.directory && it.name.equals(name, ignoreCase = true) } ?: continue
                documentFile(context, tree, child.id, names)?.let { return it }
            }
            null
        }.getOrNull()
    }

    private fun localFile(directory: File, names: List<String>): ByteArray? = runCatching {
        val files = directory.listFiles()?.filter {
            FolderArtworkNames.isRequestedEntry(it.name, false, names, emptyList())
        }.orEmpty()
        for (name in names) {
            val file = files.firstOrNull { it.name.equals(name, ignoreCase = true) && it.isFile } ?: continue
            if (file.length() !in 1..MAX_BYTES.toLong()) continue
            file.inputStream().use(::boundedBytes)?.let { return it }
        }
        null
    }.getOrNull()

    private data class Document(val id: String, val name: String, val directory: Boolean)
    private fun children(
        context: Context, tree: Uri, parentId: String,
        imageNames: List<String> = emptyList(), directoryNames: List<String> = emptyList(),
    ): List<Document> {
        val result = mutableListOf<Document>()
        context.contentResolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val directory = cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                if (FolderArtworkNames.isRequestedEntry(name, directory, imageNames, directoryNames) &&
                    result.none { it.directory == directory &&
                        if (directory) FolderArtworkNames.sameName(it.name, name) else it.name.equals(name, ignoreCase = true) }) {
                    result += Document(cursor.getString(0), name, directory)
                }
            }
        }
        return result
    }

    private fun documentFile(context: Context, tree: Uri, parentId: String, names: List<String>): ByteArray? {
        val files = children(context, tree, parentId, imageNames = names)
        for (name in names) {
            val file = files.firstOrNull { !it.directory && it.name.equals(name, ignoreCase = true) } ?: continue
            context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, file.id))
                ?.use(::boundedBytes)?.let { return it }
        }
        return null
    }

    private fun boundedBytes(input: InputStream): ByteArray? {
        val bytes = input.readNBytes(MAX_BYTES + 1).takeIf { it.isNotEmpty() && it.size <= MAX_BYTES } ?: return null
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bytes.takeIf { bounds.outWidth > 0 && bounds.outHeight > 0 }
    }
}
