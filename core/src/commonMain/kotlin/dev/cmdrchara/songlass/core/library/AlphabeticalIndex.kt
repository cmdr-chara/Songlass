package dev.cmdrchara.songlass.core.library

data class AlphabetSection(val label: String, val firstIndex: Int)

/** First visible item for each initial; never emits half of a surrogate pair as a label. */
object AlphabeticalIndex {
    fun build(titles: List<String?>): List<AlphabetSection> {
        val first = linkedMapOf<String, Int>()
        titles.forEachIndexed { index, title ->
            val initial = title?.trimStart()?.firstOrNull()
            val label = if (initial?.isLetter() == true) initial.uppercase() else "#"
            first.getOrPut(label) { index }
        }
        return first.map { (label, index) -> AlphabetSection(label, index) }
    }
}
