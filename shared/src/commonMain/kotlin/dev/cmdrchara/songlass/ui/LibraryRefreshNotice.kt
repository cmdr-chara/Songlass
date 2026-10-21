package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.LibraryRefreshFailure
import dev.cmdrchara.songlass.designsystem.ActionButton
import dev.cmdrchara.songlass.designsystem.SettingsGroup
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.stringResource

/** One recovery path across the three looks, including a first scan with no visible tracks. */
@Composable
internal fun LibraryRefreshNotice(failure: LibraryRefreshFailure?, loading: Boolean, onRetry: () -> Unit) {
    if (failure == null) return
    SettingsGroup(Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 8.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppText(stringResource(Res.string.library_refresh_failed), AppTheme.typography.headline,
                modifier = Modifier.semantics { heading() })
            AppText(stringResource(if (failure == LibraryRefreshFailure.Identity)
                Res.string.library_identity_failed_hint else Res.string.library_refresh_failed_hint),
                AppTheme.typography.body, color = AppTheme.colors.secondaryLabel,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (!loading) ActionButton(stringResource(Res.string.library_refresh_retry), onRetry)
        }
    }
}
