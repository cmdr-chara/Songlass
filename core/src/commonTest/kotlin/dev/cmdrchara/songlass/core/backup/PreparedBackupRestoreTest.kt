package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.history.ListeningHistory
import dev.cmdrchara.songlass.core.library.Favorites
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.settings.AppearanceMode
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import dev.cmdrchara.songlass.core.storage.RestoreMutationGate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreparedBackupRestoreTest {
    private val tracks = listOf(song("old"), song("restored"), song("new"))

    private fun backup(appearance: AppearanceMode = AppearanceMode.Light): String {
        val source = InMemoryKeyValueStore()
        Favorites(source).toggle("restored")
        AppSettings(source).setAppearance(appearance)
        return DataBackup(source) { 100L }.export(tracks).text
    }

    @Test fun favoritesCallbackBetweenCommitAndPublicationCannotEraseTheReviewedReplacement() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val favorites = Favorites(store, gate).apply { toggle("old") }
        val settings = AppSettings(store, gate)
        val text = backup()
        val review = DataBackup(store).preview(text, tracks)
        val before = store.snapshot()
        gate.restoring {
            val staged = PreparedBackupRestore(store, review, DataBackup(store).preview(text, tracks))
            val publishFavorites = favorites.prepareReload(staged.store)
            val publishSettings = settings.prepareReload(staged.store)
            assertEquals(before, store.snapshot())
            assertEquals(listOf("old"), favorites.ids.value)
            staged.commit()
            assertFalse(favorites.toggle("new"))
            assertEquals(listOf("old"), favorites.ids.value)
            assertEquals("restored", store.getString("favorites"))
            publishFavorites()
            publishSettings()
        }
        assertEquals(listOf("restored"), favorites.ids.value)
        assertEquals(AppearanceMode.Light, settings.state.value.appearance)
        favorites.toggle("new")
        assertEquals(listOf("restored", "new"), Favorites(store).ids.value)
    }

    @Test fun preparationFailureLeavesStoreAndEveryPublishedRepositoryUnchangedAndAllowsRetry() = runTest {
        val store = InMemoryKeyValueStore()
        val gate = RestoreMutationGate()
        val favorites = Favorites(store, gate).apply { toggle("old") }
        val settings = AppSettings(store, gate)
        val before = store.snapshot()
        val oldSettings = settings.state.value
        val text = backup()
        val review = DataBackup(store).preview(text, tracks)
        assertFailsWith<IllegalStateException> {
            gate.restoring {
                val staged = PreparedBackupRestore(store, review, review)
                favorites.prepareReload(staged.store)
                // Simulate a decoder/read failure after one repository was already prepared.
                val unreadable = object : KeyValueStore by staged.store {
                    override fun getString(key: String): String? = error("Injected preparation failure")
                }
                settings.prepareReload(unreadable)
                error("Preparation must fail before commit")
            }
        }
        assertEquals(before, store.snapshot())
        assertEquals(listOf("old"), favorites.ids.value)
        assertEquals(oldSettings, settings.state.value)
        assertTrue(gate.canMutate)
        favorites.toggle("new")
        assertEquals(listOf("old", "new"), favorites.ids.value)
    }

    @Test fun failedRealCommitRollsBackAndDoesNotPublishAnyPreparedState() = runTest {
        val backing = InMemoryKeyValueStore()
        Favorites(backing).toggle("old")
        AppSettings(backing).setAppearance(AppearanceMode.Dark)
        val store = FailingStore(backing, failOnPut = 2)
        val gate = RestoreMutationGate()
        val favorites = Favorites(store, gate)
        val settings = AppSettings(store, gate)
        val previous = backing.snapshot()
        val previousSettings = settings.state.value
        val text = backup()
        val review = DataBackup(store).preview(text, tracks)
        val failure = assertFailsWith<BackupException> {
            gate.restoring {
                val staged = PreparedBackupRestore(store, review, review)
                val publish = listOf(favorites.prepareReload(staged.store), settings.prepareReload(staged.store))
                staged.commit()
                publish.forEach { it() }
            }
        }
        assertEquals(BackupError.WriteFailed, failure.error)
        assertEquals(previous, backing.snapshot())
        assertEquals(listOf("old"), favorites.ids.value)
        assertEquals(previousSettings, settings.state.value)
        assertTrue(gate.canMutate)
        gate.restoring {
            val staged = PreparedBackupRestore(store, review, review)
            val publish = listOf(favorites.prepareReload(staged.store), settings.prepareReload(staged.store))
            staged.commit()
            publish.forEach { it() }
        }
        assertEquals(listOf("restored"), favorites.ids.value)
        assertEquals(AppearanceMode.Light, settings.state.value.appearance)
    }

    @Test fun aChangedReviewedPlanNeverReachesTheRealStore() = runTest {
        val backing = InMemoryKeyValueStore()
        var snapshotCalls = 0
        val store = object : KeyValueStore by backing {
            override fun snapshot(): Map<String, String> { snapshotCalls++; return backing.snapshot() }
        }
        val gate = RestoreMutationGate()
        val favorites = Favorites(store, gate).apply { toggle("old") }
        val before = backing.snapshot()
        val reviewed = DataBackup(store).preview(backup(AppearanceMode.Light), tracks)
        val changed = DataBackup(store).preview(backup(AppearanceMode.Dark), tracks)
        assertFailsWith<BackupPreviewChangedException> {
            gate.restoring { PreparedBackupRestore(store, reviewed, changed) }
        }
        assertEquals(0, snapshotCalls, "Reject a changed plan before reading the destination snapshot")
        assertEquals(before, backing.snapshot())
        assertEquals(listOf("old"), favorites.ids.value)
        assertTrue(gate.canMutate)
    }

    @Test fun unmanagedCacheAndQueueUpdatesDuringPreparationSurviveTheSingleReplacement() {
        val store = InMemoryKeyValueStore()
        store.putString("queue.snapshot", "before")
        store.putString("autoeq.cache.selected", "old cache")
        AppSettings(store).setReduceMotion(true)
        val review = DataBackup(store).preview(backup(), tracks)
        val staged = PreparedBackupRestore(store, review, review)
        store.putString("queue.snapshot", "new decoder state")
        store.putString("autoeq.cache.selected", "download completed")
        store.putString("unmanaged.new", "keep")
        staged.commit()
        assertEquals("new decoder state", store.getString("queue.snapshot"))
        assertEquals("download completed", store.getString("autoeq.cache.selected"))
        assertEquals("keep", store.getString("unmanaged.new"))
        assertEquals(null, store.getString(AppSettings.REDUCE_MOTION_KEY))
        assertEquals("restored", store.getString("favorites"))
        val after = store.snapshot()
        assertFailsWith<IllegalStateException> { staged.commit() }
        assertEquals(after, store.snapshot())
    }

    @Test fun historyNormalizationIsPreparedInMemoryAndIsPartOfTheSameRealCommit() {
        val store = InMemoryKeyValueStore()
        val history = ListeningHistory(store) { 10L }
        history.recordPlay("old", null)
        val previous = store.snapshot()
        val review = DataBackup(store).preview(backup(), tracks)
        val staged = PreparedBackupRestore(store, review, review)
        val publish = history.prepareReload(staged.store)
        assertEquals(previous, store.snapshot())
        assertEquals(1, history.state.value.totalPlays)
        assertTrue(staged.store.getString("history.tracking_started_ms") != null)
        staged.commit()
        val committed = store.snapshot()
        publish()
        assertEquals(0, history.state.value.totalPlays)
        assertEquals(committed, store.snapshot(), "Publication performs no second store write")
        assertEquals(history.state.value, ListeningHistory(store) { 10L }.state.value)
    }
}
