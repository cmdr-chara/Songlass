package dev.cmdrchara.songlass.core.scrobble

/**
 * Lowercase hexadecimal MD5 of the UTF-8 bytes of [text].
 *
 * Last.fm signs every Web Services call with `md5(sorted(name + value) + secret)`, so the digest is
 * part of the protocol, not a security choice of this app. It is never used to protect a password:
 * the API secret and the session key already travel only over HTTPS (see
 * `docs/design/lastfm-scrobbling-2026-10-27.md`).
 *
 * Platforms:
 * - Android: `java.security.MessageDigest`.
 * - iOS: CommonCrypto `CC_MD5` (see `Md5.ios.kt`); not compiled or exercised by the Android gate.
 *
 * The digest is a platform primitive that always exists; an implementation that cannot provide MD5
 * may throw, and callers never see a silently wrong signature.
 */
expect fun md5Hex(text: String): String

private const val HEX_DIGITS = "0123456789abcdef"

/** Shared hexadecimal formatting for the platform digests. */
internal fun ByteArray.toHexString(): String = buildString(size * 2) {
    for (byte in this@toHexString) {
        val value = byte.toInt() and 0xFF
        append(HEX_DIGITS[value ushr 4])
        append(HEX_DIGITS[value and 0x0F])
    }
}
