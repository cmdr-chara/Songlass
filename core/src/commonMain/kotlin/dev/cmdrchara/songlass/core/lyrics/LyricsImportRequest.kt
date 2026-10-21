package dev.cmdrchara.songlass.core.lyrics

import dev.cmdrchara.songlass.core.model.Track

/**
 * Small restorable selection for a document picker. It contains canonical identity metadata,
 * never the imported document or the previous stored text. It cannot target the current player.
 */
class LyricsImportRequest private constructor(
    internal val identity: ImportedLyricsRecord,
    val savedState: String,
) {
    val title: String get() = identity.title
    val artist: String? get() = identity.artist
    override fun toString(): String = "LyricsImportRequest(canonical=true)"

    companion object {
        fun restore(savedState: String): LyricsImportRequest? {
            val identity = ImportedLyricsCodec.decode(savedState) ?: return null
            if (identity.binding != LyricsBinding.Canonical || identity.text.isNotEmpty()) return null
            return LyricsImportRequest(identity, savedState)
        }

        internal fun create(selection: LyricsSelection): LyricsImportRequest? {
            if (!selection.canonicalId) return null
            val identity = ImportedLyricsRecord.bound(selection, "")
            val saved = try { ImportedLyricsCodec.encode(identity) } catch (_: IllegalArgumentException) { return null }
            return LyricsImportRequest(identity, saved)
        }
    }
}

/** A fresh preview binds the chosen document, verified target and exact text it would replace. */
class LyricsImportPreview internal constructor(
    val track: Track,
    val lyrics: Lyrics,
    val replacesExisting: Boolean,
    internal val text: String,
    internal val selection: LyricsSelection,
    internal val previousTarget: String?,
) {
    override fun toString(): String = "LyricsImportPreview(replacesExisting=$replacesExisting)"
}

sealed interface LyricsImportReview {
    data class Ready(val preview: LyricsImportPreview) : LyricsImportReview
    data class Rejected(val reason: LyricsImportResult) : LyricsImportReview
}
