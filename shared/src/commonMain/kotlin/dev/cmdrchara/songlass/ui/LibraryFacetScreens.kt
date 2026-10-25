package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.AlphabetSection
import dev.cmdrchara.songlass.core.library.AlphabeticalIndex
import dev.cmdrchara.songlass.core.library.LibraryFacet
import dev.cmdrchara.songlass.core.library.LibraryFacetKind
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun facetKindLabel(kind: LibraryFacetKind): String = stringResource(when (kind) {
    LibraryFacetKind.AlbumArtist -> Res.string.library_album_artists
    LibraryFacetKind.Year -> Res.string.library_years
    LibraryFacetKind.Composer -> Res.string.library_composers
})

@Composable
internal fun LibraryFacetCategories(state: AppState) {
    SettingsGroup(Modifier.padding(horizontal = pageInset)) {
        LibraryFacetKind.entries.forEach { kind ->
            ListRow(facetKindLabel(kind),
                leading = { AppIcon(if (kind == LibraryFacetKind.Year) AppIcons.Clock else AppIcons.Person,
                    null, size = 24.dp, tint = AppTheme.colors.accent) },
                trailing = {
                    // Miuix hosts this slot in a Box; keep the count and disclosure together
                    // in an explicit row so they cannot be placed on top of one another.
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppText(state.library.facets(kind).size.toString(), AppTheme.typography.body, color = AppTheme.colors.secondaryLabel)
                        AppIcon(AppIcons.ChevronRight, null, size = 14.dp, tint = AppTheme.colors.tertiaryLabel)
                    }
                }, onClick = { state.open(Route.FacetList(kind)) })
        }
    }
}

@Composable
fun LibraryFacetListScreen(kind: LibraryFacetKind, state: AppState, backLabel: String, contentPadding: PaddingValues) {
    val facets = state.library.facets(kind)
    val listState = rememberLazyListState()
    val scrollScope = rememberCoroutineScope()
    val sections = remember(facets) { if (kind == LibraryFacetKind.Year || facets.size < ALPHABET_MIN_ITEMS) emptyList()
        else AlphabeticalIndex.build(facets.map { it.value }).takeIf { it.size > 1 }.orEmpty() }
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), state = listState, contentPadding = contentPadding) {
        item { BackPageTitle(facetKindLabel(kind), backLabel, state::goBack) }
        if (sections.isNotEmpty()) item { LibraryAlphabetButton(state, sections, listState, scrollScope) { index -> index + 2 } }
        if (facets.isEmpty()) item {
            AppText(stringResource(Res.string.library_metadata_empty), AppTheme.typography.body, modifier = Modifier.padding(24.dp))
        }
        items(facets, key = { it.context.key }) { facet ->
            LibraryFacetRow(facet) { state.open(Route.FacetDetail(facet.context)) }
        }
    }
}

@Composable
internal fun LibraryFacetRow(facet: LibraryFacet, onClick: () -> Unit) {
    ListRow(facet.value, subtitle = facetKindLabel(facet.kind) + " · " +
        pluralStringResource(Res.plurals.songs_count, facet.tracks.size, facet.tracks.size),
        leading = { AlbumArt(facet.tracks.first().artwork(), Modifier.size(48.dp), cornerRadius = 8.dp) },
        onClick = onClick)
}

@Composable
fun LibraryFacetScreen(context: PlayContext.Facet, state: AppState, currentTrackId: String?, backLabel: String, contentPadding: PaddingValues) {
    val facet = state.library.facet(context)
    if (facet == null) {
        LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
            item { BackPageTitle(facetKindLabel(context.kind), backLabel, state::goBack) }
            item { AppText(stringResource(Res.string.library_metadata_empty), AppTheme.typography.body, modifier = Modifier.padding(24.dp)) }
        }
        return
    }
    ArtworkCollectionPage(
        artwork = facet.tracks.first().artwork(), kind = facetKindLabel(facet.kind), title = facet.value,
        subtitle = null, onSubtitleClick = null, tracks = facet.tracks,
        durationMs = facet.tracks.sumOf { it.durationMs }, context = facet.context, state = state,
        backLabel = backLabel, contentPadding = contentPadding,
    ) { index, track ->
        TrackRow(track, highlighted = track.id == currentTrackId, onMore = { state.openMenu(track) }) {
            state.tapTrack(facet.tracks, index, facet.context)
        }
    }
}

internal const val ALPHABET_MIN_ITEMS = 50

@Composable
internal fun LibraryAlphabetButton(state: AppState, sections: List<AlphabetSection>, listState: LazyListState, scope: CoroutineScope,
    itemIndex: (Int) -> Int,
) {
    val reduceMotion = AppTheme.accessibility.reduceMotion
    ActionButton(stringResource(Res.string.library_jump_letter), {
        state.openAlphabetJump(sections) { index ->
            scope.launch {
                if (reduceMotion) listState.scrollToItem(itemIndex(index)) else listState.animateScrollToItem(itemIndex(index))
            }
        }
    }, Modifier.padding(horizontal = pageInset, vertical = 4.dp))
}

/** Hosted above the scaffold; Back dismisses this picker before leaving its collection. */
@Composable
fun LibraryAlphabetMenu(state: AppState) {
    var last by remember { mutableStateOf<AlphabetJumpRequest?>(null) }
    state.alphabetJumpRequest?.let { last = it }
    val request = last
    ActionSheet(visible = state.alphabetJumpRequest != null, title = stringResource(Res.string.library_jump_letter),
        cancelLabel = stringResource(Res.string.action_cancel), onDismiss = state::closeAlphabetJump,
        actions = request?.sections?.map { section ->
            SheetAction(stringResource(Res.string.library_jump_to, section.label), null) { request.onSelect(section.firstIndex) }
        }.orEmpty())
}
