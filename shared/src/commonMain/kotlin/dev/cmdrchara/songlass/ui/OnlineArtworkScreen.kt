package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.cmdrchara.songlass.core.artwork.ArtworkCandidate
import dev.cmdrchara.songlass.core.artwork.ArtworkImageResult
import dev.cmdrchara.songlass.core.artwork.ArtworkSearchFailure
import dev.cmdrchara.songlass.core.artwork.ArtworkSearchResult
import dev.cmdrchara.songlass.core.artwork.ArtworkSubject
import dev.cmdrchara.songlass.core.artwork.DownloadedArtwork
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.Artwork
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.artwork_online_applied
import dev.cmdrchara.songlass.shared.resources.artwork_online_applying
import dev.cmdrchara.songlass.shared.resources.artwork_online_candidates
import dev.cmdrchara.songlass.shared.resources.artwork_online_current
import dev.cmdrchara.songlass.shared.resources.artwork_online_disabled
import dev.cmdrchara.songlass.shared.resources.artwork_online_download_failed
import dev.cmdrchara.songlass.shared.resources.artwork_online_downloads_unavailable
import dev.cmdrchara.songlass.shared.resources.artwork_online_error_malformed
import dev.cmdrchara.songlass.shared.resources.artwork_online_error_offline
import dev.cmdrchara.songlass.shared.resources.artwork_online_error_provider
import dev.cmdrchara.songlass.shared.resources.artwork_online_error_timeout
import dev.cmdrchara.songlass.shared.resources.artwork_online_indicator
import dev.cmdrchara.songlass.shared.resources.artwork_online_local
import dev.cmdrchara.songlass.shared.resources.artwork_online_no_album
import dev.cmdrchara.songlass.shared.resources.artwork_online_no_results
import dev.cmdrchara.songlass.shared.resources.artwork_online_open_settings
import dev.cmdrchara.songlass.shared.resources.artwork_online_preview
import dev.cmdrchara.songlass.shared.resources.artwork_online_privacy
import dev.cmdrchara.songlass.shared.resources.artwork_online_single_privacy
import dev.cmdrchara.songlass.shared.resources.artwork_online_provider
import dev.cmdrchara.songlass.shared.resources.artwork_online_apply
import dev.cmdrchara.songlass.shared.resources.artwork_online_remove
import dev.cmdrchara.songlass.shared.resources.artwork_online_removed
import dev.cmdrchara.songlass.shared.resources.artwork_online_remove_hint
import dev.cmdrchara.songlass.shared.resources.artwork_online_retry
import dev.cmdrchara.songlass.shared.resources.artwork_online_searching
import dev.cmdrchara.songlass.shared.resources.artwork_online_title
import dev.cmdrchara.songlass.shared.resources.artwork_online_waiting_wifi
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.decodeToImageBitmap
import org.jetbrains.compose.resources.stringResource

/** What the lookup is doing right now; the page renders one state at a time. */
private sealed interface LookupState {
    data object Idle : LookupState
    data object Loading : LookupState
    data class Found(val candidates: List<ArtworkCandidate>) : LookupState
    data object NoMatch : LookupState
    data class Failed(val failure: ArtworkSearchFailure) : LookupState
}

/** One-shot feedback after an action; never persisted. */
private enum class OnlineArtworkNotice { Applied, Removed, DownloadFailed, WaitingForWifi }

/**
 * Cover picker for one album: the cover currently shown, the provider's candidates with previews,
 * one confirmation that downloads and applies a cover, and the command that removes it again.
 *
 * The online-cover and network preferences gate every request. Search sends only artist and
 * album tags (artist and title for a track); this picker applies a cover after confirmation.
 * The separate missing-cover owner handles automatic matches for the current track.
 * Business decisions (matching, precedence, cache limits) live in `core/artwork`; this screen only
 * presents their results.
 */
