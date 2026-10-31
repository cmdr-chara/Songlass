package dev.cmdrchara.songlass.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.DocumentsContract
import dev.cmdrchara.songlass.artwork.FolderArtwork
import androidx.compose.ui.platform.LocalContext
import dev.cmdrchara.songlass.core.artwork.ArtworkDownloader
import dev.cmdrchara.songlass.core.artwork.ArtworkFileStore
import dev.cmdrchara.songlass.core.artwork.ArtworkImageResult
import dev.cmdrchara.songlass.core.artwork.StoredArtworkFile
import dev.cmdrchara.songlass.core.net.HttpFailureKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InterruptedIOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Android corner of the online cover feature: a private directory, a byte-level image downloader
 * and the current connection type.
 *
 * The directory is inside the app's own files (never the media store), so downloaded covers are
 * removed with the app's data and are invisible to other apps.
 */
@Composable
actual fun rememberPlatformOnlineArtwork(): OnlineArtworkPlatform? {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        OnlineArtworkPlatform(
            fileStore = FileArtworkStore(File(context.filesDir, ARTWORK_DIRECTORY)),
            downloader = HttpsArtworkDownloader(),
            isMeteredNetwork = { meteredConnection(context) },
        )
    }
}

@Composable
actual fun rememberArtworkFolderPicker(): ArtworkFolderPicker? {
    val context = LocalContext.current.applicationContext
    val revision by FolderArtwork.revision.collectAsState()
    val selected = remember(context, revision) { FolderArtwork.selected(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) FolderArtwork.select(context, uri)
    }
    // Do not synchronously query a remote document provider from composition.
    val name = selected?.let { runCatching { DocumentsContract.getTreeDocumentId(it).substringAfterLast('/').substringAfter(':') }.getOrNull() }
    return ArtworkFolderPicker(name, { launcher.launch(selected) }, { FolderArtwork.select(context, null) })
}

private const val ARTWORK_DIRECTORY = "online-covers"

/** Copy of the shared transport's identification: no install, device or user identifiers. */
private const val USER_AGENT = "Songlass/0.1.0 (Android)"

/**
 * Files under [directory], reached only through the names the core derives (a hash plus `.img`).
 *
 * Every operation runs on the IO dispatcher and swallows its own I/O failure into `null`/`false`:
 * a full disk or a deleted directory must never crash the player. Writes go through a temporary
 * file and a rename, so a cover is either the previous one or the new one, never half an image.
 */
private class FileArtworkStore(private val directory: File) : ArtworkFileStore {
    override suspend fun list(): List<StoredArtworkFile> = withContext(Dispatchers.IO) {
        directory.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && !it.name.endsWith(TEMP_SUFFIX) }
            ?.map { StoredArtworkFile(it.name, it.length(), it.lastModified()) }
            ?.toList()
            .orEmpty()
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        if (!isSafeName(name)) return@withContext null
        runCatching { File(directory, name).takeIf { it.isFile }?.readBytes() }.getOrNull()
    }

    override suspend fun write(name: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (!isSafeName(name)) return@withContext false
        runCatching {
            if (!directory.isDirectory && !directory.mkdirs()) return@runCatching false
            val temporary = File(directory, name + TEMP_SUFFIX)
            temporary.writeBytes(bytes)
            val target = File(directory, name)
            if (!temporary.renameTo(target)) {
                temporary.delete()
                false
            } else {
                true
            }
        }.getOrDefault(false)
    }

    override suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        if (!isSafeName(name)) return@withContext false
        runCatching { File(directory, name).delete() }.getOrDefault(false)
    }

    /** No separators and no hidden names: the store only ever sees flat, derived file names. */
    private fun isSafeName(name: String): Boolean =
        name.isNotEmpty() && !name.startsWith('.') && '/' !in name && '\\' !in name
}

/**
 * Byte-level image download over HTTPS.
 *
 * The shared [dev.cmdrchara.songlass.core.net.HttpTransport] returns bodies already decoded as
 * text, which would corrupt a JPEG, so this is a deliberately separate, narrower path: HTTPS only,
 * image content types only, bounded response size, fixed timeouts and the same User-Agent as the
 * text transport. Redirects are followed by the platform connection; the final URL must still be
 * HTTPS, so a downgrade is rejected. Nothing is logged and no URL is persisted.
 */
private class HttpsArtworkDownloader : ArtworkDownloader {
    override suspend fun downloadImage(url: String): ArtworkImageResult = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://", ignoreCase = true)) return@withContext malformed()
        var connection: HttpURLConnection? = null
        try {
            val opened = URL(url).openConnection() as? HttpsURLConnection ?: return@withContext malformed()
            connection = opened
            opened.connectTimeout = CONNECT_TIMEOUT_MS
            opened.readTimeout = READ_TIMEOUT_MS
            opened.instanceFollowRedirects = true
            opened.setRequestProperty("User-Agent", USER_AGENT)
            opened.setRequestProperty("Accept", "image/*")
            val status = opened.responseCode
            if (status !in 200..299) return@withContext ArtworkImageResult.Failure(HttpFailureKind.HttpError)
            // A redirect chain must not end in cleartext.
            if (!opened.url.protocol.equals("https", ignoreCase = true)) return@withContext malformed()
            val declaredLength = opened.contentLengthLong
            if (declaredLength > MAX_BYTES) return@withContext malformed()
            val contentType = opened.contentType
            if (contentType != null && !contentType.startsWith("image/")) return@withContext malformed()
            val bytes = opened.inputStream.use { stream -> readBounded(stream) } ?: return@withContext malformed()
            if (bytes.isEmpty()) malformed() else ArtworkImageResult.Success(bytes, contentType)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SocketTimeoutException) {
            ArtworkImageResult.Failure(HttpFailureKind.Timeout)
        } catch (_: InterruptedIOException) {
            ArtworkImageResult.Failure(HttpFailureKind.Timeout)
        } catch (_: IOException) {
            ArtworkImageResult.Failure(HttpFailureKind.Network)
        } catch (_: Exception) {
            malformed()
        } finally {
            connection?.disconnect()
        }
    }

    /** Reads at most [MAX_BYTES]; null as soon as the provider sends more than that. */
    private fun readBounded(stream: InputStream): ByteArray? {
        val output = ByteArrayOutputStream()
        val chunk = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = stream.read(chunk)
            if (read < 0) break
            total += read
            if (total > MAX_BYTES) return null
            output.write(chunk, 0, read)
        }
        return output.toByteArray()
    }

    private fun malformed() = ArtworkImageResult.Failure(HttpFailureKind.Malformed)

    private companion object {
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 20_000

        /** Far above a 600 px cover, small enough that a wrong URL cannot exhaust memory. */
        const val MAX_BYTES = 2 * 1024 * 1024
    }
}

private const val TEMP_SUFFIX = ".tmp"

/**
 * Whether the current connection is metered, or true when the platform cannot say (no active
 * network included): "Wi-Fi only" must never spend mobile data because of an unknown answer.
 *
 * Needs `ACCESS_NETWORK_STATE`, declared in the manifest: without it the capability query throws
 * and the conservative answer above applies.
 */
private fun meteredConnection(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
    val network = manager.activeNetwork ?: return@runCatching true
    val capabilities = manager.getNetworkCapabilities(network) ?: return@runCatching true
    !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}.getOrDefault(true)
