@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.cmdrchara.songlass.ui

import dev.cmdrchara.songlass.core.equalizer.AutoEqRepository
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.*

internal actual suspend fun downloadAutoEqProfile(url: String): String = suspendCancellableCoroutine { continuation ->
    val request = NSURLRequest.requestWithURL(
        requireNotNull(NSURL.URLWithString(url)), NSURLRequestReloadIgnoringLocalCacheData, 15.0,
    )
    val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
        if (continuation.isActive) {
            val status = (response as? NSHTTPURLResponse)?.statusCode
            val body = if (error == null && status == 200L && data != null && data.length <= AutoEqRepository.MAX_PROFILE_BYTES.toULong())
                NSString.create(data = data, encoding = NSUTF8StringEncoding)?.toString() else null
            if (body != null) continuation.resume(body)
            else continuation.resumeWithException(IllegalStateException("AutoEQ download failed"))
        }
    }
    continuation.invokeOnCancellation { task.cancel() }
    task.resume()
}
