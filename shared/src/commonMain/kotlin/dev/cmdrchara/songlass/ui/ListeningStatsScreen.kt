package dev.cmdrchara.songlass.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
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
    val comparison = remember(state.library, history, period, now, stats) {
        period.days?.let { days ->
            ListeningStats.compare(state.library, history, period, stats, localDayStarts(now, days * 2).first(), dayStarts.first())
        }
    }
    val trend = remember(state.library, history, dayStarts, now) { ListeningTrend.build(state.library, history, dayStarts, now) }
    var expanded by remember(period) { mutableStateOf(emptySet<String>()) }
    val chartPoints = trend.map { bucket ->
        val first = localCalendarDate(bucket.fromMs)
        val last = localCalendarDate(bucket.untilMs - 1)
        val from = stringResource(Res.string.stats_chart_date, first.day ?: 1, first.month ?: 1)
        val to = stringResource(Res.string.stats_chart_date, last.day ?: 1, last.month ?: 1)
        val label = if (from == to) from else "$from – $to"
        val value = if (bucket.recorded) pluralStringResource(Res.plurals.stats_play_count, bucket.plays, bucket.plays)
            else stringResource(Res.string.stats_chart_no_data)
        ListeningChartPoint(label, from, bucket.plays, "$label: $value", bucket.recorded, value)
    }
    val chartSummary = stringResource(Res.string.stats_chart_summary, trend.sumOf { it.plays })
    val started = localCalendarDate(history.trackingStartedMs ?: now)
    val datedSince = stringResource(Res.string.stats_dated_since, started.day ?: 1, started.month ?: 1, started.year)
    val periods = StatsPeriod.entries
    val titles = mapOf("t" to stringResource(Res.string.stats_top_tracks), "a" to stringResource(Res.string.stats_top_artists),
        "al" to stringResource(Res.string.stats_top_albums))
    LazyColumn(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars), contentPadding = contentPadding) {
        item { BackLink(stringResource(Res.string.tab_home), state::goBack) }
        item { AppText(stringResource(Res.string.stats_title), AppTheme.typography.largeTitle,
            modifier = Modifier.padding(horizontal = pageInset, vertical = 16.dp).semantics { heading() }) }
        item {
            // Four exclusive periods: a full-width segmented control, as in each style's own idiom.
            SegmentedControl(periods.map { stringResource(when (it) {
                StatsPeriod.Week -> Res.string.stats_week
                StatsPeriod.Month -> Res.string.stats_month
                StatsPeriod.Year -> Res.string.stats_year
                StatsPeriod.AllTime -> Res.string.stats_all_time
            }) }, periods.indexOf(period), { period = periods[it] },
                Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 8.dp))
        }
        item {
            StatSummary(listOf(
                StatFigure(stringResource(Res.string.stats_plays), stats.totalPlays.toString(), comparison?.plays?.let { changeText(it) }),
                StatFigure(stringResource(Res.string.stats_tracks), stats.differentTracks.toString(), comparison?.tracks?.let { changeText(it) }),
                StatFigure(stringResource(Res.string.stats_artists), stats.differentArtists.toString(), comparison?.artists?.let { changeText(it) }),
            ), Modifier.padding(horizontal = pageInset, vertical = 4.dp))
        }
        item { SectionTitle(stringResource(Res.string.stats_chart_title)) }
        item {
            ListeningChart(chartPoints, chartSummary, stringResource(Res.string.stats_chart_unrecorded),
                Modifier.fillMaxWidth().padding(horizontal = pageInset, vertical = 4.dp))
        }
        // Only when part of the period predates dated tracking; otherwise the note says nothing new.
        if (!stats.coversWholePeriod || trend.any { !it.recorded }) item {
            AppText(datedSince, AppTheme.typography.footnote,
                color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        }
        if (stats.tracks.isEmpty()) item {
            AppText(stringResource(Res.string.stats_empty), AppTheme.typography.body, modifier = Modifier.padding(24.dp))
        }
        if (stats.tracks.isNotEmpty()) {
            rankedSection("t", titles.getValue("t"), stats.tracks, expanded, { expanded = expanded.toggled("t") }) { ranked ->
                RankedRow(
                    rank = ranked.rank,
                    title = ranked.track.title,
                    subtitle = artistName(ranked.track.artist),
                    leading = { AlbumArt(ranked.track.artwork(), Modifier.size(48.dp), cornerRadius = 8.dp) },
                    value = pluralStringResource(Res.plurals.stats_play_count, ranked.plays, ranked.plays),
                    highlighted = ranked.track.id == currentTrackId,
                    onClick = { state.controller.playTracks(stats.tracks.map { it.track }, stats.tracks.indexOf(ranked)) },
                )
            }
            rankedSection("a", titles.getValue("a"), stats.artists, expanded, { expanded = expanded.toggled("a") }) { ranked ->
                RankedRow(
                    rank = ranked.rank,
                    title = artistName(ranked.artist.name),
                    leading = {
                        val art = ranked.artist.albums.firstOrNull()?.artwork() ?: ranked.artist.tracks.firstOrNull()?.artwork()
                        if (art != null) ArtistAvatar(art, Modifier.size(48.dp))
                    },
                    value = pluralStringResource(Res.plurals.stats_play_count, ranked.plays, ranked.plays),
                    onClick = { state.open(Route.ArtistDetail(ranked.artist)) },
                )
            }
            if (stats.albums.isNotEmpty()) {
                rankedSection("al", titles.getValue("al"), stats.albums, expanded, { expanded = expanded.toggled("al") }) { ranked ->
                    RankedRow(
                        rank = ranked.rank,
                        title = albumTitle(ranked.album.title),
                        subtitle = artistName(ranked.album.artist),
                        leading = { AlbumArt(ranked.album.artwork(), Modifier.size(48.dp), cornerRadius = 8.dp) },
                        value = pluralStringResource(Res.plurals.stats_play_count, ranked.plays, ranked.plays),
                        onClick = { state.open(Route.AlbumDetail(ranked.album)) },
                    )
                }
            }
        }
        item { AppText(stringResource(Res.string.stats_history_hint), AppTheme.typography.footnote,
            color = AppTheme.colors.secondaryLabel, modifier = Modifier.padding(24.dp)) }
    }
}

private const val COLLAPSED_ENTRIES = 5
private const val EXPANDED_ENTRIES = 25

private fun Set<String>.toggled(key: String) = if (key in this) this - key else this + key

/** A titled ranking showing the first five entries, expandable to the first 25. */
private fun <T> LazyListScope.rankedSection(
    key: String, title: String, entries: List<T>, expanded: Set<String>, onToggle: () -> Unit, row: @Composable (T) -> Unit,
) {
    item(key = "$key:title") { SectionTitle(title) }
    val open = key in expanded
    items(entries.take(if (open) EXPANDED_ENTRIES else COLLAPSED_ENTRIES)) { row(it) }
    if (entries.size > COLLAPSED_ENTRIES) item(key = "$key:more") {
        ActionButton(
            if (open) stringResource(Res.string.stats_show_fewer)
            else stringResource(Res.string.stats_show_all, minOf(entries.size, EXPANDED_ENTRIES)),
            onToggle, Modifier.padding(horizontal = pageInset, vertical = 8.dp),
        )
    }
}

@Composable
private fun changeText(change: StatChange): String = when {
    change.difference > 0 -> stringResource(Res.string.stats_change_up, change.difference)
    change.difference < 0 -> stringResource(Res.string.stats_change_down, -change.difference)
    else -> stringResource(Res.string.stats_change_same)
}
