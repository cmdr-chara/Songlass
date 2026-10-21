package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.home.*
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import org.jetbrains.compose.resources.stringResource

/** Overview, section ordering and a focused picker each have their own reading hierarchy. */
@Composable
fun HomeCustomizationScreen(state: AppState, backLabel: String, onBack: () -> Unit, contentPadding: PaddingValues, searchFocus: SettingsFocusRequest? = null) {
    val customization = state.homeCustomization
    val library = state.library
    val layout by customization.state.collectAsState()
    val personal by state.userPlaylists.playlists.collectAsState()
    val smart by state.smartPlaylists.playlists.collectAsState()
    val history by state.history.state.collectAsState()
    val favorites by state.favorites.ids.collectAsState()
    val now = rememberPlaylistTime()
    val saved = remember(library, personal, smart, history, favorites, now) {
        HomeShortcuts.savedPlaylists(library, personal, smart, history, favorites.toSet(), now)
    }
    var pane by rememberSaveable { mutableStateOf("overview") }
    val overviewListState = rememberLazyListState()
    var query by rememberSaveable { mutableStateOf("") }
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    var menuContext by remember { mutableStateOf<PlayContext?>(null) }
    var menuVisible by remember { mutableStateOf(false) }
    var kindsVisible by remember { mutableStateOf(false) }
    val kindLabels = listOf(stringResource(Res.string.search_all), stringResource(Res.string.homecfg_kind_user_playlist),
        stringResource(Res.string.homecfg_kind_smart_playlist), stringResource(Res.string.library_albums),
        stringResource(Res.string.library_artists), stringResource(Res.string.library_folders), stringResource(Res.string.library_metadata))
    val chosen = layout.shortcuts
    val full = chosen.size >= HomeShortcuts.MAX
    val options = remember(library, saved) { HomeShortcuts.options(library, saved) }
    val kinds = listOf(null, ShortcutKind.UserPlaylist, ShortcutKind.SmartPlaylist, ShortcutKind.Album, ShortcutKind.Artist, ShortcutKind.Folder, ShortcutKind.Metadata)
    val matches = remember(options, query, kindIndex) { HomeShortcuts.filter(options, query, kinds[kindIndex]) }
    val favoritesLabel = stringResource(Res.string.homecfg_kind_favorites)
    val showFavorites = kindIndex == 0 && favoritesLabel.contains(query.trim(), ignoreCase = true)
    PlatformBackHandler(pane != "overview") { pane = "overview" }
    fun toggle(context: PlayContext) {
        if (context in chosen) customization.removeShortcut(context) else customization.addShortcut(context)
    }
    fun resolveShortcut(context: PlayContext) = HomeShortcuts.resolve(library, context, emptyList(), saved)
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).padding(contentPadding).consumeWindowInsets(contentPadding).imePadding()) {
        if (pane == "picker") {
            BackPageTitle(stringResource(Res.string.homecfg_add_title), stringResource(Res.string.homecfg_title), { pane = "overview" })
            SearchField(query, { query = it }, stringResource(Res.string.homecfg_search_placeholder), stringResource(Res.string.search_clear),
                Modifier.padding(horizontal = pageInset, vertical = 8.dp))
            if (LocalGlassLook.current.homeShortcutKindMenu) {
                SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
                    ListRow(kindLabels[kindIndex], subtitle = stringResource(Res.string.homecfg_builder_type),
                        trailing = { AppIcon(AppIcons.ChevronDown, null, tint = AppTheme.colors.tertiaryLabel) },
                        onClick = { kindsVisible = true })
                }
            } else FilterChips(kindLabels, kindIndex, { kindIndex = it }, Modifier.padding(vertical = 4.dp))
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
                if (showFavorites) item(key = "favorites") {
                    PickerRow(ShortcutOption(PlayContext.Favorites, ShortcutKind.Favorites, null), null, PlayContext.Favorites in chosen, full) { toggle(PlayContext.Favorites) }
                }
                if (matches.isEmpty() && !showFavorites) item { HomeHint(stringResource(Res.string.homecfg_no_matches)) }
                items(matches, key = { it.context.key }) { option ->
                    PickerRow(option, resolveShortcut(option.context), option.context in chosen, full) { toggle(option.context) }
                }
            }
            SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                ListRow(stringResource(Res.string.homecfg_builder_count, chosen.size, HomeShortcuts.MAX),
                    subtitle = if (full) stringResource(Res.string.homecfg_builder_full) else null,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    trailing = { ActionButton(stringResource(Res.string.homecfg_builder_done), { pane = "overview" }) })
            }
        } else if (pane == "sections") {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(bottom = 12.dp)) {
                item { BackPageTitle(stringResource(Res.string.homecfg_show_title), stringResource(Res.string.homecfg_title), { pane = "overview" }) }
                item { HomeHint(stringResource(Res.string.homecfg_order_hint)) }
                item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                    layout.order.forEachIndexed { index, section ->
                        val name = sectionName(section)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SettingsToggleRow(name, layout.isVisible(section), { customization.setSectionVisible(section, it) }, Modifier.weight(1f),
                                subtitle = stringResource(Res.string.homecfg_position, index + 1, layout.order.size))
                            if (index > 0) IconButton(ChevronUp, stringResource(Res.string.homecfg_move_up, name), { customization.moveSection(section, -1) }, Modifier.size(44.dp))
                            else Spacer(Modifier.size(44.dp))
                            if (index < layout.order.lastIndex) IconButton(AppIcons.ChevronDown, stringResource(Res.string.homecfg_move_down, name), { customization.moveSection(section, 1) }, Modifier.size(44.dp))
                            else Spacer(Modifier.size(44.dp))
                        }
                    }
                } }
            }
        } else {
            val searchReveal = SettingsSearchScroll(searchFocus, overviewListState, if (searchFocus?.target == SettingsTarget.HomeCustomization) 0 else null)
            LazyColumn(Modifier.weight(1f).fillMaxWidth().then(searchReveal), state = overviewListState, contentPadding = PaddingValues(bottom = 12.dp)) {
                item {
                    SettingsSearchAnchor(SettingsTarget.HomeCustomization, searchFocus) {
                        BackPageTitle(stringResource(Res.string.homecfg_title),
                            if (searchFocus == null) backLabel else stringResource(Res.string.settings_search_results), onBack)
                    }
                }
                item { SectionTitle(stringResource(Res.string.homecfg_shortcuts_title)) }
                if (chosen.isEmpty()) item { HomeHint(stringResource(Res.string.homecfg_builder_empty)) }
                item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                    chosen.forEach { context ->
                        val resolved = resolveShortcut(context)
                        val title = chosenTitle(context, resolved)
                        val subtitle = if (resolved == null && context != PlayContext.Favorites) stringResource(Res.string.homecfg_shortcut_unavailable)
                            else kindLabel(kindOf(context))
                        val openMenu = { menuContext = context; menuVisible = true }
                        ListRow(title, subtitle = subtitle, leading = { ShortcutLeading(context, resolved) },
                            trailing = { IconButton(AppIcons.More, stringResource(Res.string.homecfg_builder_manage, title), openMenu) }, onClick = openMenu)
                    }
                    if (!full) ListRow(stringResource(Res.string.homecfg_add_title), leading = { AppIcon(AppIcons.Plus, null, tint = AppTheme.colors.accent) }, onClick = { pane = "picker" })
                } }
                item { HomeHint(stringResource(Res.string.homecfg_builder_count, chosen.size, HomeShortcuts.MAX)) }
                item { SectionTitle(stringResource(Res.string.homecfg_builder_layout)) }
                item { SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                    ListRow(stringResource(Res.string.homecfg_show_title), subtitle = stringResource(Res.string.homecfg_builder_sections, layout.visibleSections.size, layout.order.size),
                        trailing = { AppIcon(AppIcons.ChevronRight, null, tint = AppTheme.colors.tertiaryLabel) }, onClick = { pane = "sections" })
                } }
                if (!layout.isDefault) item {
                    ActionButton(stringResource(Res.string.homecfg_reset), customization::reset, Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 20.dp))
                }
            }
        }
    }
    ActionSheet(visible = kindsVisible, title = stringResource(Res.string.homecfg_builder_type),
        actions = kindLabels.mapIndexed { index, label -> SheetAction(label, if (kindIndex == index) AppIcons.Checkmark else AppIcons.MusicNote) {
            kindIndex = index; kindsVisible = false
        } }, cancelLabel = stringResource(Res.string.action_cancel), onDismiss = { kindsVisible = false })
    val context = menuContext
    val resolved = context?.let(::resolveShortcut)
    val position = chosen.indexOf(context)
    val actions = buildList {
        if (context != null) {
            if (position > 0) add(SheetAction(stringResource(Res.string.homecfg_builder_earlier), ChevronUp) { customization.moveShortcut(context, -1); menuVisible = false })
            if (position >= 0 && position < chosen.lastIndex) add(SheetAction(stringResource(Res.string.homecfg_builder_later), AppIcons.ChevronDown) { customization.moveShortcut(context, 1); menuVisible = false })
            add(SheetAction(stringResource(Res.string.homecfg_builder_remove), AppIcons.Close) { customization.removeShortcut(context); menuVisible = false })
        }
    }
    ActionSheet(visible = menuVisible, title = context?.let { chosenTitle(it, resolved) }.orEmpty(), actions = actions,
        cancelLabel = stringResource(Res.string.action_cancel), onDismiss = { menuVisible = false })
}

