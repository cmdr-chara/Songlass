package dev.cmdrchara.songlass.core.scrobble

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH

/**
 * iOS MD5 through CommonCrypto, the system digest used by the Apple platform.
 *
 * `CC_MD5` is deprecated by Apple since iOS 13 in favour of CryptoKit's `Insecure.MD5`, which is a
 * Swift overlay without a stable Kotlin/Native binding. Last.fm still requires MD5 signatures, and
 * CommonCrypto remains available on every supported iOS version.
 *
 * Limits: this actual is compiled only by the occasional `:core:compileKotlinIosArm64` check and has
 * never run on a device; scrobbling itself is disabled on iOS because `platformHttpTransport()`
 * returns null there (see `docs/verification/lastfm-scrobbling-2026-10-27.md`).
 */
@OptIn(ExperimentalForeignApi::class)
actual fun md5Hex(text: String): String {
    val input = text.encodeToByteArray()
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
    digest.usePinned { output ->
        if (input.isEmpty()) {
            // An empty C array cannot be pinned, and an empty string still has a well-defined digest.
            CC_MD5(null, 0u, output.addressOf(0).reinterpret<UByteVar>())
        } else {
            input.usePinned { bytes ->
                CC_MD5(bytes.addressOf(0), input.size.convert(), output.addressOf(0).reinterpret<UByteVar>())
            }
        }
    }
    return digest.toHexString()
}
