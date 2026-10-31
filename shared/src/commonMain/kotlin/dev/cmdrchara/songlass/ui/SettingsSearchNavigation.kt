package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.settings_search_located
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource

/** Navigation state only. No query, scroll position or highlight is persisted as a preference. */
class SettingsSearchSession {
    var query by mutableStateOf("")
    val resultsScroll = LazyListState()
    val paneScroll = SettingsPane.entries.associateWith { LazyListState() }
    var resultPane by mutableStateOf<SettingsPane?>(null)
    var focus by mutableStateOf<SettingsFocusRequest?>(null)
    internal var durationDraft by mutableStateOf<SettingsDurationDraft?>(null)
    internal var openedDurationRequestId: Int? = null
    private var nextRequest = 0

    fun updateQuery(value: String) {
        if (query == value) return
        query = value
        returnToResults()
        resultsScroll.requestScrollToItem(0)
    }

    fun select(target: SettingsTarget) {
        durationDraft = null
        resultPane = target.settingsPane
        focus = SettingsFocusRequest(target, ++nextRequest)
    }

    fun returnToResults() {
        durationDraft = null
        resultPane = null
        focus = null
    }
}

/** Unsaved presentation state survives lazy-item disposal, but is never persisted. */
internal class SettingsDurationDraft(val target: SettingsTarget, initialMs: Long) {
    var text by mutableStateOf(initialMs.toString())
    var invalid by mutableStateOf(false)
}

/** A fresh request also reveals a result that has already been opened once. */
class SettingsFocusRequest(val target: SettingsTarget, val id: Int) {
    internal var positioned by mutableStateOf(false)
    internal var revealed = false
    internal var viewport by mutableStateOf<Rect?>(null)
    internal var listState: LazyListState? = null
}

internal val SettingsTarget.settingsPane: SettingsPane?
    get() = when (this) {
        SettingsTarget.InterfaceStyle, SettingsTarget.Appearance, SettingsTarget.ArtworkStyle, SettingsTarget.AppIcon,
        SettingsTarget.SearchInNavigationBar, SettingsTarget.LiquidMaterial,
        SettingsTarget.ReduceMotion, SettingsTarget.ReduceTransparency, SettingsTarget.TextScale,
        SettingsTarget.BlurIntensity, SettingsTarget.OnlineArtworkEnabled, SettingsTarget.OnlineArtworkWifiOnly -> SettingsPane.Appearance
        SettingsTarget.Balance, SettingsTarget.StereoExpand, SettingsTarget.Tempo, SettingsTarget.Reverb,
        SettingsTarget.ReplayGainProcessing, SettingsTarget.ReplayGainPreamp, SettingsTarget.ReplayGainMissingPreamp, SettingsTarget.ReplayGainLimiter,
        SettingsTarget.ReplayGain, SettingsTarget.Crossfade, SettingsTarget.SleepTimer,
        SettingsTarget.CrossfadeDuration, SettingsTarget.ManualCrossfadeDuration, SettingsTarget.AudioFocus,
        SettingsTarget.FocusDucking, SettingsTarget.ResumeOnFocusGain, SettingsTarget.ResumeAfterCall,
        SettingsTarget.PauseFadeDuration, SettingsTarget.StopFadeDuration, SettingsTarget.SeekFadeDuration -> SettingsPane.Sound
        SettingsTarget.ResumeOnReconnect, SettingsTarget.ResumeBluetooth, SettingsTarget.BluetoothCommandDelay, SettingsTarget.PauseOnDisconnect, SettingsTarget.HeadsetMultiPress,
        SettingsTarget.ResumePerTrack, SettingsTarget.LongTrackThreshold,
        SettingsTarget.AmllLyrics, SettingsTarget.LrclibLyrics, SettingsTarget.ShowPlayerRating -> SettingsPane.Player
        SettingsTarget.TrackClickAction, SettingsTarget.QueueStart, SettingsTarget.QueueAddOrder, SettingsTarget.QueueReplaces,
        SettingsTarget.QueueEnd, SettingsTarget.ShuffleBias, SettingsTarget.KeepShuffle -> SettingsPane.Queue
        else -> null
    }

