package dev.cmdrchara.songlass.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettingsSearchTest {
    @Test
    fun libraryRulesAndLayoutAreSearchableWithoutLosingExistingLibraryControls() {
        for (query in listOf("play count rule", "regola conteggio ascolti", "count a play after", "statistiche")) {
            assertEquals(listOf(SettingsTarget.PlayCountRule), index.search(query, true), query)
        }
        // "Album art" and "Cover layout" also make ArtworkStyle a legitimate secondary hit.
        assertEquals(listOf(SettingsTarget.AlbumLayout, SettingsTarget.ArtworkStyle), index.search("album layout", true))
        for (query in listOf("vista album", "grid", "griglia", "densità")) {
            assertEquals(listOf(SettingsTarget.AlbumLayout), index.search(query, true), query)
        }
        assertEquals(listOf(SettingsTarget.MinimumDuration), index.search("durata minima", true))
        assertEquals(listOf(SettingsTarget.ExcludedFolders), index.search("cartelle escluse", true))
    }

    @Test
    fun longTrackSettingsHaveBilingualSearchDestinations() {
        val documents = SettingsTarget.entries.map { SettingsSearchDocument(it, "Control ${it.name}", "Settings") }
        val index = SettingsSearchIndex(documents)
        assertEquals(listOf(SettingsTarget.ResumePerTrack), index.search("resume each track", true))
        assertEquals(listOf(SettingsTarget.ResumePerTrack), index.search("riprendi ogni brano", true))
        assertEquals(listOf(SettingsTarget.LongTrackThreshold), index.search("long track threshold", true))
        assertEquals(listOf(SettingsTarget.LongTrackThreshold), index.search("soglia brani lunghi", true))
    }

    @Test
    fun sleepTimerIsSearchableInBothLanguages() {
        val documents = SettingsTarget.entries.map { SettingsSearchDocument(it, "Control ${it.name}", "Settings") }
        val index = SettingsSearchIndex(documents)
        for (query in listOf("sleep timer", "timer di spegnimento", "timer", "spegnimento", "fine brano")) {
            assertEquals(SettingsTarget.SleepTimer, index.search(query, true).first(), query)
        }
    }

    @Test
    fun queueAndShuffleSettingsAreSearchableInBothLanguages() {
        val documents = SettingsTarget.entries.map { SettingsSearchDocument(it, "Control ${it.name}", "Settings") }
        val index = SettingsSearchIndex(documents)
        assertEquals(SettingsTarget.TrackClickAction, index.search("azione al tocco", true).first())
        assertEquals(SettingsTarget.TrackClickAction, index.search("tap action", true).first())
        assertEquals(SettingsTarget.QueueStart, index.search("quando partono le aggiunte", true).first())
        assertEquals(SettingsTarget.QueueEnd, index.search("fine delle aggiunte", true).first())
        assertEquals(SettingsTarget.QueueAddOrder, index.search("order of additions", true).first())
        assertEquals(SettingsTarget.QueueReplaces, index.search("sostituisci le aggiunte", true).first())
        assertEquals(SettingsTarget.ShuffleBias, index.search("meno ascoltati", true).first())
        assertEquals(SettingsTarget.KeepShuffle, index.search("no reshuffle", true).first())
        assertTrue(SettingsTarget.ShuffleBias in index.search("shuffle", true))
        assertTrue(SettingsTarget.KeepShuffle in index.search("shuffle", true))
        // The queue words must not hijack the existing settings.
        assertEquals(SettingsTarget.SleepTimer, index.search("fine brano", true).first())
    }

    private val documents = SettingsTarget.entries.map { target ->
        SettingsSearchDocument(target, "Control ${target.name}", "Settings")
    }
    private val index = SettingsSearchIndex(documents)

    @Test
    fun textSizeAndBlurIntensityAreSearchableInBothLanguagesWithoutHidingAccessibility() {
        for (query in listOf("text size", "app text size", "dimensione del testo", "testo grande", "carattere")) {
            assertEquals(listOf(SettingsTarget.TextScale), index.search(query, true), query)
        }
        // "font" also prefixes the Italian "fonti" of other settings: TextScale still ranks first.
        assertEquals(SettingsTarget.TextScale, index.search("font", true).first())
        assertEquals(SettingsTarget.TextScale, index.search("dimensione", true).first())
        assertEquals(SettingsTarget.TextScale, index.search("ingrandisci", true).first())
        for (query in listOf("background blur", "blur intensity", "blur", "sfocatura", "sfocatura dello sfondo", "intensità della sfocatura")) {
            assertTrue(SettingsTarget.BlurIntensity in index.search(query, true), query)
        }
        assertEquals(SettingsTarget.BlurIntensity, index.search("background blur", true).first())
        assertEquals(SettingsTarget.BlurIntensity, index.search("blur", true).first())
        assertEquals(SettingsTarget.BlurIntensity, index.search("sfocatura", true).first())
        // The new words must not take over the accessibility controls they share synonyms with.
        assertEquals(listOf(SettingsTarget.ReduceTransparency), index.search("reduce transparency", true))
        assertEquals(listOf(SettingsTarget.ReduceMotion), index.search("reduce motion", true))
        assertTrue(SettingsTarget.ReduceTransparency in index.search("vetro", true))
        assertEquals(setOf(SettingsTarget.ReduceTransparency, SettingsTarget.BlurIntensity), index.search("vetro", true).toSet())
    }

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
        assertEquals(listOf(SettingsTarget.CrossfadeDuration, SettingsTarget.ManualCrossfadeDuration, SettingsTarget.PauseFadeDuration, SettingsTarget.StopFadeDuration, SettingsTarget.SeekFadeDuration), index.search("\tMS,\n", true))
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
    fun automaticAndManualDurationsCanBeFoundIndependently() {
        for (query in listOf("manual crossfade", "manuale ms", "skip", "cambio manuale")) {
            assertEquals(listOf(SettingsTarget.ManualCrossfadeDuration), index.search(query, true), query)
        }
        assertEquals(SettingsTarget.Crossfade, index.search("automatic crossfade", true).first())
        assertEquals(listOf(SettingsTarget.CrossfadeDuration), index.search("automatico ms", true))
        assertEquals(listOf(SettingsTarget.CrossfadeDuration, SettingsTarget.ManualCrossfadeDuration, SettingsTarget.PauseFadeDuration, SettingsTarget.StopFadeDuration, SettingsTarget.SeekFadeDuration), index.search("milliseconds", true))
    }

    @Test
    fun unsupportedOverlapControlsAreExcludedWithoutHidingOtherPlaybackSettings() {
        for (query in listOf("crossfade", "ms", "manual crossfade", "gapless", "automatico")) {
            val results = index.search(query, supportsAmllLyrics = true, supportsCrossfade = false)
            assertFalse(SettingsTarget.Crossfade in results, query)
            assertFalse(SettingsTarget.CrossfadeDuration in results, query)
            assertFalse(SettingsTarget.ManualCrossfadeDuration in results, query)
        }
        assertEquals(listOf(SettingsTarget.ReplayGain, SettingsTarget.ReplayGainProcessing), index.search("normalizzazione", true, false))
        assertEquals(listOf(SettingsTarget.ResumeOnReconnect, SettingsTarget.ResumeBluetooth), index.search("resume on reconnect", true, false))
        assertEquals(listOf(SettingsTarget.ManualCrossfadeDuration), index.search("manual crossfade", true, true))
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

    @Test
    fun lastFmIsSearchableInBothLanguagesOnlyWhereTheFeatureExists() {
        for (query in listOf("last.fm", "lastfm", "scrobbling", "scrobbler", "last fm", "collega", "sessione")) {
            assertTrue(SettingsTarget.LastFm in index.search(query, true), query)
        }
        assertEquals(listOf(SettingsTarget.LastFm), index.search("last.fm", true))
        assertEquals(listOf(SettingsTarget.LastFm), index.search("scrobbling", true))
        // A platform without a tested network engine hides the whole destination.
        assertTrue(index.search("last.fm", true, supportsScrobbling = false).isEmpty())
        assertTrue(index.search("last.fm", true).contains(SettingsTarget.LastFm))
    }

    @Test
    fun collectionResumeExceptionsAreSearchableWithoutHijackingTheGlobalResumeSettings() {
        assertEquals(SettingsTarget.ResumeOverrides, index.search("resume by collection", true).first())
        assertEquals(SettingsTarget.ResumeOverrides, index.search("ripresa per raccolta", true).first())
        assertTrue(SettingsTarget.ResumeOverrides in index.search("eccezioni", true))
        assertTrue(SettingsTarget.ResumeOverrides in index.search("cartella", true))
        assertTrue(SettingsTarget.ResumeOverrides in index.search("playlist", true))
        // The global per-track resume and its threshold keep their own exact results.
        assertEquals(listOf(SettingsTarget.ResumePerTrack), index.search("resume each track", true))
        assertEquals(listOf(SettingsTarget.LongTrackThreshold), index.search("soglia brani lunghi", true))
        assertEquals(listOf(SettingsTarget.LongTrackThreshold), index.search("long track threshold", true))
        assertEquals(SettingsTarget.ResumePerTrack, index.search("riprendi ogni brano", true).first())
    }
}
