package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import dev.cmdrchara.songlass.theme.liquidglass.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.playlists.*
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

/** Refresh rolling-date rules even if the library and listening history did not change. */
@Composable
internal fun rememberPlaylistTime(): Long {
    val now by produceState(Clock.System.now().toEpochMilliseconds()) {
        while (true) { delay(60_000); value = Clock.System.now().toEpochMilliseconds() }
    }
    return now
}

@Composable
fun SmartPlaylistScreen(id: String, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val playlists by state.smartPlaylists.playlists.collectAsState()
    val playlist = playlists.firstOrNull { it.id == id } ?: return
    val history by state.history.state.collectAsState()
    val favorites by state.favorites.ids.collectAsState()
    val now = rememberPlaylistTime()
    val tracks = remember(playlist, state.library, history, favorites, now) { playlist.rules.resolve(state.library, history, favorites.toSet(), now) }
    val exporter = rememberPlaylistExporter()
    ArtworkCollectionPage(
        artwork = playlist.artwork(tracks),
        kind = stringResource(Res.string.smart_title), title = playlist.name,
        subtitle = stringResource(Res.string.smart_dynamic), onSubtitleClick = null,
        tracks = tracks, durationMs = tracks.sumOf { it.durationMs }, context = PlayContext.SmartPlaylist(id),
        state = state, backLabel = backLabel, contentPadding = contentPadding,
        extra = if (tracks.isEmpty()) {{ item { FeatureHint(stringResource(Res.string.smart_empty)) } }} else null,
        headerAction = {
            Row {
                IconButton(AppIcons.Lyrics, stringResource(Res.string.smart_edit), { state.open(Route.SmartPlaylistEditor(id)) }, filled = true)
                if (exporter != null) IconButton(AppIcons.ImportFile, stringResource(Res.string.playlist_export), { exporter("${playlist.name}.m3u8", M3u.write(tracks)) }, filled = true)
                IconButton(AppIcons.Close, stringResource(Res.string.playlist_delete), { state.askDeleteSmartPlaylist(id) }, filled = true)
            }
        },
    ) { index, track ->
        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
            state.controller.playTracks(tracks, index, PlayContext.SmartPlaylist(id))
        }
    }
}

private val RulesSaver = Saver<SmartRules, String>(save = { SmartRulesCodec.encode(it) }, restore = { SmartRulesCodec.decode(it) })

