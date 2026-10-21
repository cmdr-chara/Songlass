package dev.cmdrchara.songlass.core.playlists

class SmartPlaylistChangedException : IllegalStateException("Smart playlist changed; reload before saving")

/** The record the user opened is part of the draft, and must be restored with its edited values. */
data class SmartPlaylistEditDraft(
    val original: SmartPlaylist?,
    val name: String,
    val rules: SmartRules,
) {
    fun isCurrent(playlists: SmartPlaylists, routeId: String?): Boolean =
        routeId == original?.id && (original == null || playlists.get(original.id) == original)

    /** Single-owner operation: compare immediately before the repository's synchronous write. */
    fun saveTo(playlists: SmartPlaylists, routeId: String?): String {
        if (!isCurrent(playlists, routeId)) throw SmartPlaylistChangedException()
        return playlists.save(routeId, name, rules)
    }

    companion object {
        fun open(original: SmartPlaylist?): SmartPlaylistEditDraft =
            SmartPlaylistEditDraft(original, original?.name.orEmpty(), original?.rules ?: SmartRules())
    }
}

/** One saveable value: incomplete state cannot pair an old draft with a newly read baseline. */
object SmartPlaylistEditDraftCodec {
    fun encode(draft: SmartPlaylistEditDraft): List<String> = listOf(
        "1", draft.original?.id.orEmpty(), draft.original?.name.orEmpty(),
        draft.original?.rules?.let(SmartRulesCodec::encode).orEmpty(), draft.name,
        SmartRulesCodec.encode(draft.rules),
    )

    fun decode(values: List<String>): SmartPlaylistEditDraft? {
        if (values.size != 6 || values[0] != "1") return null
        val original = if (values[1].isEmpty()) {
            if (values[2].isNotEmpty() || values[3].isNotEmpty()) return null
            null
        } else SmartPlaylist(values[1], values[2], SmartRulesCodec.decode(values[3]) ?: return null)
        return SmartPlaylistEditDraft(original, values[4], SmartRulesCodec.decode(values[5]) ?: return null)
    }
}
