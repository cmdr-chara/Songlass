package dev.cmdrchara.songlass.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchTest {
    private val documents = SettingsTarget.entries.map { target ->
        SettingsSearchDocument(target, "Control ${target.name}", "Settings")
    }
    private val index = SettingsSearchIndex(documents)

    @Test
    fun roadmapQueriesFindTheirControlsInBothLanguages() {
        assertEquals(SettingsTarget.CrossfadeDuration, index.search("ms", true).first())
        assertEquals(SettingsTarget.Crossfade, index.search("crossfade", true).first())
        assertEquals(SettingsTarget.ReplayGain, index.search("normalizzazione", true).first())
        assertEquals(SettingsTarget.ReplayGain, index.search("volume normalization", true).first())
        assertEquals(SettingsTarget.AmllLyrics, index.search("testi", true).first())
        assertEquals(SettingsTarget.AmllLyrics, index.search("lyrics", true).first())
        val headphones = index.search("cuffie", true)
        assertTrue(SettingsTarget.ResumeOnReconnect in headphones)
        assertTrue(SettingsTarget.HeadphoneCorrection in headphones)
        assertEquals(SettingsTarget.HeadphoneCorrection, headphones.first())
    }

    @Test
    fun bilingualLabelsWorkWithDifferentDisplayedLanguage() {
        val translated = SettingsSearchIndex(listOf(
            SettingsSearchDocument(SettingsTarget.InterfaceStyle, "Stile interfaccia", "Impostazioni > Aspetto"),
            SettingsSearchDocument(SettingsTarget.ReduceMotion, "Riduci movimento", "Impostazioni > Accessibilità"),
        ))
        assertEquals(listOf(SettingsTarget.InterfaceStyle), translated.search("UI style", true))
        assertEquals(listOf(SettingsTarget.ReduceMotion), translated.search("reduce motion", true))
        assertEquals(listOf(SettingsTarget.ReduceMotion), translated.search("riduci movimento", true))
    }

    @Test
    fun normalizesCaseAccentsWhitespaceAndPunctuation() {
        assertEquals(index.search("modalita scura", true), index.search("  MODALITÀ -- SCURA!  ", true))
        assertEquals(listOf(SettingsTarget.Appearance), index.search("modalita scura", true))
        assertEquals(index.search("accessibilita", true), index.search("accessibilita\u0300", true))
        assertTrue(index.search("accessibilita", true).containsAll(listOf(SettingsTarget.ReduceMotion, SettingsTarget.ReduceTransparency)))
        assertEquals(listOf(SettingsTarget.CrossfadeDuration), index.search("\tMS,\n", true))
    }

    @Test
    fun everyTokenMustMatchIncludingTokensFromDifferentFields() {
        val scoped = SettingsSearchIndex(listOf(
            SettingsSearchDocument(SettingsTarget.CrossfadeDuration, "Custom duration", "Settings > Playback"),
            SettingsSearchDocument(SettingsTarget.MinimumDuration, "Minimum duration", "Settings > Library"),
        ))
        assertEquals(listOf(SettingsTarget.CrossfadeDuration), scoped.search("playback ms", true))
        assertEquals(listOf(SettingsTarget.MinimumDuration), scoped.search("library duration", true))
        assertTrue(scoped.search("library ms", true).isEmpty())
        assertTrue(scoped.search("ms nonexistent", true).isEmpty())
        assertEquals(scoped.search("playback ms", true), scoped.search("ms playback ms", true))
    }

    @Test
    fun exactLocalizedAndBilingualLabelsRankBeforePrefixAliasAndPathMatches() {
        val ranked = SettingsSearchIndex(listOf(
            SettingsSearchDocument(SettingsTarget.About, "About", "Settings > Crossfade"),
            SettingsSearchDocument(SettingsTarget.CrossfadeDuration, "Crossfade duration", "Settings > Playback"),
            SettingsSearchDocument(SettingsTarget.Crossfade, "Transizione", "Settings > Playback"),
        ))
        assertEquals(listOf(SettingsTarget.Crossfade, SettingsTarget.CrossfadeDuration, SettingsTarget.About), ranked.search("crossfade", true))
        assertEquals(SettingsTarget.Crossfade, ranked.search("transizione", true).first())
        assertEquals(SettingsTarget.CrossfadeDuration, ranked.search("crossfade duration", true).first())
    }

    @Test
    fun unavailableLyricsAreNeverReturnedEvenForAnExactTitleOrPath() {
        val lyrics = SettingsSearchIndex(listOf(SettingsSearchDocument(SettingsTarget.AmllLyrics, "Word timing", "Settings > Lyrics")))
        for (query in listOf("testi", "AMLL", "word timing", "settings lyrics")) {
            assertTrue(lyrics.search(query, false).isEmpty(), query)
            assertEquals(listOf(SettingsTarget.AmllLyrics), lyrics.search(query, true), query)
        }
        assertFalse(SettingsTarget.AmllLyrics in index.search("settings", false))
    }

    @Test
    fun blankPunctuationAndUnknownQueriesReturnNoResults() {
        for (query in listOf("", "   \t\n", "... -- / > !", "\u0300", "absolutely nonexistent preference")) {
            assertTrue(index.search(query, true).isEmpty(), query)
        }
        assertTrue(SettingsSearchIndex(emptyList()).search("crossfade", true).isEmpty())
    }

    @Test
    fun duplicateDocumentsProduceOneResultButKeepTheirSearchableMetadata() {
        val duplicated = SettingsSearchIndex(listOf(
            SettingsSearchDocument(SettingsTarget.Crossfade, "Transition", "Settings > Playback"),
            SettingsSearchDocument(SettingsTarget.Crossfade, "Dissolvenza", "Impostazioni > Riproduzione"),
            SettingsSearchDocument(SettingsTarget.CrossfadeDuration, "Custom duration", "Settings > Playback"),
        ))
        assertEquals(listOf(SettingsTarget.Crossfade), duplicated.search("dissolvenza", true))
        assertEquals(listOf(SettingsTarget.Crossfade), duplicated.search("riproduzione", true))
        assertEquals(listOf(SettingsTarget.Crossfade, SettingsTarget.CrossfadeDuration), duplicated.search("crossfade", true))
    }

    @Test
    fun tiesRetainRegistrationOrderAndRepeatedSearchesAreDeterministic() {
        val stable = SettingsSearchIndex(listOf(
            SettingsSearchDocument(SettingsTarget.About, "Overview", "Settings > Common"),
            SettingsSearchDocument(SettingsTarget.MinimumDuration, "Overview", "Settings > Common"),
        ))
        val expected = listOf(SettingsTarget.About, SettingsTarget.MinimumDuration)
        assertEquals(expected, stable.search("common", true))
        repeat(3) { assertEquals(expected, stable.search("overview", true)) }
        assertEquals(expected, stable.search("common", false))
    }

    @Test
    fun indexOwnsItsSnapshotAndOnlyReturnsRegisteredTargets() {
        val source = mutableListOf(SettingsSearchDocument(SettingsTarget.About, "About", "Settings"))
        val snapshot = SettingsSearchIndex(source)
        source.clear()
        assertEquals(listOf(SettingsTarget.About), snapshot.search("privacy", true))
        assertTrue(snapshot.search("crossfade", true).isEmpty())
    }

    @Test
    fun shortUnitQueriesDoNotMatchInsideUnrelatedWords() {
        val unit = SettingsSearchIndex(listOf(
            SettingsSearchDocument(SettingsTarget.About, "Forms", "Settings"),
            SettingsSearchDocument(SettingsTarget.CrossfadeDuration, "Duration", "Settings"),
        ))
        assertEquals(listOf(SettingsTarget.CrossfadeDuration), unit.search("ms", true))
    }
}
