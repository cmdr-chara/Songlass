package dev.cmdrchara.songlass.core.net

/** Canned transport for tests: records every request and replays one fixed result per URL. */
internal class FakeHttpTransport(
    private val responses: Map<String, HttpResult> = emptyMap(),
    private val defaultResponse: HttpResult = HttpResult.Failure(HttpFailureKind.Network, "no fake response"),
) : HttpTransport {
    data class Request(
        val method: String,
        val url: String,
        val form: Map<String, String> = emptyMap(),
        val headers: Map<String, String> = emptyMap(),
    )

    val requests = mutableListOf<Request>()

    override suspend fun get(url: String, headers: Map<String, String>): HttpResult {
        requests += Request("GET", url, headers = headers)
        return responses[url] ?: defaultResponse
    }

    override suspend fun post(url: String, form: Map<String, String>, headers: Map<String, String>): HttpResult {
        requests += Request("POST", url, form = form, headers = headers)
        return responses[url] ?: defaultResponse
    }
}