@Composable
fun SmartPlaylistEditorScreen(id: String?, state: AppState, backLabel: String, contentPadding: PaddingValues) {
    val original = remember(id) { id?.let(state.smartPlaylists::get) }
    var name by rememberSaveable(id) { mutableStateOf(original?.name.orEmpty()) }
    var rules by rememberSaveable(id, stateSaver = RulesSaver) { mutableStateOf(original?.rules ?: SmartRules()) }
    var editing by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var draft by rememberSaveable(id) { mutableStateOf("") }
    var upper by rememberSaveable(id) { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf<String?>(null) }
    val history by state.history.state.collectAsState()
    val favorites by state.favorites.ids.collectAsState()
    val now = rememberPlaylistTime()
    val tracks = remember(rules, state.library, history, favorites, now) { rules.resolve(state.library, history, favorites.toSet(), now) }
    val title = stringResource(if (id == null) Res.string.smart_new else Res.string.smart_edit)
    fun edit(key: String) {
        draft = when (key) {
            "days" -> rules.addedWithinDays?.toString().orEmpty()
            "artist" -> rules.artist
            "folder" -> rules.folder
            "genre" -> rules.genre
            "year" -> rules.yearFrom?.toString().orEmpty()
            "duration" -> rules.durationFromSeconds?.toString().orEmpty()
            "excludeArtist" -> rules.excludeArtist
            "excludeFolder" -> rules.excludeFolder
            "excludeGenre" -> rules.excludeGenre
            "limit" -> rules.limit?.toString().orEmpty()
            "status" -> rules.playStatus.name
            else -> ""
        }
        upper = when (key) {
            "year" -> rules.yearTo?.toString().orEmpty()
            "duration" -> rules.durationToSeconds?.toString().orEmpty()
            else -> ""
        }
        invalid = false; editing = key
    }
    fun remove(key: String) { rules = rules.withoutCondition(key) }
    fun save() {
        invalid = name.isBlank()
        if (!invalid) {
            val saved = state.smartPlaylists.save(id, name, rules)
            state.goBack()
            if (id == null) state.open(Route.SmartPlaylistDetail(saved))
        }
    }
    PlatformBackHandler(editing != null) { editing = null; invalid = false }
    val selectedKey = editing
    val compact = LocalGlassLook.current.compactPlaylistForm
    fun apply() {
        val next = rules.applyDraft(selectedKey ?: return, draft, upper)
        invalid = next == null
        if (next != null) { rules = next.normalized(); editing = null }
    }
    Column(Modifier.fillMaxSize()
        .background(if (compact) (if (AppTheme.colors.isDark) AppTheme.colors.background else AppTheme.colors.secondaryBackground) else Color.Transparent)
        .windowInsetsPadding(WindowInsets.statusBars).padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
        if (compact) GlassPlaylistFormToolbar(
            backLabel = if (selectedKey == null) backLabel else title,
            onBack = { if (selectedKey == null) state.goBack() else { editing = null; invalid = false } },
            action = stringResource(if (selectedKey == null || selectedKey == "preview") Res.string.playlist_save else Res.string.smart_builder_apply),
            onAction = { if (selectedKey == null || selectedKey == "preview") save() else apply() },
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (selectedKey == null) {
                if (compact) {
                    item { GlassPlaylistFormTitle(title) }
                    item { GlassPlaylistFormGroup(Modifier.padding(horizontal = 20.dp)) {
                        GlassPlaylistFormField(name, { name = it; invalid = false }, stringResource(Res.string.playlist_name))
                    } }
                    item { GlassPlaylistFormSection(stringResource(Res.string.smart_builder_conditions)) }
                    item { GlassPlaylistFormGroup(Modifier.padding(horizontal = 20.dp)) {
                        GlassPlaylistFormRow(stringResource(Res.string.smart_builder_match),
                            stringResource(if (rules.match == SmartMatch.All) Res.string.smart_match_all else Res.string.smart_match_any), { menu = "match" })
                        rules.inclusionKeys().forEach { key ->
                            if (key == "favorites") SettingsToggleRow(conditionLabel(key), true, { remove(key) })
                            else GlassPlaylistFormRow(conditionLabel(key), conditionValue(key, rules), { edit(key) })
                        }
                        if (rules.inclusionKeys().size < 8) GlassPlaylistFormAdd(stringResource(Res.string.smart_builder_add)) { menu = "include" }
                    } }
                    if (rules.inclusionKeys().isEmpty()) item { FeatureHint(stringResource(Res.string.smart_builder_all_songs)) }
                    item { GlassPlaylistFormSection(stringResource(Res.string.smart_exclude)) }
                    item { GlassPlaylistFormGroup(Modifier.padding(horizontal = 20.dp)) {
                        rules.exclusionKeys().forEach { key -> GlassPlaylistFormRow(conditionLabel(key), conditionValue(key, rules), { edit(key) }) }
                        if (rules.exclusionKeys().size < 3) GlassPlaylistFormAdd(stringResource(Res.string.smart_builder_add_exclusion)) { menu = "exclude" }
                    } }
                    item { GlassPlaylistFormSection(stringResource(Res.string.smart_builder_result)) }
                    item { GlassPlaylistFormGroup(Modifier.padding(horizontal = 20.dp)) {
                        GlassPlaylistFormRow(stringResource(Res.string.smart_sort), sortLabel(rules.sort), { menu = "sort" })
                        GlassPlaylistFormRow(stringResource(Res.string.smart_builder_limit), rules.limit?.toString() ?: stringResource(Res.string.smart_builder_unlimited), { edit("limit") })
                    } }
                    if (invalid) item { FeatureHint(stringResource(Res.string.smart_builder_name_required)) }
                } else {
                    item { BackPageTitle(title, backLabel, state::goBack) }
                    item { FormField(name, { name = it; invalid = false }, stringResource(Res.string.playlist_name), Modifier.padding(horizontal = pageInset)) }
                    item { SectionTitle(stringResource(Res.string.smart_builder_conditions)) }
                    item { FilterChips(listOf(stringResource(Res.string.smart_match_all), stringResource(Res.string.smart_match_any)), rules.match.ordinal,
                        { rules = rules.copy(match = SmartMatch.entries[it]) }, Modifier.padding(vertical = 4.dp)) }
                    val included = rules.inclusionKeys()
                    if (included.isEmpty()) item { FeatureHint(stringResource(Res.string.smart_builder_all_songs)) }
                    item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                        included.forEach { key -> ConditionRow(key, rules, if (key == "favorites") null else ({ edit(key) }), { remove(key) }) }
                        if (included.size < 8) ListRow(stringResource(Res.string.smart_builder_add), leading = { AppIcon(AppIcons.Plus, null, tint = AppTheme.colors.accent) },
                            onClick = { menu = "include" })
                    } }
                    item { SectionTitle(stringResource(Res.string.smart_exclude)) }
                    item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                        rules.exclusionKeys().forEach { key -> ConditionRow(key, rules, { edit(key) }, { remove(key) }) }
                        if (rules.exclusionKeys().size < 3) ListRow(stringResource(Res.string.smart_builder_add_exclusion), leading = { AppIcon(AppIcons.Plus, null, tint = AppTheme.colors.accent) },
                            onClick = { menu = "exclude" })
                    } }
                    item { SectionTitle(stringResource(Res.string.smart_builder_result)) }
                    item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                        ListRow(stringResource(Res.string.smart_sort), subtitle = sortLabel(rules.sort),
                            trailing = { AppIcon(AppIcons.ChevronRight, null, tint = AppTheme.colors.tertiaryLabel) }, onClick = { menu = "sort" })
                        ListRow(stringResource(Res.string.smart_builder_limit), subtitle = rules.limit?.toString() ?: stringResource(Res.string.smart_builder_unlimited),
                            trailing = { AppIcon(AppIcons.ChevronRight, null, tint = AppTheme.colors.tertiaryLabel) }, onClick = { edit("limit") })
                    } }
                    if (invalid) item { FeatureHint(stringResource(Res.string.smart_builder_name_required)) }
                }
            } else if (selectedKey == "preview") {
                item {
                    if (compact) GlassPlaylistFormTitle(stringResource(Res.string.smart_builder_preview))
                    else BackPageTitle(stringResource(Res.string.smart_builder_preview), title, { editing = null })
                }
                if (tracks.isEmpty()) item { FeatureHint(stringResource(Res.string.smart_empty)) }
                items(tracks.size, key = { tracks[it].id }) { index ->
                    val track = tracks[index]
                    ListRow(track.title, subtitle = artistName(track.artist), leading = { AlbumArt(rememberTrackArtwork(track), Modifier.size(44.dp), cornerRadius = 6.dp) })
                }
            } else {
                item {
                    if (compact) GlassPlaylistFormTitle(conditionLabel(selectedKey))
                    else BackPageTitle(conditionLabel(selectedKey), title, { editing = null; invalid = false })
                }
                if (selectedKey == "status") {
                    item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                        listOf(SmartPlayStatus.Any, SmartPlayStatus.Never, SmartPlayStatus.Played).forEach { status ->
                            SettingsChoiceRow(statusLabel(status), draft == status.name, { draft = status.name })
                        }
                    } }
                } else {
                    if (compact) item { GlassPlaylistFormGroup(Modifier.padding(horizontal = 20.dp)) {
                        GlassPlaylistFormField(draft, { draft = it; invalid = false }, inputLabel(selectedKey, false), numeric = selectedKey in listOf("year", "duration", "days", "limit"))
                        if (selectedKey == "year" || selectedKey == "duration") GlassPlaylistFormField(upper, { upper = it; invalid = false }, inputLabel(selectedKey, true), numeric = true)
                    } } else {
                        item { FormField(draft, { draft = it; invalid = false }, inputLabel(selectedKey, false), Modifier.padding(horizontal = pageInset)) }
                        if (selectedKey == "year" || selectedKey == "duration") item { FormField(upper, { upper = it; invalid = false }, inputLabel(selectedKey, true), Modifier.padding(horizontal = pageInset)) }
                    }
                    if (selectedKey == "year" || selectedKey == "duration") item { FeatureHint(stringResource(Res.string.smart_metadata_hint)) }
                }
                if (invalid) item { FeatureHint(stringResource(Res.string.smart_range_invalid)) }
                if (compact && selectedKey in (rules.inclusionKeys() + rules.exclusionKeys())) item {
                    GlassPlaylistFormGroup(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                        GlassPlaylistFormRemove(stringResource(Res.string.smart_builder_remove, conditionLabel(selectedKey))) {
                            remove(selectedKey); editing = null; invalid = false
                        }
                    }
                }
            }
        }
        if (compact) {
            if (selectedKey == null) GlassPlaylistFormRow(stringResource(Res.string.smart_builder_preview),
                pluralStringResource(Res.plurals.songs_count, tracks.size, tracks.size), { editing = "preview" },
                Modifier.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite })
        } else if (selectedKey == null || selectedKey == "preview") {
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                ListRow(pluralStringResource(Res.plurals.songs_count, tracks.size, tracks.size),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    subtitle = stringResource(Res.string.smart_builder_preview),
                    trailing = { ActionButton(stringResource(Res.string.playlist_save), ::save) },
                    onClick = { editing = "preview" })
            }
        } else {
            ActionButton(stringResource(Res.string.smart_builder_apply), ::apply, Modifier.fillMaxWidth().padding(16.dp))
        }
    }
    val choices = when (menu) {
        "include" -> listOf("favorites", "status", "days", "artist", "folder", "genre", "year", "duration").filterNot { it in rules.inclusionKeys() }
        "exclude" -> listOf("excludeArtist", "excludeFolder", "excludeGenre").filterNot { it in rules.exclusionKeys() }
        else -> emptyList()
    }
    ActionSheet(visible = menu != null,
        title = stringResource(if (menu == "exclude") Res.string.smart_builder_add_exclusion else if (menu == "sort") Res.string.smart_sort else if (menu == "match") Res.string.smart_builder_match else Res.string.smart_builder_add),
        cancelLabel = stringResource(Res.string.action_cancel), onDismiss = { menu = null },
        actions = if (menu == "sort") SmartSort.entries.map { sort -> SheetAction(sortLabel(sort), AppIcons.DragHandle) { rules = rules.copy(sort = sort); menu = null } }
        else if (menu == "match") SmartMatch.entries.map { match -> SheetAction(stringResource(if (match == SmartMatch.All) Res.string.smart_match_all else Res.string.smart_match_any),
            if (match == rules.match) AppIcons.Checkmark else null) { rules = rules.copy(match = match); menu = null } }
        else choices.map { key -> SheetAction(conditionLabel(key), AppIcons.Plus) {
            menu = null
            if (key == "favorites") rules = rules.copy(favoritesOnly = true) else edit(key)
        } })
}

