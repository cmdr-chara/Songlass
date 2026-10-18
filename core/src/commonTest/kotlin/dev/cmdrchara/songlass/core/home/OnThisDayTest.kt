package dev.cmdrchara.songlass.core.home

import dev.cmdrchara.songlass.core.history.*
import dev.cmdrchara.songlass.core.library.MusicLibrary
import dev.cmdrchara.songlass.core.model.ReleaseDate
import dev.cmdrchara.songlass.core.track
import kotlin.test.*

class OnThisDayTest {
    @Test fun anniversariesRequireFullMatchingPastDate() {
        val library = MusicLibrary(listOf(
            track("a").copy(releaseDate = ReleaseDate(2000, 10, 18)),
            track("b").copy(releaseDate = ReleaseDate(2000)),
            track("c").copy(releaseDate = ReleaseDate(2026, 10, 18)),
            track("d").copy(releaseDate = ReleaseDate(2000, 10, 17))))
        val result = OnThisDay.build(library, HistorySnapshot(), ReleaseDate(2026, 10, 18)) { error("No events") }
        assertEquals(listOf("a"), result.releases.map { it.id })
    }
    @Test fun memoriesDeduplicateAndUsePlatformLocalDate() {
        val history = HistorySnapshot(datedPlays = listOf(DatedPlay("a", 1), DatedPlay("a", 2), DatedPlay("missing", 3), DatedPlay("b", 4)))
        val result = OnThisDay.build(MusicLibrary(listOf(track("a"), track("b"))), history, ReleaseDate(2026, 10, 18)) {
            if (it == 4L) ReleaseDate(2026, 10, 18) else ReleaseDate(2025, 10, 18)
        }
        assertEquals(listOf("a"), result.memories.map { it.id })
    }
}
