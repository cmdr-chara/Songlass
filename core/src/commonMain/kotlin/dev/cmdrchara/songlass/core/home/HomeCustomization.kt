package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import dev.cmdrchara.songlass.core.storage.getStringList
import dev.cmdrchara.songlass.core.storage.putStringList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A block of the Home. The declaration order is the default order, the one the Home always had. */
enum class HomeSection(val id: String) {
    Statistics("statistics"),
    OnThisDay("on_this_day"),
    Shortcuts("shortcuts"),
    ForYou("for_you"),
    RecentlyAdded("recently_added"),
    ChangeOfPace("change_of_pace"),
    MostPlayed("most_played"),
    Playlists("playlists"),
    ;

    companion object {
        val DEFAULT_ORDER: List<HomeSection> = entries.toList()

        fun fromId(id: String): HomeSection? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What the user chose for the Home. Always normalized: [order] holds every section exactly once,
 * [hidden] only known sections and [shortcuts] at most [HomeShortcuts.MAX] distinct identities.
 * [shortcuts] are saved choices, not resolved ones: those whose music is not in the visible
 * library are skipped by the feed, never removed here.
 */
data class HomeCustomizationSnapshot(
    val order: List<HomeSection> = HomeSection.DEFAULT_ORDER,
    val hidden: Set<HomeSection> = emptySet(),
    val shortcuts: List<PlayContext> = emptyList(),
) {
    /** Sections to show, in order. */
    val visibleSections: List<HomeSection> get() = order.filter { it !in hidden }

    val isDefault: Boolean get() = order == HomeSection.DEFAULT_ORDER && hidden.isEmpty() && shortcuts.isEmpty()

    fun isVisible(section: HomeSection): Boolean = section !in hidden

    companion object {
        /**
         * Builds a valid snapshot from raw persisted values: unknown and duplicate sections are
         * dropped, sections missing from [order] (e.g. added by a newer version) go to the end in
         * their default order, and shortcuts are de-duplicated and capped.
         */
        fun normalized(
            order: List<HomeSection>,
            hidden: Collection<HomeSection>,
            shortcuts: List<PlayContext>,
        ): HomeCustomizationSnapshot {
            val ordered = order.distinct()
            return HomeCustomizationSnapshot(
                order = ordered + HomeSection.DEFAULT_ORDER.filter { it !in ordered },
                hidden = hidden.toSet(),
                shortcuts = shortcuts.distinct().take(HomeShortcuts.MAX),
            )
        }
    }
}

/**
 * Which Home sections are shown and in what order, plus up to [HomeShortcuts.MAX] chosen
 * shortcuts. Kept in [store] under `home.*` keys; default values are stored as absent keys, so
 * someone who never customizes keeps exactly the original Home. [reload] re-reads the store, for
 * after a backup restore.
 */
class HomeCustomization(
    private val store: KeyValueStore = InMemoryKeyValueStore(),
    private val mutations: RestoreMutationGate = RestoreMutationGate(),
) {
    private val _state = MutableStateFlow(load())

    val state: StateFlow<HomeCustomizationSnapshot> = _state.asStateFlow()

    fun reload() = prepareReload().invoke()

    fun prepareReload(store: KeyValueStore = this.store): () -> Unit {
        val loaded = load(store)
        return { _state.value = loaded }
    }

    /** Moves [section] by [offset] places (negative = earlier), staying inside the list. */
    fun moveSection(section: HomeSection, offset: Int) = update { current ->
        val index = current.order.indexOf(section)
        val target = (index + offset).coerceIn(0, current.order.lastIndex)
        if (index < 0 || target == index) return@update current
        val order = current.order.toMutableList()
        order.add(target, order.removeAt(index))
        current.copy(order = order)
    }

    fun setSectionVisible(section: HomeSection, visible: Boolean) = update { current ->
        current.copy(hidden = if (visible) current.hidden - section else current.hidden + section)
    }

    /** Adds [context] as the last shortcut. False when it is already there or the list is full. */
    fun addShortcut(context: PlayContext): Boolean {
        if (!mutations.canMutate) return false
        val current = _state.value.shortcuts
        if (context in current || current.size >= HomeShortcuts.MAX) return false
        update { it.copy(shortcuts = it.shortcuts + context) }
        return true
    }

    fun removeShortcut(context: PlayContext) = update { it.copy(shortcuts = it.shortcuts - context) }

    fun moveShortcut(context: PlayContext, offset: Int) = update { current ->
        val index = current.shortcuts.indexOf(context)
        val target = (index + offset).coerceIn(0, current.shortcuts.lastIndex.coerceAtLeast(0))
        if (index < 0 || target == index) return@update current
        val shortcuts = current.shortcuts.toMutableList()
        shortcuts.add(target, shortcuts.removeAt(index))
        current.copy(shortcuts = shortcuts)
    }

    /** Default order with every section visible; the chosen shortcuts stay. */
    fun resetSections() = update { it.copy(order = HomeSection.DEFAULT_ORDER, hidden = emptySet()) }

    /** Back to automatic shortcuts only. */
    fun resetShortcuts() = update { it.copy(shortcuts = emptyList()) }

    /** Restores every default. */
    fun reset() = update { HomeCustomizationSnapshot() }

    private fun update(change: (HomeCustomizationSnapshot) -> HomeCustomizationSnapshot) {
        if (!mutations.canMutate) return
        val current = _state.value
        val updated = change(current).let { HomeCustomizationSnapshot.normalized(it.order, it.hidden, it.shortcuts) }
        if (updated == current) return
        save(updated)
        _state.value = updated
    }

    private fun load(store: KeyValueStore = this.store): HomeCustomizationSnapshot = HomeCustomizationSnapshot.normalized(
        order = store.getStringList(SECTIONS_KEY).mapNotNull(HomeSection::fromId),
        hidden = store.getStringList(HIDDEN_KEY).mapNotNull(HomeSection::fromId),
        shortcuts = store.getStringList(SHORTCUTS_KEY).mapNotNull(PlayContext::fromKey),
    )

    private fun save(snapshot: HomeCustomizationSnapshot) {
        // Defaults are removed instead of written, so the store only holds real choices.
        if (snapshot.order == HomeSection.DEFAULT_ORDER) store.putString(SECTIONS_KEY, null)
        else store.putStringList(SECTIONS_KEY, snapshot.order.map { it.id })
        if (snapshot.hidden.isEmpty()) store.putString(HIDDEN_KEY, null)
        else store.putStringList(HIDDEN_KEY, HomeSection.DEFAULT_ORDER.filter { it in snapshot.hidden }.map { it.id })
        if (snapshot.shortcuts.isEmpty()) store.putString(SHORTCUTS_KEY, null)
        else store.putStringList(SHORTCUTS_KEY, snapshot.shortcuts.map { it.key })
    }

    companion object {
        const val KEY_PREFIX = "home."
        private const val SECTIONS_KEY = "home.sections"
        private const val HIDDEN_KEY = "home.hidden_sections"
        private const val SHORTCUTS_KEY = "home.shortcuts"
    }
}
