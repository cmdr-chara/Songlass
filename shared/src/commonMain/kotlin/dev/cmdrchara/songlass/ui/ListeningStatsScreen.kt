package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.cmdrchara.songlass.core.history.*
import dev.cmdrchara.songlass.designsystem.*
import dev.cmdrchara.songlass.designsystem.foundation.AppText
import dev.cmdrchara.songlass.designsystem.theme.AppTheme
import dev.cmdrchara.songlass.shared.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Clock

@Composable
fun ListeningStatsScreen(state: AppState, currentTrackId: String?, contentPadding: PaddingValues) {
    val history by state.history.state.collectAsState()
    var period by remember { mutableStateOf(StatsPeriod.Month) }
    val now = remember(history) { Clock.System.now().toEpochMilliseconds() }
    val days = remember(history, period, now) { ListeningTrend.days(period, history, now) }
    val dayStarts = remember(days, now) { localDayStarts(now, days) }
    val stats = remember(state.library, history, period, now, dayStarts) {
        ListeningStats.build(state.library, history, period, now, dayStarts.first())
    }
    val trend = remember(state.library, history, dayStarts, now) { ListeningTrend.build(state.library, history, dayStarts, now) }
    val chartPoints = trend.map { bucket ->
        val first = localCalendarDate(bucket.fromMs)
        val last = localCalendarDate(bucket.untilMs - 1)
        val from = stringResource(Res.string.stats_chart_date, first.day ?: 1, first.month ?: 1)
        val to = stringResource(Res.string.stats_chart_date, last.day ?: 1, last.month ?: 1)
        val label = if (from == to) from else "$from – $to"
        val value = if (bucket.recorded) pluralStringResource(Res.plurals.stats_play_count, bucket.plays, bucket.plays)
            else stringResource(Res.string.stats_chart_no_data)
        ListeningChartPoint(label, from, bucket.plays, "$label: $value", bucket.recorded)
    }
    val chartSummary = stringResource(Res.string.stats_chart_summary, trend.sumOf { it.plays })
    val started = localCalendarDate(history.trackingStartedMs ?: now)
    val datedSince = stringResource(Res.string.stats_dated_since, started.day ?: 1, started.month ?: 1, started.year)
    val periods = StatsPeriod.entries
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item { BackLink(stringResource(Res.string.tab_home), state::goBack) }
        item { AppText(stringResource(Res.string.stats_title), AppTheme.typography.largeTitle,
            modifier = Modifier.padding(16.dp).semantics { heading() }) }
        item {
            FilterChips(periods.map { stringResource(when (it) {
                StatsPeriod.Week -> Res.string.stats_week
                StatsPeriod.Month -> Res.string.stats_month
                StatsPeriod.Year -> Res.string.stats_year
                StatsPeriod.AllTime -> Res.string.stats_all_time
            }) }, periods.indexOf(period), { period = periods[it] }, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        item {
            SettingsGroup(Modifier.padding(horizontal = 16.dp)) {
                ListRow(stringResource(Res.string.stats_plays), trailing = { AppText(stats.totalPlays.toString(), AppTheme.typography.title2) })
                ListRow(stringResource(Res.string.stats_tracks), trailing = { AppText(stats.differentTracks.toString(), AppTheme.typography.title2) })
                ListRow(stringResource(Res.string.stats_artists), trailing = { AppText(stats.differentArtists.toString(), AppTheme.typography.title2) })
            }
        }
        item { SectionTitle(stringResource(Res.string.stats_chart_title)) }
        item { ListeningChart(chartPoints, chartSummary, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) }
        item { AppText(datedSince, AppTheme.typography.footnote,
            color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        if (stats.tracks.isEmpty()) item {
            AppText(stringResource(Res.string.stats_empty), AppTheme.typography.body, modifier = Modifier.padding(24.dp))
        }
        if (stats.tracks.isNotEmpty()) {
            item { SectionTitle(stringResource(Res.string.stats_top_tracks)) }
            items(stats.tracks.take(10), key = { "t:${it.track.id}" }) { ranked ->
                ListRow(
                    title = ranked.track.title,
                    subtitle = artistName(ranked.track.artist),
                    leading = { AlbumArt(ranked.track.artwork(), Modifier.size(56.dp), cornerRadius = 8.dp) },
                    trailing = { AppText(pluralStringResource(Res.plurals.stats_play_count, ranked.plays, ranked.plays), AppTheme.typography.footnote) },
                    highlighted = ranked.track.id == currentTrackId,
                    onClick = { state.controller.playTracks(stats.tracks.map { it.track }, stats.tracks.indexOf(ranked)) },
                )
            }
            item { SectionTitle(stringResource(Res.string.stats_top_artists)) }
            items(stats.artists.take(10), key = { "a:${it.artist.name}" }) { ranked ->
                ListRow(artistName(ranked.artist.name), subtitle = pluralStringResource(Res.plurals.stats_play_count, ranked.plays, ranked.plays),
                    onClick = { state.open(Route.ArtistDetail(ranked.artist)) })
            }
            if (stats.albums.isNotEmpty()) {
                item { SectionTitle(stringResource(Res.string.stats_top_albums)) }
                items(stats.albums.take(10), key = { "album:${it.album.title}:${it.album.artist}" }) { ranked ->
                    ListRow(albumTitle(ranked.album.title), subtitle = pluralStringResource(Res.plurals.stats_play_count, ranked.plays, ranked.plays),
                        leading = { AlbumArt(ranked.album.artwork(), Modifier.size(56.dp), cornerRadius = 8.dp) },
                        onClick = { state.open(Route.AlbumDetail(ranked.album)) })
                }
            }
        }
        item { AppText(stringResource(Res.string.stats_history_hint), AppTheme.typography.footnote,
            color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(24.dp)) }
    }
}