/** Display state derives from the validated core rules; filtering stays in core. */
private fun SmartRules.inclusionKeys(): List<String> = buildList {
    if (favoritesOnly) add("favorites")
    if (playStatus != SmartPlayStatus.Any) add("status")
    if (addedWithinDays != null) add("days")
    if (artist.isNotBlank()) add("artist")
    if (folder.isNotBlank()) add("folder")
    if (genre.isNotBlank()) add("genre")
    if (yearFrom != null || yearTo != null) add("year")
    if (durationFromSeconds != null || durationToSeconds != null) add("duration")
}
private fun SmartRules.exclusionKeys() = buildList {
    if (excludeArtist.isNotBlank()) add("excludeArtist")
    if (excludeFolder.isNotBlank()) add("excludeFolder")
    if (excludeGenre.isNotBlank()) add("excludeGenre")
}
private fun SmartRules.withoutCondition(key: String) = when (key) {
    "favorites" -> copy(favoritesOnly = false)
    "status" -> copy(playStatus = SmartPlayStatus.Any)
    "days" -> copy(addedWithinDays = null)
    "artist" -> copy(artist = "")
    "folder" -> copy(folder = "")
    "genre" -> copy(genre = "")
    "year" -> copy(yearFrom = null, yearTo = null)
    "duration" -> copy(durationFromSeconds = null, durationToSeconds = null)
    "excludeArtist" -> copy(excludeArtist = "")
    "excludeFolder" -> copy(excludeFolder = "")
    "excludeGenre" -> copy(excludeGenre = "")
    else -> this
}
private fun SmartRules.applyDraft(key: String, value: String, upper: String): SmartRules? {
    val input = SmartRuleInput(addedWithinDays?.toString().orEmpty(), limit?.toString().orEmpty(),
        yearFrom?.toString().orEmpty(), yearTo?.toString().orEmpty(),
        durationFromSeconds?.toString().orEmpty(), durationToSeconds?.toString().orEmpty())
    return when (key) {
        "days" -> input.copy(days = value).applyTo(this)
        "limit" -> input.copy(limit = value).applyTo(this)
        "year" -> input.copy(yearFrom = value, yearTo = upper).applyTo(this)
        "duration" -> input.copy(durationFromSeconds = value, durationToSeconds = upper).applyTo(this)
        "artist" -> copy(artist = value)
        "folder" -> copy(folder = value)
        "genre" -> copy(genre = value)
        "excludeArtist" -> copy(excludeArtist = value)
        "excludeFolder" -> copy(excludeFolder = value)
        "excludeGenre" -> copy(excludeGenre = value)
        "status" -> copy(playStatus = SmartPlayStatus.entries.first { it.name == value })
        else -> this
    }
}