@Composable
private fun PickerRow(option: ShortcutOption, item: HomeItem?, chosen: Boolean, full: Boolean, onToggle: () -> Unit) {
    val title = if (option.kind == ShortcutKind.Favorites) stringResource(Res.string.homecfg_kind_favorites)
        else if (option.kind == ShortcutKind.Artist) artistName(option.title) else option.title ?: albumTitle(null)
    val label = if (chosen) stringResource(Res.string.homecfg_shortcut_remove, title) else stringResource(Res.string.homecfg_builder_add_named, title)
    val kindTitle = (option.context as? PlayContext.Facet)?.let { facetKindLabel(it.kind) } ?: kindLabel(option.kind)
    ListRow(title, subtitle = kindTitle + (option.subtitle?.let { " · $it" } ?: ""),
        modifier = Modifier.semantics { stateDescription = label }, leading = { ShortcutLeading(option.context, item) },
        trailing = if (chosen || !full) {{ IconButton(if (chosen) AppIcons.Checkmark else AppIcons.Plus, label, onToggle, selected = chosen) }} else null,
        onClick = if (chosen || !full) onToggle else null)
}
@Composable
private fun HomeHint(text: String) = AppText(text, AppTheme.typography.footnote, color = AppTheme.colors.secondaryLabel,
    modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp))

