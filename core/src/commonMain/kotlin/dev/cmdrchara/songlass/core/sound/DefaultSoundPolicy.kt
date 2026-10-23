package dev.cmdrchara.songlass.core.sound

import dev.cmdrchara.songlass.core.model.Track

enum class DefaultSoundTarget { Ringtone, Alarm }

/** Fresh platform observations, including an opaque provider generation/version witness. */
data class DefaultSoundSourceFacts(
    val uri: String,
    val fileName: String,
    val folder: String,
    val dateAddedMs: Long,
    val durationMs: Long,
    val canonicalPath: String,
    val providerIdentity: String,
)

/** Conservative binding to the selected library file, before native descriptor/content checks. */
object DefaultSoundPolicy {
    private val mediaRow = Regex("content://media/[A-Za-z0-9_-]+/audio/media/[1-9][0-9]*")

    fun isSupportedSource(uri: String): Boolean = mediaRow.matches(uri)

    fun matches(selected: Track, facts: DefaultSoundSourceFacts): Boolean {
        if (!isSupportedSource(selected.uri) || selected.uri != facts.uri || facts.providerIdentity.isBlank()) return false
        if (facts.fileName.isBlank() || facts.fileName.contains('/') || facts.fileName.contains('\\')) return false
        if (!strongPath(facts.canonicalPath) || facts.dateAddedMs <= 0 || facts.durationMs <= 0) return false
        val folder = facts.folder.trim('/')
        if (folder.split('/').any { it == "." || it == ".." }) return false
        val suffix = if (folder.isEmpty()) "/${facts.fileName}" else "/$folder/${facts.fileName}"
        if (!facts.canonicalPath.endsWith(suffix)) return false
        val absolute = selected.absoluteFilePath
        val expectedName = selected.fileName
        val expectedFolder = selected.folder
        if (absolute != null) {
            if (!strongPath(absolute) || absolute != facts.canonicalPath) return false
        } else if (expectedName.isNullOrBlank() || expectedFolder.isNullOrBlank()) return false
        if (expectedName != null && expectedName != facts.fileName) return false
        if (expectedFolder != null && expectedFolder.trim('/') != facts.folder.trim('/')) return false
        if (selected.dateAddedMs != null && selected.dateAddedMs != facts.dateAddedMs) return false
        if (selected.durationMs > 0 && !sameDuration(selected.durationMs, facts.durationMs)) return false
        return true
    }

    fun unchanged(before: DefaultSoundSourceFacts, after: DefaultSoundSourceFacts): Boolean = before == after

    fun sameDuration(expected: Long, observed: Long): Boolean = expected > 0 && observed > 0 &&
        maxOf(expected, observed) - minOf(expected, observed) <= 1_000

    private fun strongPath(path: String): Boolean = path.startsWith('/') && !path.endsWith('/') &&
        !path.endsWith(" (deleted)") && path.split('/').drop(1).all { it.isNotBlank() && it != "." && it != ".." } &&
        path.none { it == '\u0000' || it == '\\' }
}

/** Exact byte accounting; subtraction avoids overflow even for adversarial file lengths. */
class DefaultSoundCopyBudget(val expectedBytes: Long) {
    init { require(expectedBytes > 0) }
    var copiedBytes: Long = 0
        private set

    fun consume(bytes: Int) {
        require(bytes > 0 && bytes.toLong() <= expectedBytes - copiedBytes) { "Source length changed" }
        copiedBytes += bytes
    }

    fun requireComplete() { check(copiedBytes == expectedBytes) { "Source ended early" } }
}