@Composable
private fun ConditionRow(key: String, rules: SmartRules, onEdit: (() -> Unit)?, onRemove: () -> Unit) {
    val label = conditionLabel(key)
    ListRow(label, subtitle = conditionValue(key, rules), onClick = onEdit,
        trailing = { IconButton(AppIcons.Close, stringResource(Res.string.smart_builder_remove, label), onRemove) })
}

@Composable
private fun conditionLabel(key: String): String = stringResource(when (key) {
    "favorites" -> Res.string.smart_favorites
    "status" -> Res.string.smart_play_status
    "days" -> Res.string.smart_builder_recent
    "artist", "excludeArtist" -> Res.string.homecfg_kind_artist
    "folder", "excludeFolder" -> Res.string.homecfg_kind_folder
    "genre", "excludeGenre" -> Res.string.smart_builder_genre
    "year" -> Res.string.smart_year
    "duration" -> Res.string.smart_duration
    else -> Res.string.smart_builder_limit
})

@Composable
private fun conditionValue(key: String, rules: SmartRules): String = when (key) {
    "favorites" -> stringResource(Res.string.smart_favorites)
    "status" -> statusLabel(rules.playStatus)
    "days" -> stringResource(Res.string.smart_builder_days_value, rules.addedWithinDays ?: 0)
    "artist" -> rules.artist
    "folder" -> rules.folder
    "genre" -> rules.genre
    "excludeArtist" -> rules.excludeArtist
    "excludeFolder" -> rules.excludeFolder
    "excludeGenre" -> rules.excludeGenre
    "year" -> rangeLabel(rules.yearFrom, rules.yearTo)
    "duration" -> rangeLabel(rules.durationFromSeconds, rules.durationToSeconds) + " " + stringResource(Res.string.smart_builder_seconds)
    else -> ""
}
@Composable
private fun rangeLabel(from: Int?, to: Int?): String = when {
    from != null && to != null -> "$from–$to"
    from != null -> stringResource(Res.string.smart_builder_from, from)
    to != null -> stringResource(Res.string.smart_builder_to, to)
    else -> ""
}
@Composable
private fun inputLabel(key: String, upper: Boolean): String = stringResource(when (key) {
    "year" -> if (upper) Res.string.smart_year_to else Res.string.smart_year_from
    "duration" -> if (upper) Res.string.smart_duration_to else Res.string.smart_duration_from
    "days" -> Res.string.smart_days
    "limit" -> Res.string.smart_limit
    "artist", "excludeArtist" -> Res.string.smart_exclude_artist
    "folder", "excludeFolder" -> Res.string.smart_exclude_folder
    else -> Res.string.smart_exclude_genre
})
@Composable
private fun sortLabel(sort: SmartSort): String = stringResource(when (sort) {
    SmartSort.Title -> Res.string.smart_sort_title
    SmartSort.Newest -> Res.string.smart_sort_newest
    SmartSort.MostPlayed -> Res.string.smart_sort_most
    SmartSort.LeastPlayed -> Res.string.smart_sort_least
})
@Composable
private fun statusLabel(status: SmartPlayStatus): String = stringResource(when (status) {
    SmartPlayStatus.Any -> Res.string.smart_any
    SmartPlayStatus.Never -> Res.string.smart_never
    SmartPlayStatus.Played -> Res.string.smart_played
})

@Composable
internal fun FeatureHint(text: String) = AppText(text, AppTheme.typography.footnote,
    color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))

/** Hosted above the scaffold so transport/navigation cannot obscure the confirm action. */
@Composable
fun SmartPlaylistDeleteSheet(state: AppState) {
    val playlists by state.smartPlaylists.playlists.collectAsState()
    var last by remember { mutableStateOf<SmartPlaylist?>(null) }
    playlists.firstOrNull { it.id == state.deletingSmartPlaylistId }?.let { last = it }
    val playlist = last
    ActionSheet(visible = state.deletingSmartPlaylistId != null && playlist != null,
        title = stringResource(Res.string.smart_delete_question), subtitle = playlist?.name,
        cancelLabel = stringResource(Res.string.action_cancel), onDismiss = state::closeDeleteSmartPlaylist,
        actions = if (playlist == null) emptyList() else listOf(SheetAction(stringResource(Res.string.playlist_delete), AppIcons.Close) {
            state.closeDeleteSmartPlaylist()
            state.smartPlaylists.delete(playlist.id)
            if ((state.currentRoute as? Route.SmartPlaylistDetail)?.id == playlist.id) state.goBack()
        }))
}