internal val SettingsTarget.settingsRoute: Route
    get() = when (this) {
        SettingsTarget.EqualizerBass, SettingsTarget.EqualizerTreble, SettingsTarget.EqualizerBands,
        SettingsTarget.EqualizerLock,
        SettingsTarget.Equalizer, SettingsTarget.EqualizerPresets, SettingsTarget.OutputProfiles,
        SettingsTarget.HeadphoneCorrection -> Route.Equalizer
        SettingsTarget.MinimumDuration, SettingsTarget.MinimumRating, SettingsTarget.ExcludedFolders,
        SettingsTarget.PlayCountRule, SettingsTarget.AlbumLayout, SettingsTarget.ResumeOverrides -> Route.LibrarySettings
        SettingsTarget.HomeCustomization -> Route.HomeCustomization
        SettingsTarget.Backup -> Route.Backup
        SettingsTarget.About -> Route.About
        SettingsTarget.LastFm -> Route.LastFm
        else -> Route.Settings
    }

/**
 * First compose the owning lazy item. The anchor then reveals its precise nested control.
 * Apply the returned modifier to the list after its layout/inset modifiers so its viewport
 * excludes status bars and surrounding chrome. Content padding is accounted for below.
 */
@Composable
internal fun SettingsSearchScroll(request: SettingsFocusRequest?, listState: LazyListState, positions: SettingsItemPositions): Modifier {
    LaunchedEffect(request) {
        if (request == null || request.revealed) return@LaunchedEffect
        request.listState = listState
        // The page records its positions while its list is built, so wait until there is something to measure.
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        val itemIndex = positions.byTarget[request.target] ?: return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > itemIndex }
        if (listState.layoutInfo.visibleItemsInfo.none { it.index == itemIndex }) listState.scrollToItem(itemIndex)
        withFrameNanos { }
        request.positioned = true
    }
    return if (request == null) Modifier else Modifier.onGloballyPositioned {
        request.viewport = it.boundsInWindow()
    }
}

/** For pages that are fixed lists written by hand: the item index of the control is known. */
@Composable
internal fun SettingsSearchScroll(request: SettingsFocusRequest?, listState: LazyListState, itemIndex: Int?): Modifier {
    val positions = remember(request?.target, itemIndex) {
        SettingsItemPositions().also { if (request != null && itemIndex != null) it.byTarget[request.target] = itemIndex }
    }
    return SettingsSearchScroll(request, listState, positions)
}

/** A temporary outline and spoken cue locate the control without activating it or stealing focus. */
@Composable
internal fun SettingsSearchAnchor(
    target: SettingsTarget,
    request: SettingsFocusRequest?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    var highlighted by remember(request) { mutableStateOf(false) }
    val reduceMotion = AppTheme.accessibility.reduceMotion
    val located = stringResource(Res.string.settings_search_located, settingsTargetTitle(target))
    LaunchedEffect(request, request?.positioned) {
        if (request == null || request.target != target || !request.positioned || request.revealed) return@LaunchedEffect
        snapshotFlow { bounds?.let { anchor -> request.viewport?.let { viewport -> anchor to viewport } } }
            .filterNotNull().first()
        // The coarse lazy scroll and route transition must have reached layout before measuring.
        withFrameNanos { }
        val anchor = bounds ?: return@LaunchedEffect
        val viewport = request.viewport ?: return@LaunchedEffect
        val list = request.listState ?: return@LaunchedEffect
        val top = viewport.top + list.layoutInfo.beforeContentPadding
        val bottom = viewport.bottom - list.layoutInfo.afterContentPadding
        val distance = when {
            anchor.top < top || anchor.height > bottom - top -> anchor.top - top
            anchor.bottom > bottom -> anchor.bottom - bottom
            else -> 0f
        }
        if (distance != 0f) {
            if (reduceMotion) list.scrollBy(distance) else list.animateScrollBy(distance)
        }
        request.revealed = true
        highlighted = true
        delay(2_500)
        highlighted = false
    }
    Box(modifier.fillMaxWidth().onGloballyPositioned {
        // Keep the full control bounds even when a lazy item clips most of it offscreen.
        bounds = Rect(it.localToWindow(Offset.Zero), Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }
        .then(if (highlighted) Modifier.border(2.dp, AppTheme.colors.accent, RoundedCornerShape(16.dp)) else Modifier)
        .semantics {
            if (highlighted) {
                stateDescription = located
                liveRegion = LiveRegionMode.Polite
            }
        }) { content() }
}
