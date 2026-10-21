package dev.cmdrchara.songlass.core.library

import dev.cmdrchara.songlass.core.backup.BackupKeys
import dev.cmdrchara.songlass.core.backup.DataBackup
import dev.cmdrchara.songlass.core.model.Track
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrackIdentityRegistryTest {
    @Test
    fun firstScanSeedsLegacyIdsWithoutChangingExistingUserData() = runTest {
        val store = CountingStore()
        val historical = mapOf(
            "history.counts" to "7\u001e12\u001e1234",
            "playlists.example.tracks" to "7\u001fmissing-legacy-id",
            "favorites" to "7",
        )
        historical.forEach { (key, value) -> store.putString(key, value) }
        val input = listOf(raw("7"), raw("9", name = "second.flac"))

        assertEquals(input, TrackIdentityRegistry(store).canonicalize(input))
        historical.forEach { (key, value) -> assertEquals(value, store.getString(key)) }
        assertEquals(historical.keys + TrackIdentityRegistry.STORAGE_KEY, store.snapshot().keys)
        assertEquals(1, store.commits)
    }

    @Test
    fun reindexKeepsCanonicalIdButUsesNewPlayableUriAndMetadataSnapshot() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        val original = raw("7")
        registry.canonicalize(listOf(original))
        // MediaStore can report a new arrival time when rebuilding its index.
        val reindexed = raw("42", dateAddedMs = 9_000)

        val mapped = registry.canonicalize(listOf(reindexed)).single()
        assertEquals(reindexed.copy(id = "7"), mapped)
        assertEquals(mapped, TrackIdentityRegistry(store).canonicalize(listOf(reindexed)).single())
        assertEquals(2, store.commits)
    }

    @Test
    fun recycledIdCannotStealReferencesWhileOriginalIsReindexedInEitherScanOrder() = runTest {
        for (reverse in listOf(false, true)) {
            val store = CountingStore()
            val registry = TrackIdentityRegistry(store)
            registry.canonicalize(listOf(raw("7")))
            val original = raw("42")
            val replacement = raw("7", name = "other.flac", title = "Other", dateAddedMs = 2_000)
            val scan = listOf(original, replacement).let { if (reverse) it.reversed() else it }

            val resolved = registry.canonicalize(scan)
            assertEquals("7", resolved.single { it.uri == original.uri }.id)
            val newId = resolved.single { it.uri == replacement.uri }.id
            assertNotEquals("7", newId)
            assertNotEquals("42", newId)
            assertEquals(resolved, TrackIdentityRegistry(store).canonicalize(scan))
        }
    }

    @Test
    fun absentIdentitySurvivesReplacementAndLaterReturnsWithoutRewritingHistory() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7")))
        store.putString("history.counts", "7\u001e5\u001e100")
        assertTrue(registry.canonicalize(emptyList()).isEmpty())
        val replacement = raw("7", name = "replacement.flac", title = "Replacement", dateAddedMs = 2_000)
        val replacementId = registry.canonicalize(listOf(replacement)).single().id
        assertNotEquals("7", replacementId)

        val returned = registry.canonicalize(listOf(replacement, raw("42")))
        assertEquals(listOf(replacementId, "7"), returned.map { it.id })
        assertEquals("7\u001e5\u001e100", store.getString("history.counts"))
        assertEquals(returned, TrackIdentityRegistry(store).canonicalize(listOf(replacement, raw("42"))))
    }

    @Test
    fun emptyFirstScanStillPreventsLaterLegacyIdReuse() = runTest {
        val store = CountingStore()
        store.putString("favorites", "7")
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(emptyList())
        val later = TrackIdentityRegistry(store).canonicalize(listOf(raw("7"))).single()

        assertNotEquals("7", later.id)
        assertEquals("7", store.getString("favorites"))
        assertEquals(2, store.commits)
    }

    @Test
    fun sameFileTagEditsKeepIdentityAndBecomeTheSignatureForLaterReindex() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7")))
        val edited = raw("7").copy(title = "Correct title", artist = "Correct artist", album = "Correct album", artworkRevision = 19)

        assertEquals(edited, registry.canonicalize(listOf(edited)).single())
        val reindexed = edited.copy(id = "42", uri = uri("42"), dateAddedMs = 3_000)
        assertEquals(reindexed.copy(id = "7"), registry.canonicalize(listOf(reindexed)).single())
    }

    @Test
    fun sameLocationWithContradictoryArrivalOrDurationGetsANewIdentity() = runTest {
        for (replacement in listOf(raw("7", dateAddedMs = 2_000), raw("7", durationMs = 240_000))) {
            val store = CountingStore()
            val registry = TrackIdentityRegistry(store)
            registry.canonicalize(listOf(raw("7")))

            val mapped = registry.canonicalize(listOf(replacement)).single()
            assertNotEquals("7", mapped.id)
            assertEquals(mapped, TrackIdentityRegistry(store).canonicalize(listOf(replacement)).single())
        }
    }

    @Test
    fun ambiguousHistoricalPathIsNotAssignedToFirstCandidate() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7"), raw("8")))

        val mapped = registry.canonicalize(listOf(raw("42"))).single()
        assertTrue(mapped.id !in setOf("7", "8", "42"))
        assertEquals(mapped, TrackIdentityRegistry(store).canonicalize(listOf(raw("42"))).single())
        // Exact surviving source identity still works despite the duplicate relative path.
        assertEquals("7", registry.canonicalize(listOf(raw("7"))).single().id)
    }

    @Test
    fun duplicatePathsInNewScanDoNotCompeteForOneHistoricalId() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7")))
        val incoming = listOf(raw("42"), raw("43"))

        val mapped = registry.canonicalize(incoming)
        assertEquals(2, mapped.map { it.id }.toSet().size)
        assertTrue(mapped.none { it.id == "7" })
        assertEquals(mapped.reversed(), registry.canonicalize(incoming.reversed()))
        assertEquals(2, store.commits)
    }

    @Test
    fun reindexWithoutAPathOrKnownDurationDoesNotGuessFromSongTitle() = runTest {
        for (original in listOf(raw("7").copy(fileName = null), raw("7", durationMs = 0))) {
            val registry = TrackIdentityRegistry(CountingStore())
            registry.canonicalize(listOf(original))
            val reindexed = original.copy(id = "42", uri = uri("42"))
            assertNotEquals("7", registry.canonicalize(listOf(reindexed)).single().id)
        }
    }

    @Test
    fun ambiguousExactHistoricalOwnersFailWithoutPickingOneOrWriting() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7", durationMs = 100_000)))
        registry.canonicalize(listOf(raw("7", durationMs = 102_000)))
        val before = store.getString(TrackIdentityRegistry.STORAGE_KEY)

        // Both known owners are within the tolerance; this is insufficient to choose either.
        assertFailsWith<TrackIdentityRegistryException> { registry.canonicalize(listOf(raw("7", durationMs = 101_000))) }
        assertEquals(before, store.getString(TrackIdentityRegistry.STORAGE_KEY))
        assertEquals(2, store.commits)
    }

    @Test
    fun reorderedOrCosmeticallyUpdatedScanAndMissingRowsDoNotWrite() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        val originals = listOf(raw("7"), raw("9", name = "second.flac"))
        registry.canonicalize(originals)
        val refreshed = originals.reversed().map { it.copy(artworkRevision = 6, genre = "Rock", albumArtist = "New grouping") }

        assertEquals(refreshed, registry.canonicalize(refreshed))
        assertTrue(registry.canonicalize(emptyList()).isEmpty())
        assertEquals(originals, TrackIdentityRegistry(store).canonicalize(originals))
        assertEquals(1, store.commits)
    }

    @Test
    fun generatedIdsCannotCollideWithSeededOrCurrentRawIds() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(emptyList())
        val seeded = registry.canonicalize(listOf(raw("7", name = "seed.flac"))).single()
        val nextRawId = seeded.id.removeSuffix("-1") + "-2"
        val incoming = listOf(raw("9", name = "new.flac"), raw(nextRawId, name = "also-new.flac"))

        val ids = registry.canonicalize(incoming).map { it.id }
        assertEquals(2, ids.toSet().size)
        assertTrue(ids.none { it in setOf("7", "9", seeded.id, nextRawId) })
    }

    @Test
    fun duplicateScanIdsOrUrisAreRejectedWithoutChangingTheRegistry() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7")))
        val before = store.getString(TrackIdentityRegistry.STORAGE_KEY)
        val invalid = listOf(
            listOf(raw("7"), raw("7").copy(uri = uri("8"))),
            listOf(raw("7"), raw("8").copy(uri = uri("7"))),
            listOf(raw("bad\u001fID")),
        )

        for (scan in invalid) assertFailsWith<TrackIdentityRegistryException> { registry.canonicalize(scan) }
        assertEquals(before, store.getString(TrackIdentityRegistry.STORAGE_KEY))
        assertEquals(1, store.commits)
    }

    @Test
    fun malformedRegistryFailsClosedInsteadOfSeedingOverHistoricalData() = runTest {
        val validStore = CountingStore()
        TrackIdentityRegistry(validStore).canonicalize(listOf(raw("7")))
        val valid = validStore.getString(TrackIdentityRegistry.STORAGE_KEY)!!
        val row = listOf("7", "7", uri("7"), "Music/first.flac", "First", "Artist", "Album", "180000", "1000")
        val namespace = "0".repeat(32)
        val malformed = listOf(
            "", valid.replaceFirst("v1|", "v2|"), valid.dropLast(1), valid + "trailing",
            encoded(listOf("bad-namespace", "1", "0")),
            encoded(listOf(namespace, "01", "0")), encoded(listOf(namespace, "1", "100001")),
            encoded(listOf(namespace, "1", "2") + row + row),
            encoded(listOf(namespace, "1", "1") + row.toMutableList().also { it[7] = "-1" }),
            encoded(listOf(namespace, "1", "1") + row.toMutableList().also { it[2] = "x".repeat(4_097) }),
            "v1|1:11:1-2:",
        )
        for (bad in malformed) {
            val store = CountingStore()
            store.putString(TrackIdentityRegistry.STORAGE_KEY, bad)
            store.putString("favorites", "7")

            assertFailsWith<TrackIdentityRegistryException> { TrackIdentityRegistry(store).canonicalize(listOf(raw("7"))) }
            assertEquals(bad, store.getString(TrackIdentityRegistry.STORAGE_KEY))
            assertEquals("7", store.getString("favorites"))
            assertEquals(0, store.commits)
        }
    }

    @Test
    fun failedCommitPublishesNothingAndDoesNotConsumeAnIdentity() = runTest {
        val store = CountingStore()
        val registry = TrackIdentityRegistry(store)
        registry.canonicalize(listOf(raw("7")))
        val before = store.getString(TrackIdentityRegistry.STORAGE_KEY)
        val replacement = raw("7", name = "other.flac", title = "Other", dateAddedMs = 2_000)
        store.rejectWrites = true

        assertFailsWith<RejectedCommit> { registry.canonicalize(listOf(replacement)) }
        assertEquals(before, store.getString(TrackIdentityRegistry.STORAGE_KEY))
        assertEquals("7", TrackIdentityRegistry(store).canonicalize(listOf(raw("7"))).single().id)
        store.rejectWrites = false
        assertTrue(registry.canonicalize(listOf(replacement)).single().id.endsWith("-1"))
        assertEquals(2, store.commits)
    }

    @Test
    fun firstRegistrationMustAlsoBeDurablyCommittedBeforeReturning() = runTest {
        val store = CountingStore().apply { rejectWrites = true }
        val registry = TrackIdentityRegistry(store)
        assertFailsWith<RejectedCommit> { registry.canonicalize(listOf(raw("7"))) }
        assertNull(store.getString(TrackIdentityRegistry.STORAGE_KEY))
        store.rejectWrites = false
        assertEquals("7", registry.canonicalize(listOf(raw("7"))).single().id)
    }

    @Test
    fun registryIsExcludedFromExplicitBackupNamespaces() = runTest {
        val store = CountingStore()
        TrackIdentityRegistry(store).canonicalize(listOf(raw("7")))
        store.putString("favorites", "7")

        assertNull(BackupKeys.kindOf(TrackIdentityRegistry.STORAGE_KEY))
        assertEquals(mapOf("favorites" to "7"), BackupKeys.exportable(store.snapshot()))
    }

    @Test
    fun importedMissingIdsFromAnotherRegistryNeverAliasItsFirstNewFile() = runTest {
        val source = CountingStore()
        val sourceRegistry = TrackIdentityRegistry(source, Random(1))
        sourceRegistry.canonicalize(emptyList())
        val saved = sourceRegistry.canonicalize(listOf(raw("7"))).single()
        source.putString("favorites", saved.id)
        val backupText = DataBackup(source).export(listOf(saved)).text
        val target = CountingStore()
        val targetRegistry = TrackIdentityRegistry(target, Random(2))
        targetRegistry.canonicalize(emptyList())
        val targetRegistryBefore = target.getString(TrackIdentityRegistry.STORAGE_KEY)
        val backup = DataBackup(target)
        val preview = backup.preview(backupText, emptyList())
        assertEquals(1, preview.tracks.missing)
        backup.apply(preview)
        assertEquals(targetRegistryBefore, target.getString(TrackIdentityRegistry.STORAGE_KEY))

        val unrelated = targetRegistry.canonicalize(listOf(raw("7", name = "other.flac", title = "Other"))).single()
        assertNotEquals(saved.id, unrelated.id)
        assertEquals(saved.id, target.getString("favorites"))
    }

    @Test
    fun explicitBackupStillRemapsKnownFilesToTheTargetCanonicalIds() = runTest {
        val source = CountingStore()
        val sourceRegistry = TrackIdentityRegistry(source, Random(1))
        sourceRegistry.canonicalize(emptyList())
        val saved = sourceRegistry.canonicalize(listOf(raw("7"))).single()
        source.putString("favorites", saved.id)
        val target = CountingStore()
        val targetRegistry = TrackIdentityRegistry(target, Random(2))
        targetRegistry.canonicalize(emptyList())
        val existing = targetRegistry.canonicalize(listOf(raw("42"))).single()
        val targetRegistryBefore = target.getString(TrackIdentityRegistry.STORAGE_KEY)
        val backup = DataBackup(target)
        val preview = backup.preview(DataBackup(source).export(listOf(saved)).text, listOf(existing))
        assertEquals(1, preview.tracks.remapped)

        backup.apply(preview)
        assertEquals(existing.id, target.getString("favorites"))
        assertEquals(targetRegistryBefore, target.getString(TrackIdentityRegistry.STORAGE_KEY))
        assertEquals(existing, TrackIdentityRegistry(target).canonicalize(listOf(raw("42"))).single())
    }

    private fun raw(
        id: String,
        name: String = "first.flac",
        title: String = "First",
        durationMs: Long = 180_000,
        dateAddedMs: Long = 1_000,
    ) = Track(id, title, uri(id), "Artist", "Album", durationMs = durationMs, folder = "Music", fileName = name, dateAddedMs = dateAddedMs)

    private fun uri(id: String): String = "content://media/external/audio/media/$id"

    private fun encoded(fields: List<String?>): String = buildString {
        append("v1|")
        fields.forEach { value -> if (value == null) append("-1:") else { append(value.length); append(':'); append(value) } }
    }

    private class RejectedCommit : RuntimeException()

    private class CountingStore : KeyValueStore {
        private val delegate = InMemoryKeyValueStore()
        var commits = 0
            private set
        var rejectWrites = false
        override fun getString(key: String): String? = delegate.getString(key)
        override fun putString(key: String, value: String?) = delegate.putString(key, value)
        override fun snapshot(): Map<String, String> = delegate.snapshot()
        override fun replace(removing: Set<String>, writing: Map<String, String>) {
            if (rejectWrites) throw RejectedCommit()
            delegate.replace(removing, writing)
            commits++
        }
    }
}