@Composable
private fun ShortcutLeading(context: PlayContext, item: HomeItem?) {
    when {
        context == PlayContext.Favorites -> AppIcon(AppIcons.Star, null, size = 28.dp, tint = AppTheme.colors.accent)
        item == null -> AppIcon(AppIcons.MusicNote, null, size = 28.dp, tint = AppTheme.colors.tertiaryLabel)
        item is HomeItem.ArtistItem -> ArtistAvatar(artworkOf(item), Modifier.size(44.dp))
        else -> AlbumArt(artworkOf(item), Modifier.size(44.dp), cornerRadius = 6.dp)
    }
}
private fun artworkOf(item: HomeItem): Artwork = when (item) {
    is HomeItem.AlbumItem -> item.album.artwork()
    is HomeItem.PlaylistItem -> item.playlist.artwork()
    is HomeItem.ArtistItem -> item.artist.albums.firstOrNull()?.artwork() ?: item.artist.tracks.first().artwork()
    is HomeItem.FacetItem -> item.facet.tracks.first().artwork()
    is HomeItem.TrackItem -> item.track.artwork()
    is HomeItem.FavoritesItem -> item.tracks.first().artwork()
    is HomeItem.UserPlaylistItem -> item.playlist.artwork(item.tracks)
    is HomeItem.SmartPlaylistItem -> item.playlist.artwork(item.tracks)
}
private fun kindOf(context: PlayContext): ShortcutKind = when (context) {
    is PlayContext.Album -> ShortcutKind.Album
    is PlayContext.Artist -> ShortcutKind.Artist
    is PlayContext.Facet -> ShortcutKind.Metadata
    is PlayContext.Playlist -> ShortcutKind.Folder
    PlayContext.Favorites -> ShortcutKind.Favorites
    is PlayContext.UserPlaylist -> ShortcutKind.UserPlaylist
    is PlayContext.SmartPlaylist -> ShortcutKind.SmartPlaylist
}
@Composable
private fun kindLabel(kind: ShortcutKind): String = stringResource(when (kind) {
    ShortcutKind.Favorites -> Res.string.homecfg_kind_favorites
    ShortcutKind.Album -> Res.string.homecfg_kind_album
    ShortcutKind.Artist -> Res.string.homecfg_kind_artist
    ShortcutKind.Folder -> Res.string.homecfg_kind_folder
    ShortcutKind.UserPlaylist -> Res.string.homecfg_builder_personal_kind
    ShortcutKind.SmartPlaylist -> Res.string.homecfg_builder_smart_kind
    ShortcutKind.Metadata -> Res.string.library_metadata
})
@Composable
private fun chosenTitle(context: PlayContext, item: HomeItem?): String = when (context) {
    PlayContext.Favorites -> stringResource(Res.string.homecfg_kind_favorites)
    is PlayContext.Album -> context.title.ifEmpty { null } ?: albumTitle(null)
    is PlayContext.Artist -> artistName(context.name)
    is PlayContext.Facet -> (item as? HomeItem.FacetItem)?.facet?.value ?: context.value
    is PlayContext.Playlist -> (item as? HomeItem.PlaylistItem)?.playlist?.name ?: context.id.trim('/').substringAfterLast('/')
    is PlayContext.UserPlaylist -> (item as? HomeItem.UserPlaylistItem)?.playlist?.name ?: stringResource(Res.string.homecfg_kind_user_playlist)
    is PlayContext.SmartPlaylist -> (item as? HomeItem.SmartPlaylistItem)?.playlist?.name ?: stringResource(Res.string.homecfg_kind_smart_playlist)
}
@Composable
private fun sectionName(section: HomeSection): String = stringResource(when (section) {
    HomeSection.Statistics -> Res.string.homecfg_section_statistics
    HomeSection.OnThisDay -> Res.string.homecfg_section_on_this_day
    HomeSection.Shortcuts -> Res.string.homecfg_section_shortcuts
    HomeSection.ForYou -> Res.string.homecfg_section_for_you
    HomeSection.RecentlyAdded -> Res.string.homecfg_section_recently_added
    HomeSection.ChangeOfPace -> Res.string.homecfg_section_change_of_pace
    HomeSection.MostPlayed -> Res.string.homecfg_section_most_played
    HomeSection.Playlists -> Res.string.homecfg_section_playlists
})

private val ChevronUp: ImageVector by lazy {
    ImageVector.Builder(
        name = "ChevronUp", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    ).path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(5f, 15f)
        lineTo(12f, 9f)
        lineTo(19f, 15f)
    }.build()
}
