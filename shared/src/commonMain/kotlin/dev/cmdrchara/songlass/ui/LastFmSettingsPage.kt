package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.scrobble.LastFmAuthorizationResult
import dev.cmdrchara.songlass.core.scrobble.LastFmAuthorizationStart
import dev.cmdrchara.songlass.core.scrobble.LastFmFailure
import dev.cmdrchara.songlass.core.scrobble.LastFmScrobbler
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.SettingsTarget
import dev.cmdrchara.songlass.designsystem.ActionSheet
import dev.cmdrchara.songlass.designsystem.FormField
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.SettingsToggleRow
import dev.cmdrchara.songlass.designsystem.SheetAction
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import dev.cmdrchara.songlass.theme.liquidglass.LocalGlassLook
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/**
 * Settings → Last.fm. Presentation only: every decision (queueing, retries, authorization,
 * classification of failures) lives in [LastFmScrobbler] and in the core preferences.
 *
 * The page works in all three interface styles because it only uses the shared settings primitives;
 * the network calls it starts are suspended in a composition scope and never block rendering.
 */
@Composable
fun LastFmSettingsPage(
    settings: AppSettings,
    scrobbler: LastFmScrobbler,
    backLabel: String,
    onBack: () -> Unit,
    onForget: () -> Unit,
    contentPadding: PaddingValues,
    searchFocus: SettingsFocusRequest? = null,
) {
    val preferences by settings.state.collectAsState()
    val status by scrobbler.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var authorization by remember { mutableStateOf<LastFmAuthorizationStart.Ready?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<LastFmFailure?>(null) }
    var keyDraft by remember { mutableStateOf("") }
    var secretDraft by remember { mutableStateOf("") }
    var confirmingForget by remember { mutableStateOf(false) }

    // Another component may have reset the account while this page was closed.
    LaunchedEffect(scrobbler) { scrobbler.reloadFromStorage() }

    fun beginAuthorization() {
        if (busy) return
        busy = true
        failure = null
        scope.launch {
            when (val started = scrobbler.beginAuthorization()) {
                is LastFmAuthorizationStart.Ready -> authorization = started
                LastFmAuthorizationStart.NotConfigured -> failure = LastFmFailure.RejectedCredentials
                LastFmAuthorizationStart.Unsupported -> failure = LastFmFailure.MalformedResponse
                is LastFmAuthorizationStart.Failed -> failure = started.failure
            }
            busy = false
        }
    }

    fun completeAuthorization() {
        val token = authorization?.token ?: return
        if (busy) return
        busy = true
        failure = null
        scope.launch {
            when (val connected = scrobbler.completeAuthorization(token)) {
                is LastFmAuthorizationResult.Connected -> {
                    authorization = null
                    // Connecting is the user asking for scrobbling; the switch can still turn it off.
                    settings.setLastFmEnabled(true)
                    scrobbler.refresh()
                }
                LastFmAuthorizationResult.NotConfigured -> failure = LastFmFailure.RejectedCredentials
                LastFmAuthorizationResult.Unsupported -> failure = LastFmFailure.MalformedResponse
                is LastFmAuthorizationResult.Failed -> failure = connected.failure
            }
            busy = false
        }
    }

    fun saveCredentials() {
        val key = keyDraft.trim()
        val secret = secretDraft.trim()
        if (busy || key.isEmpty() || secret.isEmpty()) return
        // A session is issued for one API key; keeping it after a key change would fail with a
        // bad signature, so the old account is disconnected first.
        if (status.connected) scrobbler.disconnect()
        settings.setLastFmApiCredentials(key, secret)
        keyDraft = ""
        secretDraft = ""
        failure = null
        scrobbler.refresh()
    }

    LocalGlassLook.current.SettingsPage {
        val reveal = SettingsSearchScroll(
            searchFocus, listState,
            if (searchFocus?.target == SettingsTarget.LastFm) 1 else null,
        )
        LazyColumn(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars).imePadding().then(reveal),
            state = listState,
            contentPadding = contentPadding,
        ) {
            item(key = "title") {
                LocalGlassLook.current.SettingsPageTitle(
                    stringResource(Res.string.lastfm_title), backLabel, onBack, Modifier,
                )
            }
            if (!status.supported) {
                item(key = "unsupported") { LastFmHint(stringResource(Res.string.lastfm_unsupported)) }
                item(key = "end") { Box(Modifier.padding(bottom = 20.dp)) }
                return@LazyColumn
            }

            item(key = "connection-title") { SettingsPageSection(stringResource(Res.string.lastfm_section_connection)) }
            item(key = "enable") {
                SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
                    SettingsSearchAnchor(SettingsTarget.LastFm, searchFocus) {
                        SettingsToggleRow(
                            title = stringResource(Res.string.lastfm_enable),
                            checked = preferences.lastFmEnabled,
                            onCheckedChange = { enabled ->
                                settings.setLastFmEnabled(enabled)
                                scrobbler.refresh()
                            },
                            subtitle = stringResource(Res.string.lastfm_enable_hint),
                            enabled = status.configured && !busy,
                        )
                    }
                }
            }
            item(key = "account") {
                SettingsGroup(Modifier.padding(horizontal = pageInset).padding(top = 4.dp)) {
                    val pendingAuthorization = authorization
                    when {
                        status.connected -> {
                            ListRow(
                                title = stringResource(Res.string.lastfm_account),
                                fullText = true,
                                subtitle = stringResource(
                                    Res.string.lastfm_connected_as, status.username.orEmpty(),
                                ),
                            )
                            ListRow(
                                title = stringResource(Res.string.lastfm_disconnect),
                                fullText = true,
                                subtitle = stringResource(Res.string.lastfm_disconnect_hint),
                                leading = { AppIcon(AppIcons.InfoCircle, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                                onClick = { if (!busy) scrobbler.disconnect() },
                            )
                        }
                        pendingAuthorization != null -> {
                            ListRow(
                                title = stringResource(Res.string.lastfm_account),
                                fullText = true,
                                subtitle = stringResource(Res.string.lastfm_authorize),
                            )
                            ListRow(
                                title = stringResource(Res.string.lastfm_open_authorization),
                                fullText = true,
                                leading = { AppIcon(AppIcons.InfoCircle, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                                onClick = { uriHandler.openUri(pendingAuthorization.url) },
                            )
                            ListRow(
                                title = stringResource(Res.string.lastfm_complete_connection),
                                fullText = true,
                                leading = { AppIcon(AppIcons.Checkmark, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                                onClick = { completeAuthorization() },
                            )
                        }
                        status.configured -> {
                            ListRow(
                                title = stringResource(Res.string.lastfm_account),
                                fullText = true,
                                subtitle = stringResource(Res.string.lastfm_not_connected),
                            )
                            ListRow(
                                title = stringResource(Res.string.lastfm_connect),
                                fullText = true,
                                leading = { AppIcon(AppIcons.InfoCircle, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                                onClick = { beginAuthorization() },
                            )
                        }
                        else -> ListRow(
                            title = stringResource(Res.string.lastfm_account),
                            fullText = true,
                            subtitle = stringResource(Res.string.lastfm_not_connected),
                        )
                    }
                }
            }
            if (failure != null) {
                item(key = "connection-error") { LastFmHint(lastFmFailureMessage(failure!!)) }
            }

            item(key = "api-title") { SettingsPageSection(stringResource(Res.string.lastfm_section_api)) }
            item(key = "api") {
                SettingsGroup(Modifier.padding(horizontal = pageInset)) {
                    ListRow(
                        title = stringResource(Res.string.lastfm_api_state),
                        fullText = true,
                        subtitle = stringResource(
                            if (status.configured) Res.string.lastfm_api_configured else Res.string.lastfm_api_missing,
                        ),
                    )
                    FormField(keyDraft, { keyDraft = it }, stringResource(Res.string.lastfm_api_key), Modifier)
                    FormField(secretDraft, { secretDraft = it }, stringResource(Res.string.lastfm_api_secret), Modifier)
                    ListRow(
                        title = stringResource(Res.string.lastfm_api_save),
                        fullText = true,
                        leading = { AppIcon(AppIcons.Checkmark, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                        onClick = { saveCredentials() },
                    )
                }
            }
            item(key = "api-hint") { LastFmHint(stringResource(Res.string.lastfm_api_hint)) }

            item(key = "pending-title") { SettingsPageSection(stringResource(Res.string.lastfm_section_pending)) }
            item(key = "pending") {
                LastFmHint(
                    if (status.pendingScrobbles == 0) stringResource(Res.string.lastfm_pending_none)
                    else stringResource(Res.string.lastfm_pending_count, status.pendingScrobbles.toString()),
                )
            }
            if (status.pendingScrobbles > 0) {
                item(key = "retry") {
                    SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 4.dp)) {
                        ListRow(
                            title = stringResource(Res.string.lastfm_retry),
                            fullText = true,
                            leading = { AppIcon(AppIcons.AudioOutput, contentDescription = null, size = 24.dp, tint = AppTheme.colors.accent) },
                            onClick = { if (!busy) scrobbler.refresh() },
                        )
                    }
                }
            }
            status.lastFailure?.let { last ->
                item(key = "queue-error") { LastFmHint(lastFmFailureMessage(last)) }
            }

            item(key = "privacy-title") { SettingsPageSection(stringResource(Res.string.lastfm_section_privacy)) }
            item(key = "privacy") { LastFmHint(stringResource(Res.string.lastfm_privacy_hint)) }
            item(key = "privacy-storage") { LastFmHint(stringResource(Res.string.lastfm_privacy_storage)) }

            item(key = "forget") {
                SettingsGroup(Modifier.padding(horizontal = pageInset, vertical = 8.dp)) {
                    ListRow(
                        title = stringResource(Res.string.lastfm_forget),
                        fullText = true,
                        subtitle = stringResource(Res.string.lastfm_forget_hint),
                        onClick = { confirmingForget = true },
                    )
                }
            }
            item(key = "end") { Box(Modifier.padding(bottom = 20.dp)) }
        }
    }

    ActionSheet(
        visible = confirmingForget,
        title = stringResource(Res.string.lastfm_forget),
        subtitle = stringResource(Res.string.lastfm_forget_hint),
        actions = listOf(SheetAction(stringResource(Res.string.lastfm_forget_action), null) {
            confirmingForget = false
            authorization = null
            failure = null
            onForget()
        }),
        cancelLabel = stringResource(Res.string.action_cancel),
        onDismiss = { confirmingForget = false },
    )
}

/** Why the last call failed, in words the user can act on. */
@Composable
private fun lastFmFailureMessage(failure: LastFmFailure): String = stringResource(
    when (failure) {
        LastFmFailure.Offline -> Res.string.lastfm_error_offline
        LastFmFailure.RateLimited -> Res.string.lastfm_error_rate_limited
        LastFmFailure.ServerError -> Res.string.lastfm_error_server
        LastFmFailure.SessionExpired -> Res.string.lastfm_error_session
        LastFmFailure.RejectedCredentials -> Res.string.lastfm_error_credentials
        LastFmFailure.Suspended -> Res.string.lastfm_error_suspended
        LastFmFailure.MalformedResponse -> Res.string.lastfm_error_response
    },
)

@Composable
private fun LastFmHint(text: String) = AppText(
    text,
    AppTheme.typography.footnote,
    color = AppTheme.colors.secondaryLabel,
    modifier = Modifier.padding(horizontal = pageInset + 4.dp, vertical = 8.dp),
)