@Composable
fun OnlineArtworkScreen(
    album: Album,
    state: AppState,
    backLabel: String,
    contentPadding: PaddingValues,
    track: Track? = null,
    isActive: () -> Boolean = { true },
) {
    val controller = LocalOnlineArtwork.current
    val preferences by state.settings.state.collectAsState()
    val coverView = if (track == null) rememberAlbumCoverView(album) else rememberTrackCoverView(track)
    val subject = remember(album.artist, album.title, track) {
        if (track == null) ArtworkSubject.of(album.artist, album.title) else ArtworkSubject.forTrack(track)
    }
    var covers: Map<String, DownloadedArtwork> = emptyMap()
    if (controller != null) covers = controller.downloaded.state.collectAsState().value
    val downloaded = subject?.let { covers[it.key] }
    val route = remember(album, track) {
        if (track == null) Route.OnlineArtwork(album) else Route.OnlineTrackArtwork(track)
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifecycleState by lifecycle.currentStateFlow.collectAsState()
    val active = isActive() && lifecycleState.isAtLeast(Lifecycle.State.RESUMED) &&
        state.currentRoute == route && !state.isNowPlayingOpen && !state.hasOverlay
    val latestIsActive by rememberUpdatedState(isActive)
    val latestController by rememberUpdatedState(controller)
    val latestSubject by rememberUpdatedState(subject)
    val latestRoute by rememberUpdatedState(route)

    fun ownsRequest(owner: OnlineArtworkController, target: ArtworkSubject): Boolean =
        latestIsActive() && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            state.currentRoute == latestRoute && !state.isNowPlayingOpen && !state.hasOverlay &&
            latestController === owner && latestSubject == target

    val scope = rememberCoroutineScope()
    var lookup by remember(subject) { mutableStateOf<LookupState>(LookupState.Idle) }
    var previews by remember(subject) { mutableStateOf<Map<String, ImageBitmap>>(emptyMap()) }
    var pendingCandidate by remember(subject) { mutableStateOf<ArtworkCandidate?>(null) }
    val applying = pendingCandidate?.providerId
    var notice by remember(subject) { mutableStateOf<OnlineArtworkNotice?>(null) }
    var attempt by remember(subject) { mutableIntStateOf(0) }

    val enabled = preferences.onlineArtworkEnabled
    var networkAllowed by remember(controller, subject, enabled, preferences.onlineArtworkWifiOnly, active) {
        mutableStateOf(controller != null && subject != null && active && controller.canUseNetwork(preferences))
    }
    fun canUseNetwork(owner: OnlineArtworkController, target: ArtworkSubject): Boolean {
        if (!ownsRequest(owner, target)) return false
        val allowed = owner.canUseNetwork(state.settings.state.value)
        if (!allowed && networkAllowed) {
            networkAllowed = false
            // A brief loss may recover before the next poll. Invalidate the completed lookup
            // too, so a coalesced false/true snapshot cannot leave it waiting in Loading.
            attempt += 1
        }
        return allowed
    }
    // The platform exposes a snapshot callback. Observe it only while this picker is visible;
    // equal values do not restart the lookup or produce another provider request.
    LaunchedEffect(controller, subject, enabled, preferences.onlineArtworkWifiOnly, active) {
        val owner = controller ?: return@LaunchedEffect
        val target = subject ?: return@LaunchedEffect
        if (!active || !enabled) return@LaunchedEffect
        while (currentCoroutineContext().isActive) {
            if (!ownsRequest(owner, target)) return@LaunchedEffect
            networkAllowed = owner.canUseNetwork(state.settings.state.value)
            if (networkAllowed && notice == OnlineArtworkNotice.WaitingForWifi) notice = null
            delay(2_000L)
        }
    }

    LaunchedEffect(controller, subject, active, networkAllowed, attempt) {
        val activeController = controller ?: return@LaunchedEffect
        val target = subject ?: return@LaunchedEffect
        if (!networkAllowed || !canUseNetwork(activeController, target)) {
            if (ownsRequest(activeController, target)) lookup = LookupState.Idle
            return@LaunchedEffect
        }
        lookup = LookupState.Loading
        previews = emptyMap()
        val searchResult = if (target.trackId == null) activeController.search.search(target.album, target.artist)
            else activeController.search.searchTrack(target.album, target.artist)
        currentCoroutineContext().ensureActive()
        if (!canUseNetwork(activeController, target)) return@LaunchedEffect
        lookup = when (val result = searchResult) {
            is ArtworkSearchResult.Found -> LookupState.Found(result.candidates)
            ArtworkSearchResult.NoMatch -> LookupState.NoMatch
            is ArtworkSearchResult.Failed -> LookupState.Failed(result.failure)
        }
    }

    // Previews are fetched small and one at a time: enough to choose, gentle on the provider.
    LaunchedEffect(controller, subject, active, networkAllowed, lookup) {
        val candidates = (lookup as? LookupState.Found)?.candidates ?: return@LaunchedEffect
        val activeController = controller ?: return@LaunchedEffect
        val target = subject ?: return@LaunchedEffect
        val downloader = activeController.downloader ?: return@LaunchedEffect
        if (!networkAllowed) return@LaunchedEffect
        for (candidate in candidates) {
            currentCoroutineContext().ensureActive()
            if (!canUseNetwork(activeController, target)) return@LaunchedEffect
            if (previews.containsKey(candidate.providerId)) continue
            val url = candidate.previewUrl ?: continue
            val bytes = (downloader.downloadImage(url) as? ArtworkImageResult.Success)?.bytes ?: continue
            currentCoroutineContext().ensureActive()
            if (!canUseNetwork(activeController, target)) return@LaunchedEffect
            val bitmap = withContext(Dispatchers.Default) {
                runCatching { bytes.decodeToImageBitmap() }.getOrNull()
            } ?: continue
            currentCoroutineContext().ensureActive()
            if (!canUseNetwork(activeController, target)) return@LaunchedEffect
            previews = previews + (candidate.providerId to bitmap)
        }
    }

    LaunchedEffect(controller, subject, active, networkAllowed, pendingCandidate) {
        val candidate = pendingCandidate ?: return@LaunchedEffect
        try {
            val activeController = controller ?: return@LaunchedEffect
            val target = subject ?: return@LaunchedEffect
            val downloader = activeController.downloader ?: return@LaunchedEffect
            if (!networkAllowed || !canUseNetwork(activeController, target)) return@LaunchedEffect
            val bytes = (downloader.downloadImage(candidate.imageUrl) as? ArtworkImageResult.Success)?.bytes
            currentCoroutineContext().ensureActive()
            if (!canUseNetwork(activeController, target)) return@LaunchedEffect
            val valid = bytes != null && withContext(Dispatchers.Default) {
                runCatching { bytes.decodeToImageBitmap() }.isSuccess
            }
            currentCoroutineContext().ensureActive()
            if (!canUseNetwork(activeController, target)) return@LaunchedEffect
            val saved = if (valid) activeController.downloaded.save(target, checkNotNull(bytes)) else null
            currentCoroutineContext().ensureActive()
            if (!canUseNetwork(activeController, target)) return@LaunchedEffect
            if (saved == null) {
                notice = OnlineArtworkNotice.DownloadFailed
            } else {
                notice = OnlineArtworkNotice.Applied
                state.goBack()
            }
        } finally {
            if (pendingCandidate === candidate) pendingCandidate = null
        }
    }

    fun applyCandidate(candidate: ArtworkCandidate) {
        val activeController = controller ?: return
        val target = subject ?: return
        if (applying != null || activeController.downloader == null) return
        if (!ownsRequest(activeController, target)) return
        if (!canUseNetwork(activeController, target)) {
            notice = OnlineArtworkNotice.WaitingForWifi
            return
        }
        notice = null
        pendingCandidate = candidate
    }

    fun removeDownloaded() {
        val activeController = controller ?: return
        val target = subject ?: return
        if (!ownsRequest(activeController, target)) return
        scope.launch {
            activeController.downloaded.remove(target.key)
            currentCoroutineContext().ensureActive()
            if (ownsRequest(activeController, target)) notice = OnlineArtworkNotice.Removed
        }
    }

    LocalGlassLook.current.SettingsPage {
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                LocalGlassLook.current.SettingsPageTitle(
                    stringResource(Res.string.artwork_online_title), backLabel, state::goBack, Modifier,
                )
            }
            item {
                OnlineArtworkCoverCard(coverView.artwork, downloaded != null, onRemove = ::removeDownloaded)
            }
            item {
                AppText(
                    stringResource(if (subject?.trackId == null) Res.string.artwork_online_privacy else Res.string.artwork_online_single_privacy),
                    AppTheme.typography.footnote,
                    color = AppTheme.colors.secondaryLabel,
                    modifier = Modifier.padding(horizontal = pageInset + 4.dp),
                )
            }
            when {
                controller == null -> item {
                    OnlineArtworkMessage(stringResource(Res.string.artwork_online_downloads_unavailable))
                }
                subject == null -> item { OnlineArtworkMessage(stringResource(Res.string.artwork_online_no_album)) }
                !enabled -> item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = pageInset), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OnlineArtworkMessage(stringResource(Res.string.artwork_online_disabled))
                        ActionButton(stringResource(Res.string.artwork_online_open_settings), {
                            state.openSettingsResult(SettingsTarget.OnlineArtworkEnabled)
                        }, Modifier.fillMaxWidth())
                    }
                }
                !networkAllowed -> item { OnlineArtworkMessage(stringResource(Res.string.artwork_online_waiting_wifi)) }
                else -> {
                    item { OnlineArtworkLookupStatus(lookup, onRetry = { attempt += 1 }) }
                    val candidates = (lookup as? LookupState.Found)?.candidates.orEmpty()
                    if (candidates.isNotEmpty()) {
                        item { SectionTitle(stringResource(Res.string.artwork_online_candidates)) }
                        items(candidates.size) { index ->
                            val candidate = candidates[index]
                            OnlineArtworkCandidateRow(
                                candidate = candidate,
                                preview = previews[candidate.providerId],
                                applying = applying == candidate.providerId,
                                enabled = applying == null && controller.downloader != null,
                                onClick = { applyCandidate(candidate) },
                            )
                        }
                    }
                }
            }
            notice?.let { current ->
                item {
                    val text = when (current) {
                        OnlineArtworkNotice.Applied -> stringResource(Res.string.artwork_online_applied)
                        OnlineArtworkNotice.Removed -> stringResource(Res.string.artwork_online_removed)
                        OnlineArtworkNotice.DownloadFailed -> stringResource(Res.string.artwork_online_download_failed)
                        OnlineArtworkNotice.WaitingForWifi -> stringResource(Res.string.artwork_online_waiting_wifi)
                    }
                    AppText(text, AppTheme.typography.footnote, color = AppTheme.colors.accent,
                        modifier = Modifier.padding(horizontal = pageInset + 4.dp))
                }
            }
            item {
                AppText(
                    stringResource(Res.string.artwork_online_provider),
                    AppTheme.typography.footnote,
                    color = AppTheme.colors.tertiaryLabel,
                    modifier = Modifier.padding(horizontal = pageInset + 4.dp),
                )
            }
            item { Box(Modifier.size(20.dp)) }
        }
    }
}

