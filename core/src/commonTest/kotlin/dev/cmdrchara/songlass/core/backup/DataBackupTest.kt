package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.equalizer.EqOutput
import dev.cmdrchara.songlass.core.equalizer.Equalizer
import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.history.PlayContext
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.library.Pins
import dev.cmdrchara.songlass.core.lyrics.LyricsRepository
import dev.cmdrchara.songlass.core.lyrics.LyricsResult
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.playlists.SmartPlayStatus
import dev.cmdrchara.songlass.core.playlists.SmartPlaylists
import dev.cmdrchara.songlass.core.playlists.SmartRules
import dev.cmdrchara.songlass.core.playlists.UserPlaylists
import dev.cmdrchara.songlass.core.search.SearchHistory
import dev.cmdrchara.songlass.core.search.SearchTarget
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DataBackupTest {
    private val library = listOf(song("1"), song("2"), song("3"), song("4"))
    private val playlistName = "Mix: 12:34 \u001F\u001E\n😀"
    private val parametricEq = "Preamp: -3 dB\nFilter 1: ON PK Fc 100 Hz Gain 2.5 dB Q 1.0\nFilter 2: ON HSC Fc 8000 Hz Gain -3 dB Q 0.7"

    @Test
    fun independentCrossfadeDurationsRoundTripAndLegacyBackupRestoresManualOff() {
        val source = InMemoryKeyValueStore()
        val sourceSettings = AppSettings(source).apply {
            setCrossfadeDuration(6_000L)
            setManualCrossfadeDuration(1_250L)
        }
        val target = InMemoryKeyValueStore()
        val targetSettings = AppSettings(target)
        val backup = DataBackup(target)
        val preview = backup.preview(DataBackup(source) { NOW }.export(library).text, library)
        assertTrue(target.snapshot().isEmpty(), "preview must not apply audio preferences")
        backup.apply(preview)
        targetSettings.reload()
        assertEquals(sourceSettings.state.value, targetSettings.state.value)

        val legacy = InMemoryKeyValueStore().apply { putString("settings.crossfade_duration_ms", "1250") }
        backup.apply(backup.preview(DataBackup(legacy) { NOW }.export(library).text, library))
        targetSettings.reload()
        assertEquals(1_250L, targetSettings.state.value.crossfadeDurationMs)
        assertEquals(0L, targetSettings.state.value.manualCrossfadeDurationMs)
        assertNull(target.getString("settings.manual_crossfade_duration_ms"))
    }

    /** Fills [store] through the real repositories, so the stored layouts are the app's own. */
    private fun populate(store: KeyValueStore, tracks: List<Track>) {
        Favorites(store).apply { toggle("1"); toggle("3") }
        Pins(store).apply {
            toggle(PlayContext.Album("Album", "Artist"))
            toggle(PlayContext.Playlist("Music/Chill"))
            toggle(PlayContext.Artist(null))
        }
        ListeningHistory(store) { NOW }.apply {
            recordPlay("1", PlayContext.Favorites)
            recordPlay("1", null)
            recordPlay("3", PlayContext.Album("Album", null))
        }
        UserPlaylists(store) { NOW }.apply {
            create("Road trip", listOf("1", "2", "3"))
            create(playlistName, listOf("3"))
        }
        SmartPlaylists(store) { NOW }.save(null, "Unplayed", SmartRules(playStatus = SmartPlayStatus.Never, artist = ODD, limit = 25))
        SearchHistory(store).apply {
            record(SearchTarget.Song("2"))
            record(SearchTarget.AlbumId("Album", null))
            record(SearchTarget.ArtistName(ODD))
            record(SearchTarget.FolderId("Music/Chill"))
        }
        Equalizer(store) { NOW }.apply {
            setGain(0, 3.0)
            importProfile("HD 600 ParametricEQ.txt", parametricEq)
            setOutput(EqOutput("wired:headphones", "Headphones"))
            saveForCurrentOutput()
            setAutomaticOutputs(true)
        }
        assertTrue(LyricsRepository(store = store).import(tracks[0], "[00:01.00]Hello\n[00:02.00]World"))
        store.putString("settings.appearance", "dark")
        store.putString("library.minimum_duration_ms", "30000")
        store.putString("home.sections", ODD)
    }

    @Test
    fun everyKindOfDataRoundTripsIntoAFreshStore() = runTest {
        val source = InMemoryKeyValueStore()
        populate(source, library)
        val export = DataBackup(source) { NOW }.export(library)
        assertEquals(
            BackupCounts(
                favorites = 2, pins = 3, userPlaylists = 2, smartPlaylists = 1, playedTracks = 2, datedPlays = 3,
                searchItems = 4, headphoneProfiles = 1, outputProfiles = 1, importedLyrics = 1, settings = 2, homeEntries = 1,
            ),
            export.counts,
        )
        assertEquals(0, export.omittedLyrics)

        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target) { NOW }
        val preview = backup.preview(export.text, library)
        assertEquals(export.counts, preview.counts)
        assertEquals(NOW, preview.createdMs)
        assertEquals(BackupTrackSummary(unchanged = 3, remapped = 0, missing = 0, conflicting = 0), preview.tracks)
        assertEquals(emptyList(), preview.unresolved)
        assertEquals(0, preview.droppedReferences)
        assertEquals(library.size, preview.libraryTrackCount)
        assertTrue(target.snapshot().isEmpty(), "previewing must not write")

        val result = backup.apply(preview)
        assertEquals(preview.entries.size, result.written)
        assertEquals(0, result.removed)
        assertEquals(BackupKeys.exportable(source.snapshot()), target.snapshot())

        // The repositories read the restored data exactly as they read the original.
        assertEquals(listOf("1", "3"), Favorites(target).ids.value)
        assertEquals(Pins(source).contexts.value, Pins(target).contexts.value)
        assertEquals(ListeningHistory(source) { NOW }.state.value, ListeningHistory(target) { NOW }.state.value)
        assertEquals(UserPlaylists(source) { NOW }.playlists.value, UserPlaylists(target) { NOW }.playlists.value)
        assertTrue(UserPlaylists(target) { NOW }.playlists.value.any { it.name == playlistName })
        assertEquals(SmartPlaylists(source) { NOW }.playlists.value, SmartPlaylists(target) { NOW }.playlists.value)
        assertEquals(SmartRules(playStatus = SmartPlayStatus.Never, artist = ODD, limit = 25), SmartPlaylists(target) { NOW }.playlists.value.single().rules)
        assertEquals(SearchHistory(source).targets.value, SearchHistory(target).targets.value)
        assertEquals(Equalizer(source) { NOW }.state.value, Equalizer(target) { NOW }.state.value)
        assertEquals(1, Equalizer(target) { NOW }.state.value.outputProfiles.size)
        val lyrics = LyricsRepository(store = target).load(library[0])
        assertIs<LyricsResult.Found>(lyrics)
        assertEquals(LyricsRepository(store = source).load(library[0]), lyrics)
        assertEquals("dark", target.getString("settings.appearance"))
        assertEquals(ODD, target.getString("home.sections"))
    }

    @Test
    fun reloadMakesRepositoriesCreatedBeforeTheRestoreSeeTheRestoredData() = runTest {
        val source = InMemoryKeyValueStore()
        populate(source, library)
        val text = DataBackup(source) { NOW }.export(library).text

        val target = InMemoryKeyValueStore()
        val favorites = Favorites(target).also { it.toggle("9") }
        val pins = Pins(target)
        val history = ListeningHistory(target) { NOW }.also { it.recordPlay("9", null) }
        val userPlaylists = UserPlaylists(target) { NOW + 5 }.also { it.create("Old") }
        val smart = SmartPlaylists(target) { NOW + 5 }.also { it.save(null, "Old", SmartRules()) }
        val search = SearchHistory(target).also { it.record(SearchTarget.Song("9")) }
        val equalizer = Equalizer(target) { NOW + 5 }.also { it.setEnabled(true) }
        val lyrics = LyricsRepository(store = target)
        assertEquals(LyricsResult.NotFound, lyrics.load(library[0]))

        val backup = DataBackup(target) { NOW }
        backup.apply(backup.preview(text, library))
        // Until they are told, running repositories keep what they had.
        assertEquals(listOf("9"), favorites.ids.value)

        favorites.reload(); pins.reload(); history.reload(); userPlaylists.reload()
        smart.reload(); search.reload(); equalizer.reload(); lyrics.reload()

        assertEquals(listOf("1", "3"), favorites.ids.value)
        assertEquals(Pins(source).contexts.value, pins.contexts.value)
        assertEquals(ListeningHistory(source) { NOW }.state.value, history.state.value)
        assertEquals(UserPlaylists(source) { NOW }.playlists.value, userPlaylists.playlists.value)
        assertEquals(SmartPlaylists(source) { NOW }.playlists.value, smart.playlists.value)
        assertEquals(SearchHistory(source).targets.value, search.targets.value)
        assertEquals(Equalizer(source) { NOW }.state.value, equalizer.state.value)
        assertIs<LyricsResult.Found>(lyrics.load(library[0]))
    }

    @Test
    fun equalizerReloadKeepsTheOutputThatIsPlayingAndPicksItsCurveAgain() {
        val source = InMemoryKeyValueStore()
        populate(source, library)
        val headphones = EqOutput("wired:headphones", "Headphones")
        val target = InMemoryKeyValueStore()
        val equalizer = Equalizer(target) { NOW }.also { it.setOutput(headphones) }
        val backup = DataBackup(target)
        backup.apply(backup.preview(DataBackup(source) { NOW }.export(library).text, library))
        equalizer.reload()
        assertEquals(headphones, equalizer.state.value.output)
        assertTrue(equalizer.state.value.usesOutputProfile)
        assertEquals(3.0, equalizer.state.value.gains[0])
    }

    @Test
    fun unusualCharactersSurviveInValuesAndInTheTrackIdentity() {
        val odd = song("7", path = "Music/Zażółć: 日本/ça va?.mp3", title = ODD, artist = "AC/DC\n", album = null)
        val source = storeOf("favorites" to "7", "home.note" to ODD)
        val text = DataBackup(source) { NOW }.export(listOf(odd)).text

        // On a device without that file the identity is still reported intact.
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(text, emptyList())
        assertEquals(listOf(UnresolvedTrack("7", ODD, "AC/DC\n", null, odd.path, UnresolvedKind.Missing)), preview.unresolved)
        assertEquals(0, preview.libraryTrackCount)
        backup.apply(preview)
        assertEquals("7", target.getString("favorites"))
        assertEquals(ODD, target.getString("home.note"))
    }

    @Test
    fun onlyAllowlistedKeysAreExportedAndOnlyForItemsStillListed() {
        val store = storeOf(
            "favorites" to "1",
            "queue.state" to "SECRET-QUEUE",
            "autoeq.cache.index" to "SECRET-CACHE",
            "auth.token" to "SECRET-TOKEN",
            "future.pref" to "SECRET-FUTURE",
            "history.unknown" to "SECRET-HISTORY",
            "playlists.pGone.name" to "SECRET-DELETED",
            "playlists.pGone.tracks" to "1",
            "playlists.index" to "pLive",
            "playlists.pLive.name" to "Live",
            "playlists.pLive.created" to "5",
            "playlists.pLive.tracks" to "1",
        )
        val export = DataBackup(store) { NOW }.export(library)
        assertTrue("SECRET" !in export.text, "nothing outside the allowlist may leave the store")
        val keys = DataBackup(InMemoryKeyValueStore()).preview(export.text, library).entries.keys
        assertEquals(
            setOf("favorites", "playlists.index", "playlists.pLive.name", "playlists.pLive.created", "playlists.pLive.tracks"),
            keys,
        )
    }

    @Test
    fun exportKeepsOnlyTheTracksTheDataMentions() {
        val store = storeOf("favorites" to "2")
        val export = DataBackup(store) { NOW }.export(library)
        assertEquals(listOf("2"), BackupFormat.read(export.text).sources.map { it.id })
        assertEquals(0, export.unidentifiedTracks)
    }

    @Test
    fun exportWithoutALoadedLibraryReportsTracksItCouldNotIdentify() {
        val store = storeOf("favorites" to "1\u001F2", "history.counts" to "2\u001E1\u001E0")
        val export = DataBackup(store) { NOW }.export(emptyList())
        assertEquals(2, export.unidentifiedTracks)
        assertEquals(emptyList(), BackupFormat.read(export.text).sources)
    }

    @Test
    fun damagedStoredValuesAreWrittenInTheFormTheAppWouldHaveReadThem() {
        val store = storeOf(
            "history.counts" to "x\u001Enot-a-number\u001Fa\u001E2\u001E5",
            "favorites" to "1\u001F\u001F2",
            "search.opened.v1" to "1:s1:5garbage",
        )
        val preview = DataBackup(InMemoryKeyValueStore()).preview(DataBackup(store) { NOW }.export(library).text, library)
        assertEquals("a\u001E2\u001E5", preview.entries["history.counts"])
        assertEquals("1\u001F2", preview.entries["favorites"])
        assertEquals("1:s1:5", preview.entries["search.opened.v1"])
    }

    @Test
    fun restoreReplacesManagedKeysRemovesStaleOnesAndKeepsEverythingElse() {
        val source = storeOf("favorites" to "1", "settings.appearance" to "dark")
        val text = DataBackup(source) { NOW }.export(library).text
        val target = storeOf(
            "favorites" to "2",
            "settings.appearance" to "light",
            "settings.reduce_motion" to "true",
            "playlists.index" to "pOld",
            "playlists.pOld.name" to "Old",
            "future.pref" to "keep",
            "autoeq.cache.x" to "keep-cache",
        )
        val backup = DataBackup(target)
        val result = backup.apply(backup.preview(text, library))
        assertEquals("1", target.getString("favorites"))
        assertEquals("dark", target.getString("settings.appearance"))
        assertNull(target.getString("settings.reduce_motion"))
        assertNull(target.getString("playlists.index"))
        assertNull(target.getString("playlists.pOld.name"))
        assertEquals("keep", target.getString("future.pref"))
        assertEquals("keep-cache", target.getString("autoeq.cache.x"))
        assertEquals(BackupResult(written = 2, removed = 3), result)
    }

    @Test
    fun lyricsAreLeftOutRatherThanFailingWhenTheBackupWouldBeTooBig() {
        val store = storeOf("settings.appearance" to "dark")
        val bulky = "a".repeat(1024 * 1024)
        repeat(18) { store.putString("lyrics.import.content://media/$it", bulky) }
        val export = DataBackup(store) { NOW }.export(emptyList())
        assertEquals(18, export.omittedLyrics)
        assertEquals(0, export.counts.importedLyrics)
        assertEquals(1, export.counts.settings)
        assertTrue(export.text.length < 1_000)
        assertEquals(setOf("settings.appearance"), DataBackup(InMemoryKeyValueStore()).preview(export.text, emptyList()).entries.keys)
    }

    @Test
    fun aBackupThatCannotFitFailsInsteadOfBeingCutShort() {
        val store = storeOf("settings.huge" to "x".repeat(DataBackup.MAX_BYTES))
        assertEquals(BackupError.TooLarge, assertFailsWith<BackupException> { DataBackup(store).export(emptyList()) }.error)
    }

    @Test
    fun textThatCouldNotBeReadBackFromAFileIsNotExported() {
        val store = storeOf("settings.broken" to "\uD800")
        assertEquals(BackupError.Unrepresentable, assertFailsWith<BackupException> { DataBackup(store).export(emptyList()) }.error)
    }

    @Test
    fun aStoreThatCannotListItsKeysIsReportedNotSilentlyTreatedAsEmpty() {
        val plain = object : KeyValueStore {
            override fun getString(key: String): String? = null
            override fun putString(key: String, value: String?) = Unit
        }
        val valid = DataBackup(storeOf("favorites" to "1")) { NOW }.export(library).text
        val backup = DataBackup(plain)
        assertEquals(BackupError.StoreUnsupported, assertFailsWith<BackupException> { backup.export(library) }.error)
        assertEquals(BackupError.StoreUnsupported, assertFailsWith<BackupException> { backup.apply(backup.preview(valid, library)) }.error)
    }
}
