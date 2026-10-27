package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.settings.AppPreferences
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One collection's resumption choices; a null field inherits the global preference. */
data class ResumeOverride(
    /** null keeps the global Resume setting, true/false forces it for this collection. */
    val resumePerTrack: Boolean? = null,
    /** null keeps the global threshold; otherwise one of [AppSettings.LONG_TRACK_CHOICES_MS]. */
    val longTrackThresholdMs: Long? = null,
) {
    val isEmpty: Boolean get() = resumePerTrack == null && longTrackThresholdMs == null
}

/** The effective resumption policy: global preferences with the collection override applied. */
data class ResumePolicy(val resumePerTrack: Boolean, val longTrackThresholdMs: Long)

/** Global settings plus the override of the collection playback was started from. */
fun AppPreferences.resumePolicy(override: ResumeOverride?): ResumePolicy = ResumePolicy(
    resumePerTrack = override?.resumePerTrack ?: resumePerTrack,
    longTrackThresholdMs = override?.longTrackThresholdMs ?: longTrackThresholdMs,
)

/** A stored override with the collection it belongs to. */
data class ResumeOverrideEntry(val context: PlayContext, val override: ResumeOverride)

/**
 * A stored override resolved against the current data. [name] is null when the collection no
 * longer exists: the entry stays visible as unavailable until the user removes it.
 */
data class ResumeOverrideSummary(
    val context: PlayContext,
    val name: String?,
    val override: ResumeOverride,
) {
    val available: Boolean get() = name != null
}

/**
 * Per-collection resumption overrides for folders and playlists, keyed by [PlayContext.key].
 * A folder is identified by its canonical path (the same id as its [Playlist]) and a playlist by
 * its stable id, never by a recycled MediaStore id. Saved under the `library.` namespace, so the
 * standard backup carries them; a missing or malformed record is ignored without rewriting it.
 *
 * Reads and writes are owner-thread only, like the other preference stores.
 */
class ResumeOverrides(
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
) {
    private val _entries = MutableStateFlow(read())

    /** Stored overrides, in insertion order. */
    val entries: StateFlow<List<ResumeOverrideEntry>> = _entries.asStateFlow()

    /** The override for [context], or null when the collection inherits the global settings. */
    fun overrideFor(context: PlayContext?): ResumeOverride? {
        val key = context?.let(::normalize)?.key ?: return null
        return _entries.value.firstOrNull { it.context.key == key }?.override
    }

    /** Re-reads the overrides from [store], after a backup restore replaced them. */
    fun reload() = prepareReload().invoke()

    /** Decode the staging store off the owner thread; publish only after its commit. */
    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = read(store)
        return { _entries.value = loaded }
    }

    /**
     * Sets (or replaces) the override of a folder or playlist. An empty override removes it.
     * Only collections that can own resumption are accepted; the threshold must be one of the
     * global choices, so the sheet and the stored value never disagree.
     */
    fun set(context: PlayContext, override: ResumeOverride) {
        if (!mutations.canMutate) return
        val target = requireNotNull(supported(context)) { "resumption overrides belong to folders and playlists" }
        require(override.longTrackThresholdMs == null || override.longTrackThresholdMs in AppSettings.LONG_TRACK_CHOICES_MS)
        val current = _entries.value
        val next = when {
            override.isEmpty -> current.filterNot { it.context.key == target.key }
            current.any { it.context.key == target.key } -> current.map {
                if (it.context.key == target.key) ResumeOverrideEntry(target, override) else it
            }
            else -> current + ResumeOverrideEntry(target, override)
        }
        if (next == current) return
        publish(next)
    }

    /** Removes the override of [context]; also works for a collection that no longer exists. */
    fun remove(context: PlayContext) {
        if (!mutations.canMutate) return
        val key = normalize(context).key
        val next = _entries.value.filterNot { it.context.key == key }
        if (next.size == _entries.value.size) return
        publish(next)
    }

    fun clearAll() {
        if (!mutations.canMutate || _entries.value.isEmpty()) return
        publish(emptyList())
    }

    /** Current overrides joined with the names the caller can resolve from live data. */
    fun summaries(resolveName: (PlayContext) -> String?): List<ResumeOverrideSummary> =
        _entries.value.map { ResumeOverrideSummary(it.context, resolveName(it.context), it.override) }

    private fun publish(entries: List<ResumeOverrideEntry>) {
        if (entries.isEmpty()) store.putString(KEY, null) else store.putStringList(KEY, entries.map(::encode))
        _entries.value = entries
    }

    private fun read(store: KeyValueStore = this.store): List<ResumeOverrideEntry> {
        // Duplicated records (a hand-edited or merged value) collapse to the last one.
        val byKey = LinkedHashMap<String, ResumeOverrideEntry>()
        store.getStringList(KEY).forEach { raw -> decode(raw)?.let { byKey[it.context.key] = it } }
        return byKey.values.toList()
    }

    private fun decode(raw: String): ResumeOverrideEntry? {
        val fields = raw.split(RECORD_SEPARATOR)
        if (fields.size != FIELD_COUNT) return null
        val context = PlayContext.fromKey(fields[0])?.let(::supported) ?: return null
        val resumePerTrack = when (fields[1]) {
            "" -> null
            "0" -> false
            "1" -> true
            else -> return null
        }
        val thresholdText = fields[2]
        val longTrackThresholdMs = if (thresholdText.isEmpty()) null else {
            thresholdText.toLongOrNull()?.takeIf {
                it in AppSettings.LONG_TRACK_CHOICES_MS && it.toString() == thresholdText
            } ?: return null
        }
        val override = ResumeOverride(resumePerTrack, longTrackThresholdMs)
        return if (override.isEmpty) null else ResumeOverrideEntry(context, override)
    }

    private fun encode(entry: ResumeOverrideEntry): String = listOf(
        entry.context.key,
        when (entry.override.resumePerTrack) {
            null -> ""
            false -> "0"
            true -> "1"
        },
        entry.override.longTrackThresholdMs?.toString().orEmpty(),
    ).joinToString(RECORD_SEPARATOR.toString())

    private fun normalize(context: PlayContext): PlayContext = supported(context) ?: context

    private companion object {
        /** The standard backup carries every `library.` key, so no new allowlist entry is needed. */
        const val KEY = "library.resume_overrides"

        /** PlayContext uses RS, so records use the unit separator's neighbour and never collide. */
        const val RECORD_SEPARATOR = '\u001E'
        const val FIELD_COUNT = 3
    }
}

/** Only folders (PlayContext.Playlist) and playlists can carry a resumption override. */
private fun supported(context: PlayContext): PlayContext? = when (context) {
    is PlayContext.Playlist -> context.copy(id = context.id.trim('/')).takeIf { it.id.isNotEmpty() }
    is PlayContext.UserPlaylist, is PlayContext.SmartPlaylist -> context
    else -> null
}
