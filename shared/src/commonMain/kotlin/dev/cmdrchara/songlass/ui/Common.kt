package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.library.Album
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.designsystem.AlbumArt
import dev.cmdrchara.songlass.designsystem.IconButton
import dev.cmdrchara.songlass.designsystem.BackButton
import dev.cmdrchara.songlass.designsystem.ListRow
import dev.cmdrchara.songlass.designsystem.PlaybackLabels
import dev.cmdrchara.songlass.designsystem.foundation.AppIcon
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.icons.AppIcons
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.Res
import dev.cmdrchara.songlass.shared.resources.action_more
import dev.cmdrchara.songlass.shared.resources.playback_next
import dev.cmdrchara.songlass.shared.resources.playback_pause
import dev.cmdrchara.songlass.shared.resources.playback_play
import dev.cmdrchara.songlass.shared.resources.playback_previous
import dev.cmdrchara.songlass.shared.resources.unknown_album
import dev.cmdrchara.songlass.shared.resources.unknown_artist
import org.jetbrains.compose.resources.stringResource

@Composable
fun playbackLabels() = PlaybackLabels(
    play = stringResource(Res.string.playback_play),
    pause = stringResource(Res.string.playback_pause),
    next = stringResource(Res.string.playback_next),
    previous = stringResource(Res.string.playback_previous),
)

@Composable
fun artistName(name: String?): String = name ?: stringResource(Res.string.unknown_artist)

@Composable
fun albumTitle(title: String?): String = title ?: stringResource(Res.string.unknown_album)

/** Large navigation title at the top of a root screen. */
@Composable
fun ScreenTitle(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = text,
            style = AppTheme.typography.largeTitle,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.invoke()
    }
}

@Composable
fun SectionTitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppText(
            text = text,
            style = AppTheme.typography.title3.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        action?.invoke()
    }
}

/** iOS-style back link: chevron plus the previous screen's title, 44 dp tall. */
@Composable
fun BackLink(label: String, onBack: () -> Unit) {
    BackButton(label, onBack, Modifier.windowInsetsPadding(WindowInsets.statusBars))
}

@Composable
fun TrackRow(track: Track, highlighted: Boolean, onMore: () -> Unit, onClick: () -> Unit) {
    ListRow(
        title = track.title,
        subtitle = artistName(track.artist),
        leading = { AlbumArt(track.artwork(), Modifier.size(48.dp), cornerRadius = 6.dp) },
        trailing = { MoreButton(onMore) },
        highlighted = highlighted,
        onClick = onClick,
    )
}

/** "…" button that opens the actions for a row. */
@Composable
fun MoreButton(onClick: () -> Unit) {
    IconButton(AppIcons.More, stringResource(Res.string.action_more), onClick)
}

@Composable
fun AlbumTile(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)) {
        AlbumArt(album.artwork(), Modifier.fillMaxWidth().aspectRatio(1f), cornerRadius = 10.dp)
        Spacer(Modifier.size(6.dp))
        AppText(albumTitle(album.title), AppTheme.typography.subheadline.copy(fontWeight = FontWeight.Medium), maxLines = 1)
        AppText(artistName(album.artist), AppTheme.typography.subheadline, color = AppTheme.colors.secondaryLabel, maxLines = 1)
    }
}

/** Two-column album grid row. */
@Composable
fun AlbumGridRow(albums: List<Album>, onOpen: (Album) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        albums.forEachIndexed { index, album ->
            if (index > 0) Spacer(Modifier.width(16.dp))
            AlbumTile(album, onClick = { onOpen(album) }, modifier = Modifier.weight(1f))
        }
        if (albums.size == 1) {
            Spacer(Modifier.width(16.dp))
            Spacer(Modifier.weight(1f))
        }
    }
}
