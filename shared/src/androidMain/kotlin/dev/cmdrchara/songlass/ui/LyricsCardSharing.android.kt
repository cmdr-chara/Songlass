package dev.cmdrchara.songlass.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AtomicFile
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/** Separate provider type as recommended by Android; its only root is the lyric-card cache. */
class LyricsCardFileProvider : FileProvider()

@Composable
actual fun rememberLyricsCardSharer(onResult: (Boolean) -> Unit): ((ImageBitmap, String) -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val result by rememberUpdatedState(onResult)
    return { bitmap, title -> scope.launch {
        try {
            val uri = withContext(Dispatchers.IO) { prepareCard(context, bitmap) }
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_TITLE, title)
                clipData = ClipData.newUri(context.contentResolver, title, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null))
            result(true)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { result(false) }
    } }
}

private fun prepareCard(context: Context, bitmap: ImageBitmap): Uri {
    require(bitmap.width == LYRICS_CARD_WIDTH && bitmap.height == LYRICS_CARD_HEIGHT)
    val directory = File(context.cacheDir, "lyric-cards")
    check(directory.canonicalFile.parentFile == context.cacheDir.canonicalFile)
    check(directory.isDirectory || directory.mkdir())
    val bytes = ByteArrayOutputStream().use { stream ->
        check(bitmap.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream))
        stream.toByteArray()
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val file = File(directory, "$digest.png")
    check(file.canonicalFile.parentFile == directory.canonicalFile)
    if (!file.isFile || file.length() != bytes.size.toLong()) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (failure: Exception) { atomic.failWrite(stream); throw failure }
    }
    file.setLastModified(System.currentTimeMillis())
    // Only generated cache files older than a day; never traverse directories or follow links.
    val expiry = System.currentTimeMillis() - 24L * 60 * 60 * 1000
    directory.listFiles()?.filter { candidate ->
        candidate != file && candidate.isFile && candidate.name.matches(Regex("[0-9a-f]{64}\\.png")) &&
            candidate.canonicalFile.parentFile == directory.canonicalFile && candidate.lastModified() < expiry
    }?.forEach { it.delete() }
    return FileProvider.getUriForFile(context, "${context.packageName}.lyriccards", file)
}
