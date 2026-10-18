package dev.cmdrchara.songlass.core.model

/** Conservative credit parsing: never split commas, ampersands or slashes in artist names. */
object ArtistTags {
    private val credits = Regex("\\s+(?:feat\\.?|ft\\.?|featuring)\\s+", RegexOption.IGNORE_CASE)
    fun names(display: String?, explicit: List<String> = emptyList()): List<String> {
        val tagged = explicit.flatMap { it.split('\u0000', ';') }.map(String::trim).filter(String::isNotEmpty)
        val names = tagged.ifEmpty {
            display.orEmpty().split(credits).flatMap { it.split(';', '\u0000') }.map(String::trim).filter(String::isNotEmpty)
        }
        return names.distinctBy { it.lowercase() }
    }
}
