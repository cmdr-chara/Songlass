package dev.cmdrchara.songlass.core.playlists

import dev.cmdrchara.songlass.core.model.Track
import kotlin.math.abs

internal data class M3uPath(val path: String? = null, val issue: M3uIssueKind? = null)

/** Decode file URIs once; percent signs and plus signs in ordinary paths remain literal. */
internal fun localM3uPath(raw: String): M3uPath {
    if (raw.isEmpty() || raw.length > M3u.MAX_PATH_LENGTH || raw.any { it == '\u0000' || it == '\r' || it == '\n' }) {
        return M3uPath(issue = M3uIssueKind.Malformed)
    }
    val classified = raw.trimStart()
    if (classified.startsWith("file:", ignoreCase = true)) {
        val body = classified.substring(5)
        val path = if (body.startsWith("//")) {
            val authority = body.drop(2).substringBefore('/')
            if (authority.isNotEmpty() && !authority.equals("localhost", ignoreCase = true)) {
                return M3uPath(issue = M3uIssueKind.Unsupported)
            }
            body.drop(2 + authority.length)
        } else body
        if (!path.startsWith('/') || path.contains('?') || path.contains('#')) return M3uPath(issue = M3uIssueKind.Malformed)
        val decoded = decodeFilePath(path) ?: return M3uPath(issue = M3uIssueKind.Malformed)
        return M3uPath(decoded)
    }
    val windowsDrive = classified.length >= 3 && classified[0].isLetter() && classified[1] == ':' && (classified[2] == '\\' || classified[2] == '/')
    val colon = classified.indexOf(':')
    if (!windowsDrive && colon > 0 && classified.take(colon).all { it.isLetterOrDigit() || it in "+-." }) {
        return M3uPath(issue = M3uIssueKind.Unsupported)
    }
    // UNC paths address another machine, not a local recording.
    if (classified.startsWith("\\\\") || classified.startsWith("//")) return M3uPath(issue = M3uIssueKind.Unsupported)
    return M3uPath(raw)
}

private fun decodeFilePath(path: String): String? {
    val bytes = ArrayList<Byte>(path.length)
    var index = 0
    while (index < path.length) {
        if (path[index] == '%') {
            if (index + 2 >= path.length) return null
            val high = path[index + 1].digitToIntOrNull(16) ?: return null
            val low = path[index + 2].digitToIntOrNull(16) ?: return null
            bytes += ((high shl 4) or low).toByte()
            index += 3
        } else {
            val end = path.indexOf('%', index).takeIf { it >= 0 } ?: path.length
            bytes.addAll(path.substring(index, end).encodeToByteArray().toList())
            index = end
        }
    }
    val decoded = try { bytes.toByteArray().decodeToString(throwOnInvalidSequence = true) } catch (_: CharacterCodingException) { return null }
    return decoded.takeUnless { it.any { char -> char == '\u0000' || char == '\r' || char == '\n' || char == '\\' } }
}

private fun segments(path: String): List<String> = buildList {
    for (part in path.replace('\\', '/').split('/')) when (part) {
        "", "." -> Unit
        ".." -> if (isNotEmpty() && last() != "..") removeAt(lastIndex) else add(part)
        else -> add(part)
    }
}

internal data class M3uMatch(val track: Track? = null, val issue: M3uIssueKind? = null)

/** Path/name indexes; a reversed trie is built only for a queried, ambiguous basename. */
internal class M3uLibraryIndex(tracks: List<Track>) {
    private data class PathTrack(val track: Track, val parts: List<String>)
    private class PathNode {
        val children = mutableMapOf<String, PathNode>()
        val tracks = mutableListOf<Track>()
    }
    private val byName = mutableMapOf<String, MutableList<PathTrack>>()
    private val roots = mutableMapOf<String, PathNode>()
    private val exact = mutableMapOf<String, MutableList<Track>>()
    private val absolute = mutableMapOf<String, MutableList<Track>>()
    private val fingerprints = mutableMapOf<Pair<String, String>, MutableList<Track>>()
    private val duplicateIds = tracks.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys

    init {
        for (track in tracks) {
            val relativePath = track.path?.takeIf(M3u::exportablePath)
            val absolutePath = track.absoluteFilePath?.takeIf(M3u::absoluteFilePath)
            if (relativePath != null) {
                val parts = segments(relativePath)
                exact.getOrPut(parts.joinToString("/")) { mutableListOf() }.add(track)
            }
            if (absolutePath != null) {
                absolute.getOrPut(segments(absolutePath).joinToString("/")) { mutableListOf() }.add(track)
            }
            val path = absolutePath ?: relativePath
            if (path != null) {
                val parts = segments(path)
                parts.lastOrNull()?.lowercase()?.let { name -> byName.getOrPut(name) { mutableListOf() }.add(PathTrack(track, parts)) }
            }
            if (track.title.isNotBlank() && !track.artist.isNullOrBlank() && track.durationMs > 0) {
                fingerprints.getOrPut(track.title.normalized() to track.artist.normalized()) { mutableListOf() }.add(track)
            }
        }
    }