/** Current cover with the downloaded/own-file state and the command that restores the local one. */
@Composable
private fun OnlineArtworkCoverCard(
    artwork: Artwork,
    downloaded: Boolean,
    onRemove: () -> Unit,
) {
    LocalGlassLook.current.SettingsPageGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
        ListRow(
            title = stringResource(Res.string.artwork_online_current),
            subtitle = stringResource(if (downloaded) Res.string.artwork_online_indicator else Res.string.artwork_online_local),
            leading = { AlbumArt(artwork, Modifier.size(56.dp), cornerRadius = 8.dp) },
        )
        if (downloaded) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppText(stringResource(Res.string.artwork_online_remove_hint), AppTheme.typography.footnote,
                    color = AppTheme.colors.secondaryLabel)
                ActionButton(stringResource(Res.string.artwork_online_remove), onRemove, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun OnlineArtworkLookupStatus(lookup: LookupState, onRetry: () -> Unit) {
    when (lookup) {
        LookupState.Idle -> Unit
        is LookupState.Found -> Unit
        LookupState.Loading -> OnlineArtworkMessage(stringResource(Res.string.artwork_online_searching))
        LookupState.NoMatch -> OnlineArtworkMessage(stringResource(Res.string.artwork_online_no_results))
        is LookupState.Failed -> Column(
            Modifier.fillMaxWidth().padding(horizontal = pageInset),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OnlineArtworkMessage(
                stringResource(
                    when (lookup.failure) {
                        ArtworkSearchFailure.Offline -> Res.string.artwork_online_error_offline
                        ArtworkSearchFailure.Timeout -> Res.string.artwork_online_error_timeout
                        ArtworkSearchFailure.ProviderError -> Res.string.artwork_online_error_provider
                        ArtworkSearchFailure.Malformed -> Res.string.artwork_online_error_malformed
                        ArtworkSearchFailure.Disabled -> Res.string.artwork_online_disabled
                    },
                ),
            )
            ActionButton(stringResource(Res.string.artwork_online_retry), onRetry, Modifier.fillMaxWidth())
        }
    }
}

/** One candidate: its preview, what it is called and the confirmation the user taps. */
@Composable
private fun OnlineArtworkCandidateRow(
    candidate: ArtworkCandidate,
    preview: ImageBitmap?,
    applying: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListRow(
        title = candidate.album,
        subtitle = candidate.artist.takeIf { it.isNotBlank() },
        leading = { CandidatePreview(candidate, preview) },
        trailing = {
            if (applying) {
                AppText(stringResource(Res.string.artwork_online_applying), AppTheme.typography.footnote,
                    color = AppTheme.colors.secondaryLabel)
            } else if (enabled) {
                AppText(stringResource(Res.string.artwork_online_apply), AppTheme.typography.footnote,
                    color = AppTheme.colors.accent)
            }
        },
        onClick = if (enabled) onClick else null,
    )
}

@Composable
private fun CandidatePreview(candidate: ArtworkCandidate, preview: ImageBitmap?) {
    val description = stringResource(Res.string.artwork_online_preview, candidate.album)
    Box(
        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(AppTheme.colors.fill),
        contentAlignment = Alignment.Center,
    ) {
        if (preview != null) {
            Image(preview, contentDescription = description, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            AppIcon(AppIcons.Album, null, tint = AppTheme.colors.secondaryLabel)
        }
    }
}

@Composable
private fun OnlineArtworkMessage(text: String) {
    AppText(
        text,
        AppTheme.typography.body,
        color = AppTheme.colors.secondaryLabel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = pageInset + 4.dp),
    )
}

/**
 * One line on the album page: the cover shown was downloaded, not taken from the files. The picker
 * (the button next to the pin) is where it is removed again.
 */
@Composable
internal fun DownloadedCoverIndicator() {
    AppText(
        stringResource(Res.string.artwork_online_indicator),
        AppTheme.typography.footnote,
        color = AppTheme.colors.accent,
        modifier = Modifier.padding(horizontal = pageInset + 4.dp, vertical = 6.dp),
    )
}

/** The indicator in the album page's lazy list, between the header and the songs. */
internal fun LazyListScope.downloadedCoverNotice() {
    item { DownloadedCoverIndicator() }
}

/** Singles without album tags still have an explicit search-and-apply flow. */
@Composable
fun OnlineArtworkScreen(
    track: Track,
    state: AppState,
    backLabel: String,
    contentPadding: PaddingValues,
    isActive: () -> Boolean = { true },
) = OnlineArtworkScreen(
    Album(track.album, track.groupingArtist, listOf(track)), state, backLabel, contentPadding, track, isActive,
)
