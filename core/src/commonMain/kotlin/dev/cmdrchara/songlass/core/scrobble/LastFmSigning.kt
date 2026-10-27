package dev.cmdrchara.songlass.core.scrobble

/**
 * Last.fm Web Services: endpoints and request signing.
 *
 * Every call is a signed form POST to [LASTFM_API_URL]. The signature is
 * `md5(concatenation of sorted "name" + "value" pairs + shared secret)`, computed on the decoded
 * values; `format`, `callback` and `api_sig` itself never take part. This file builds the exact
 * parameter set Last.fm expects, so the network layer stays free of protocol details.
 */

/** Endpoint shared by every method, including the three calls used here. */
const val LASTFM_API_URL: String = "https://ws.audioscrobbler.com/2.0/"

/** Web page where a signed-in Last.fm user grants this application access to a token. */
fun lastFmAuthorizationUrl(apiKey: String, token: String): String =
    "https://www.last.fm/api/auth/?api_key=${encodeComponent(apiKey)}&token=${encodeComponent(token)}"

/**
 * `md5(sorted "name" + "value" pairs + secret)`, exactly as Last.fm documents it.
 *
 * Names are sorted by their byte order (`String.compareTo` on ASCII parameter names), which is the
 * order every known Last.fm client uses. Values are used decoded, not percent-encoded.
 */
internal fun lastFmSignature(params: Map<String, String>, secret: String): String {
    val canonical = params.entries
        .filter { (name, _) -> name != "format" && name != "callback" && name != "api_sig" }
        .sortedBy { (name, _) -> name }
        .joinToString(separator = "") { (name, value) -> name + value }
    return md5Hex(canonical + secret)
}

/**
 * The complete signed form for one call.
 *
 * [sessionKey] is added as `sk` for authenticated methods and included in the signature like any
 * other parameter. `format=json` is added for parsing but excluded from the signature.
 */
internal fun signedLastFmForm(
    credentials: LastFmCredentials,
    method: String,
    params: Map<String, String>,
    sessionKey: String? = null,
): Map<String, String> {
    val unsigned = buildMap {
        put("method", method)
        put("api_key", credentials.apiKey)
        put("format", "json")
        if (sessionKey != null) put("sk", sessionKey)
        putAll(params)
    }
    return unsigned + ("api_sig" to lastFmSignature(unsigned, credentials.apiSecret))
}

/**
 * Percent-encodes one URL component. API keys and tokens are hexadecimal in practice; the helper
 * keeps the authorization URL correct even if that ever changes.
 */
internal fun encodeComponent(value: String): String = buildString {
    for (byte in value.encodeToByteArray()) {
        val code = byte.toInt() and 0xFF
        val unreserved = code in 'a'.code..'z'.code || code in 'A'.code..'Z'.code ||
            code in '0'.code..'9'.code || code == '-'.code || code == '_'.code ||
            code == '.'.code || code == '~'.code
        if (unreserved) {
            append(code.toChar())
        } else {
            append('%')
            append(HEX_UPPER[code ushr 4])
            append(HEX_UPPER[code and 0x0F])
        }
    }
}

private const val HEX_UPPER = "0123456789ABCDEF"