    fun match(entry: M3uEntry): M3uMatch {
        // parseDetailed has already decoded URIs; manually constructed entries still get bounds.
        if (entry.path.isEmpty() || entry.path.length > M3u.MAX_PATH_LENGTH || entry.path.any { it == '\u0000' || it == '\r' || it == '\n' }) {
            return M3uMatch(issue = M3uIssueKind.Malformed)
        }
        val parts = segments(entry.path)
        // Equal storage-relative names on two volumes are different files. A real absolute
        // address can identify one without relying on tags or arbitrary scan ordering.
        val absoluteMatch = if (entry.path.startsWith('/')) absolute[parts.joinToString("/")] else null
        if (absoluteMatch != null) return choosePath(entry, absoluteMatch)
        val direct = exact[parts.joinToString("/")]
        if (direct != null) return choosePath(entry, direct)
        val name = parts.lastOrNull()?.lowercase()
        val sameName = name?.let { byName[it] }.orEmpty()
        var depth = 0
        if (sameName.size == 1) {
            val candidate = sameName.single()
            while (depth < parts.size && depth < candidate.parts.size &&
                parts[parts.lastIndex - depth].equals(candidate.parts[candidate.parts.lastIndex - depth], ignoreCase = true)) depth++
            if (depth >= 2 || depth == 1 && parts.size == 1) return choosePath(entry, listOf(candidate.track))
        } else if (sameName.isNotEmpty() && name != null) {
            var node = roots.getOrPut(name) {
                val root = PathNode()
                for (candidate in sameName) {
                    var branch = root
                    branch.tracks += candidate.track
                    for (part in candidate.parts.asReversed().drop(1)) {
                        branch = branch.children.getOrPut(part.lowercase()) { PathNode() }
                        branch.tracks += candidate.track
                    }
                }
                root
            }
            depth = 1
            for (part in parts.asReversed().drop(1)) {
                node = node.children[part.lowercase()] ?: break
                depth++
            }
            if (depth >= 2 || parts.size == 1) return choosePath(entry, node.tracks)
        }
        // A common basename alone cannot override a conflicting folder. Tags must prove a
        // unique plausible relocation, including the precision supplied by EXTINF.
        if (entry.title.isNullOrBlank() || entry.artist.isNullOrBlank() || entry.expectedDurationMs == null) {
            return M3uMatch(issue = if (depth > 0) M3uIssueKind.Ambiguous else M3uIssueKind.Missing)
        }
        val candidates = fingerprints[entry.title.normalized() to entry.artist.normalized()].orEmpty()
        val compatible = candidates.asSequence().filter { knownFactsAgree(entry, it) }.take(2).toList()
        return when {
            compatible.size == 1 && compatible.single().id !in duplicateIds -> M3uMatch(compatible.single())
            compatible.size > 1 || compatible.any { it.id in duplicateIds } -> M3uMatch(issue = M3uIssueKind.Ambiguous)
            candidates.isNotEmpty() -> M3uMatch(issue = M3uIssueKind.Conflict)
            else -> M3uMatch(issue = M3uIssueKind.Missing)
        }
    }

    private fun choosePath(entry: M3uEntry, candidates: List<Track>): M3uMatch {
        if (candidates.size == 1) {
            val track = candidates.single()
            return when {
                track.id in duplicateIds -> M3uMatch(issue = M3uIssueKind.Ambiguous)
                !knownFactsAgree(entry, track) -> M3uMatch(issue = M3uIssueKind.Conflict)
                else -> M3uMatch(track)
            }
        }
        // Duplicate paths and equally long suffixes remain uncertain unless all competitors
        // have known conflicting metadata. Unknown fields do not disprove another candidate.
        if (entry.title.isNullOrBlank() || entry.artist.isNullOrBlank() || entry.expectedDurationMs == null) {
            return M3uMatch(issue = M3uIssueKind.Ambiguous)
        }
        val compatible = candidates.asSequence().filter { knownFactsAgree(entry, it) }.take(2).toList()
        val only = compatible.singleOrNull()
        return when {
            only != null && only.durationMs > 0 && only.title.isNotBlank() && !only.artist.isNullOrBlank() && only.id !in duplicateIds -> M3uMatch(only)
            compatible.isEmpty() -> M3uMatch(issue = M3uIssueKind.Conflict)
            else -> M3uMatch(issue = M3uIssueKind.Ambiguous)
        }
    }

    private fun knownFactsAgree(entry: M3uEntry, track: Track): Boolean {
        val expectedLabel = listOfNotNull(track.artist?.takeIf { it.isNotBlank() }, track.title).joinToString(" - ").singleLine()
        val exactLabel = entry.displayLabel?.normalized() == expectedLabel.normalized()
        if (!exactLabel) {
            if (!entry.title.isNullOrBlank() && track.title.isNotBlank() && entry.title.normalized() != track.title.normalized()) return false
            if (!entry.artist.isNullOrBlank() && !track.artist.isNullOrBlank() && entry.artist.normalized() != track.artist.normalized()) return false
        }
        val expectedDuration = entry.expectedDurationMs
        return expectedDuration == null || track.durationMs <= 0 ||
            abs(expectedDuration - track.durationMs) <= entry.durationToleranceMs.coerceIn(1, 1_000)
    }
}

private fun String.normalized(): String = trim().singleLine().lowercase()
