package dev.cmdrchara.songlass.core.model

/** Read real composer credits; never infer them from performing or album artists. */
object ComposerTags {
    fun isComposerField(key: String?): Boolean = when (key?.uppercase()) {
        "TCOM", "TCM", "COMPOSER", "COMPOSERS", "©WRT" -> true
        else -> false
    }

    /** Multi-value separators only: punctuation such as commas, slashes and '&' stays intact. */
    fun names(values: List<String>): List<String> = values.asSequence()
        .flatMap { it.split('\u0000', ';').asSequence() }
        .map(String::trim).filter { it.isNotEmpty() && it != "<unknown>" }
        .distinctBy { it.lowercase() }.toList()
}
