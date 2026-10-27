package dev.cmdrchara.songlass.core.net

/**
 * Transport policy: only absolute HTTPS URLs without embedded credentials are accepted.
 *
 * Cleartext would expose track metadata (and provider API keys) on the wire, and a
 * `user:password@` authority would end up in transport logs, so both are rejected before a request
 * is built.
 */
internal fun isSecureHttpUrl(url: String): Boolean {
    if (!url.startsWith("https://", ignoreCase = true)) return false
    val rest = url.substring("https://".length)
    if (rest.isEmpty()) return false
    if (rest.any { it.isWhitespace() || it.isISOControl() }) return false
    val authority = rest.takeWhile { it != '/' && it != '?' && it != '#' }
    if (authority.isEmpty() || authority.contains('@')) return false
    return true
}
