package dev.cmdrchara.songlass.core.scrobble

import java.security.MessageDigest

/**
 * Android MD5 through the platform [MessageDigest].
 *
 * `MD5` is a mandatory JCA algorithm on Android and on the host JVM that runs `core` tests, so the
 * digest is always available; a missing algorithm would be a broken platform and is left to throw
 * rather than producing an invalid signature.
 */
actual fun md5Hex(text: String): String =
    MessageDigest.getInstance("MD5").digest(text.encodeToByteArray()).toHexString()
