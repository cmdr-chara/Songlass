package dev.cmdrchara.songlass.core.playlists

/**
 * One in-memory export payload, owned by one document-picker registration. Never serialize this
 * object into Android saved state or reuse it for a later launch. Its owner confines calls to the
 * main thread; a late or duplicate result can claim no payload after cancellation/disposal.
 */
class M3uExportRequest(content: String) {
    private var payload: String? = content

    fun claim(): String? = payload.also { payload = null }

    /** Used for both picker cancellation and disposal of the owning UI. */
    fun invalidate() { payload = null }
}
