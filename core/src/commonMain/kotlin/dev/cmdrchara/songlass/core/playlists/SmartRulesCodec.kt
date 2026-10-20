package dev.cmdrchara.songlass.core.playlists

/** Reads both legacy layouts; v2 is written only when a rule needs the new fields. */
object SmartRulesCodec {
    fun encode(rules: SmartRules): String {
        require(rules.isValid)
        val basic = listOf(rules.favoritesOnly.toString(), rules.playStatus.name,
            rules.addedWithinDays?.toString().orEmpty(), rules.artist, rules.folder, rules.sort.name,
            rules.limit?.toString().orEmpty())
        val advanced = listOf(rules.match.name, rules.genre, rules.yearFrom?.toString().orEmpty(),
            rules.yearTo?.toString().orEmpty(), rules.durationFromSeconds?.toString().orEmpty(),
            rules.durationToSeconds?.toString().orEmpty(), rules.excludeArtist, rules.excludeFolder, rules.excludeGenre)
        val needsV2 = rules.match != SmartMatch.All || advanced.drop(1).any { it.isNotEmpty() }
        val values = if (needsV2) basic + advanced else basic
        return (if (needsV2) "v2:" else "v1:") + values.joinToString("") { "${it.length}:$it" }
    }

    fun decode(text: String?): SmartRules? {
        text ?: return null
        val v2 = text.startsWith("v2:")
        val values = when {
            text.startsWith("v1:") || v2 -> fields(text, if (v2) 16 else 7)
            else -> text.split('\u001F').takeIf { it.size == 7 }
        } ?: return null
        fun number(index: Int): Int? = values[index].takeIf { it.isNotEmpty() }?.toIntOrNull()
        val numeric = if (v2) listOf(2, 6, 9, 10, 11, 12) else listOf(2, 6)
        if (numeric.any { values[it].isNotEmpty() && number(it) == null }) return null
        val rules = SmartRules(
            favoritesOnly = values[0].toBooleanStrictOrNull() ?: return null,
            playStatus = SmartPlayStatus.entries.firstOrNull { it.name == values[1] } ?: return null,
            addedWithinDays = number(2), artist = values[3], folder = values[4],
            sort = SmartSort.entries.firstOrNull { it.name == values[5] } ?: return null, limit = number(6),
            match = if (v2) SmartMatch.entries.firstOrNull { it.name == values[7] } ?: return null else SmartMatch.All,
            genre = if (v2) values[8] else "", yearFrom = if (v2) number(9) else null,
            yearTo = if (v2) number(10) else null, durationFromSeconds = if (v2) number(11) else null,
            durationToSeconds = if (v2) number(12) else null, excludeArtist = if (v2) values[13] else "",
            excludeFolder = if (v2) values[14] else "", excludeGenre = if (v2) values[15] else "",
        )
        return rules.takeIf { it.isValid }
    }

    private fun fields(text: String, count: Int): List<String>? {
        var at = 3
        val result = ArrayList<String>(count)
        repeat(count) {
            val colon = text.indexOf(':', at).takeIf { it >= at } ?: return null
            val length = text.substring(at, colon).toIntOrNull()?.takeIf { it >= 0 } ?: return null
            val start = colon + 1
            if (length > text.length - start) return null
            result += text.substring(start, start + length)
            at = start + length
        }
        return result.takeIf { at == text.length }
    }
}
