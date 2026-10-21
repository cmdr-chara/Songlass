package dev.cmdrchara.songlass.core.lyrics

import kotlin.text.CharacterCodingException

/** Bounded content/backoff shared by the Android lyrics transports; it never performs I/O. */
object LyricsTransportPolicy {
    const val MAX_RESPONSE_BYTES = 512 * 1024
    const val MAX_RETRY_DELAY_MS = 60L * 60 * 1_000

    /** A malformed byte sequence is unavailable data, never silently repaired lyrics. */
    fun decodeUtf8(bytes: ByteArray, maxBytes: Int = MAX_RESPONSE_BYTES): String? {
        require(maxBytes in 1..MAX_RESPONSE_BYTES)
        if (bytes.size > maxBytes) return null
        return try { bytes.decodeToString(throwOnInvalidSequence = true) }
        catch (_: CharacterCodingException) { null }
    }

    /**
     * Retry-After delta seconds or a platform-parsed HTTP date, capped at the same one-hour
     * maximum for both providers. Arithmetic never wraps for hostile dates or decimal overflow.
     */
    fun retryAfterMillis(
        value: String?,
        nowEpochMs: Long,
        parseDateMillis: (String) -> Long? = { null },
    ): Long? {
        val header = value?.trim()?.takeIf { it.length in 1..256 } ?: return null
        if (header.all { it in '0'..'9' }) {
            val seconds = header.toLongOrNull() ?: return MAX_RETRY_DELAY_MS
            return seconds.coerceAtMost(MAX_RETRY_DELAY_MS / 1_000) * 1_000
        }
        val retryAt = parseDateMillis(header) ?: return null
        if (retryAt <= nowEpochMs) return 0
        // Add only when representable; subtract only after proving the distance is bounded.
        if (nowEpochMs <= Long.MAX_VALUE - MAX_RETRY_DELAY_MS &&
            retryAt >= nowEpochMs + MAX_RETRY_DELAY_MS) return MAX_RETRY_DELAY_MS
        return retryAt - nowEpochMs
    }
}
