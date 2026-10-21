package dev.cmdrchara.songlass.core.backup

import dev.cmdrchara.songlass.core.playback.TrackMarkers
import dev.cmdrchara.songlass.core.playback.TrackMarkersCodec
import dev.cmdrchara.songlass.core.playback.TrackMarkersState
import dev.cmdrchara.songlass.core.playback.MarkerBinding
import dev.cmdrchara.songlass.core.playback.TrackSignature
import dev.cmdrchara.songlass.core.settings.AppSettings
import dev.cmdrchara.songlass.core.storage.InMemoryKeyValueStore
import dev.cmdrchara.songlass.core.storage.KeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class TrackMarkersBackupTest {
    private val original = song("11", "Music/Podcasts/Chapter 1.flac", "Chapter 1", "Narrator",
        "My Podcast", durationMs = 1_800_000L)

    @Test
    fun pointsBookmarksAndOptInRoundTripWithoutChangingV1BackupContainer() {
        val store = InMemoryKeyValueStore()
        val markers = TrackMarkers(store) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 650_000L, 600_000L, true)
            add(original, 630_000L, null)
            add(original, 720_000L, "  Second chapter  ")
        }
        AppSettings(store).apply {
            setResumePerTrack(true)
            setLongTrackThreshold(300_000L)
        }
        val exported = DataBackup(store) { NOW }.export(listOf(original))
        assertTrue(exported.text.startsWith("SONGLASS-BACKUP 1\n"))
        assertEquals(1, exported.counts.trackProgress)
        assertEquals(2, exported.counts.bookmarks)
        val target = InMemoryKeyValueStore()
        val backup = DataBackup(target)
        val preview = backup.preview(exported.text, listOf(original))
        assertEquals(1, preview.counts.trackProgress)
        assertEquals(2, preview.counts.bookmarks)
        assertTrue(target.snapshot().isEmpty())
        backup.apply(preview)
        val restored = TrackMarkers(target).apply { setLibrary(listOf(original)) }
        val expected = TrackMarkersState(
            markers.state.value.progress.map { it.copy(track = it.track.copy(binding = MarkerBinding.Canonical)) },
            markers.state.value.bookmarks.map { it.copy(track = it.track.copy(binding = MarkerBinding.Canonical)) },
        )
        assertEquals(expected, restored.state.value)
        assertEquals(650_000L, restored.progressFor(original, 300_000L))
        assertEquals(2, restored.bookmarksFor(original).size)
        val restoredSettings = AppSettings(target).state.value
        assertTrue(restoredSettings.resumePerTrack)
        assertEquals(300_000L, restoredSettings.longTrackThresholdMs)
        // Reading a v1 marker establishes target trust; later exports retain the outer v1
        // backup container and round-trip the bound marker v2 values without further changes.
        val boundText = DataBackup(target) { NOW }.export(listOf(original)).text
        assertTrue(boundText.startsWith("SONGLASS-BACKUP 1\n"))
        val another = InMemoryKeyValueStore()
        DataBackup(another).apply(DataBackup(another).preview(boundText, listOf(original)))
        assertEquals(boundText, DataBackup(another) { NOW }.export(listOf(original)).text)
    }

    @Test
    fun reindexRemapsOnlyUnambiguousStoragePathAndUsesValidatedTargetFacts() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 340_000L, 600_000L, true)
            add(original, 340_000L, "Middle")
        }
        val backupText = DataBackup(source) { NOW }.export(listOf(original)).text
        val indexed = original.copy(id = "37", uri = "content://media/external/audio/media/37")
        val destination = InMemoryKeyValueStore()
        val preview = DataBackup(destination).preview(backupText, listOf(indexed))
        assertEquals(1, preview.tracks.remapped)
        assertEquals(0, preview.droppedReferences)
        DataBackup(destination).apply(preview)
        val bookmarks = TrackMarkers(destination).apply { setLibrary(listOf(indexed)) }
        assertEquals("37", bookmarks.state.value.bookmarks.single().track.id)
        assertEquals(TrackSignature.of(indexed).copy(binding = MarkerBinding.Canonical),
            bookmarks.state.value.bookmarks.single().track)
        assertEquals(indexed, bookmarks.resolve(bookmarks.state.value.bookmarks.single().track))
        assertEquals(340_000L, bookmarks.progressFor(indexed, 600_000L))
        val duplicate = indexed.copy(id = "38", uri = "content://media/external/audio/media/38")
        bookmarks.setLibrary(listOf(indexed, duplicate))
        assertNull(bookmarks.resolve(bookmarks.state.value.bookmarks.single().track))
        assertNull(bookmarks.progressFor(indexed, 600_000L))
    }

    @Test
    fun missingAndRecycledIdsPreserveUnopenableRecordsRatherThanMisbinding() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 340_000L, 600_000L, true)
            add(original, 340_000L, "Still here")
        }
        val text = DataBackup(source) { NOW }.export(listOf(original)).text
        val unrelated = song(original.id, "Music/Other/Not it.flac", "Wrong", "Someone else",
            "Different", durationMs = 900_000L)
        val target = InMemoryKeyValueStore()
        val preview = DataBackup(target).preview(text, listOf(unrelated))
        assertEquals(1, preview.tracks.conflicting)
        DataBackup(target).apply(preview)
        val markers = TrackMarkers(target).apply { setLibrary(listOf(unrelated), stableIds = true) }
        assertEquals(1, markers.state.value.bookmarks.size)
        assertEquals(1, markers.state.value.progress.size)
        assertEquals(MarkerBinding.Unresolved, markers.state.value.bookmarks.single().track.binding)
        assertEquals(MarkerBinding.Unresolved, markers.state.value.progress.single().track.binding)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
        assertNull(markers.progressFor(unrelated, 600_000L))
        markers.setLibrary(emptyList(), stableIds = true)
        assertEquals(1, markers.state.value.bookmarks.size)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
    }

    @Test
    fun conflictingMarkersSurviveReExportWithoutClaimingTheReplacementFileOrRevivingLater() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source) { NOW }.apply {
            setLibrary(listOf(original), stableIds = true)
            checkpoint(original, 340_000, 600_000, force = true)
            add(original, 340_000, "Original only")
        }
        val text = DataBackup(source) { NOW }.export(listOf(original)).text
        val replacement = original.copy(title = "Wrong recording", fileName = "replacement.flac")
        val target = InMemoryKeyValueStore()
        DataBackup(target).apply(DataBackup(target).preview(text, listOf(replacement)))
        val isolated = TrackMarkers(target).apply { setLibrary(listOf(replacement), stableIds = true) }
        val preserved = isolated.state.value
        assertEquals(original.id, preserved.bookmarks.single().track.id)
        assertEquals(original.uri, preserved.bookmarks.single().track.uri)
        assertEquals(original.path, preserved.bookmarks.single().track.path)
        assertEquals("Original only", preserved.bookmarks.single().title)
        val exported = DataBackup(target) { NOW }.export(listOf(replacement))
        assertEquals(1, exported.counts.bookmarks)
        assertEquals(1, exported.counts.trackProgress)
        assertEquals(0, exported.unidentifiedTracks)
        assertTrue(BackupFormat.read(exported.text).sources.isEmpty())

        val another = InMemoryKeyValueStore()
        val preview = DataBackup(another).preview(exported.text, listOf(original))
        assertEquals(0, preview.tracks.unchanged)
        DataBackup(another).apply(preview)
        val again = TrackMarkers(another).apply { setLibrary(listOf(original), stableIds = true) }
        assertEquals(preserved, again.state.value)
        assertNull(again.resolve(again.state.value.bookmarks.single().track))
        assertNull(again.progressFor(original, 600_000))
        assertTrue(again.bookmarksFor(original).isEmpty())
        // No synthetic ids or source records can later collide with an unrelated library entry.
        assertEquals(exported.text, DataBackup(another) { NOW }.export(listOf(original)).text)
    }

    @Test
    fun aValidatedCrossDeviceMoveBindsTargetLocationInTheRestoreTransactionWithoutAnotherCommit() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source).apply {
            setLibrary(listOf(original))
            checkpoint(original, 340_000, 600_000, force = true)
            add(original, 340_000, "Chapter")
        }
        val text = DataBackup(source).export(listOf(original)).text
        val moved = original.copy(uri = "file:///another-device/chapter.flac", folder = "Other device",
            fileName = "chapter.flac")
        val backing = InMemoryKeyValueStore()
        var transactions = 0
        val target = object : KeyValueStore by backing {
            override fun replace(removing: Set<String>, writing: Map<String, String>) {
                transactions++
                backing.replace(removing, writing)
            }
        }
        val markers = TrackMarkers(target).apply { setLibrary(listOf(moved), stableIds = true) }
        val preview = DataBackup(target).preview(text, listOf(moved))
        assertEquals(1, preview.tracks.unchanged)
        DataBackup(target).apply(preview)
        assertEquals(1, transactions)
        val committed = target.snapshot()
        markers.writesSuspended = true
        markers.reload() // Already-current library; no subsequent scan can hide an unsafe restore.
        val restored = markers.state.value.bookmarks.single()
        assertEquals(TrackSignature.of(moved).copy(binding = MarkerBinding.Canonical), restored.track)
        assertEquals(340_000L, restored.positionMs)
        assertEquals("Chapter", restored.title)
        assertEquals(moved, markers.resolve(restored.track))
        assertEquals(340_000L, markers.progressFor(moved, 600_000))
        assertEquals(1, transactions)
        assertEquals(committed, target.snapshot())
    }

    @Test
    fun aMissingCanonicalSourceLosesForeignTrustUntilItsLocalIdentityCanBeValidated() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source).apply {
            setLibrary(listOf(original), stableIds = true)
            checkpoint(original, 340_000, 600_000, force = true)
            add(original, 340_000, "Chapter")
        }
        val text = DataBackup(source).export(listOf(original)).text
        val target = InMemoryKeyValueStore()
        val preview = DataBackup(target).preview(text, emptyList())
        assertEquals(1, preview.tracks.missing)
        DataBackup(target).apply(preview)
        val markers = TrackMarkers(target).apply { setLibrary(emptyList(), stableIds = true) }
        assertEquals(MarkerBinding.Legacy, markers.state.value.bookmarks.single().track.binding)
        assertEquals(TrackSignature.of(original), markers.state.value.bookmarks.single().track)
        assertNull(markers.resolve(markers.state.value.bookmarks.single().track))
        markers.setLibrary(listOf(original), stableIds = true)
        assertEquals(MarkerBinding.Canonical, markers.state.value.bookmarks.single().track.binding)
        assertEquals(340_000L, markers.progressFor(original, 600_000))
    }

    @Test
    fun legacyMarkerFactsCannotBorrowTheIdentityOfAnExportedReplacementAtTheSameId() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source).apply {
            setLibrary(listOf(original))
            checkpoint(original, 340_000, 600_000, force = true)
            add(original, 340_000, "Old file")
        }
        val replacement = original.copy(folder = "Different file with identical tags")
        // An old v1 record and an occupied raw id may already coexist before the upgrade.
        val text = DataBackup(source).export(listOf(replacement)).text
        val target = InMemoryKeyValueStore()
        val preview = DataBackup(target).preview(text, listOf(replacement))
        assertEquals(1, preview.tracks.unchanged) // Source-to-target match alone is insufficient.
        DataBackup(target).apply(preview)
        val markers = TrackMarkers(target).apply { setLibrary(listOf(replacement), stableIds = true) }
        assertEquals(MarkerBinding.Unresolved, markers.state.value.bookmarks.single().track.binding)
        assertEquals(original.path, markers.state.value.bookmarks.single().track.path)
        assertNull(markers.progressFor(replacement, 600_000))
    }

    @Test
    fun aCanonicalSourceCanExportAfterVerifiedTagEditsWithoutTreatingOldDisplayTagsAsAConflict() {
        val source = InMemoryKeyValueStore()
        val markers = TrackMarkers(source).apply {
            setLibrary(listOf(original), stableIds = true)
            checkpoint(original, 340_000, 600_000, force = true)
            add(original, 340_000, "Chapter")
        }
        val edited = original.copy(title = "Edited", artist = "Edited artist", album = "Edited album")
        markers.setLibrary(listOf(edited), stableIds = true)
        val text = DataBackup(source).export(listOf(edited)).text
        val target = InMemoryKeyValueStore()
        DataBackup(target).apply(DataBackup(target).preview(text, listOf(edited)))
        val restored = TrackMarkers(target).apply { setLibrary(listOf(edited), stableIds = true) }
        assertEquals(TrackSignature.of(edited).copy(binding = MarkerBinding.Canonical),
            restored.state.value.bookmarks.single().track)
        assertEquals(340_000L, restored.progressFor(edited, 600_000))
    }

    @Test
    fun canonicalFlagsWithoutSourceIdentityCannotAuthorizeAnOccupiedTargetId() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source).apply {
            setLibrary(listOf(original), stableIds = true)
            add(original, 340_000, "Unverified source")
        }
        val text = handBuilt(listOf(TrackMarkersCodec.BOOKMARKS_KEY to
            requireNotNull(source.getString(TrackMarkersCodec.BOOKMARKS_KEY))))
        val target = InMemoryKeyValueStore()
        DataBackup(target).apply(DataBackup(target).preview(text, listOf(original)))
        val restored = TrackMarkers(target).apply { setLibrary(listOf(original), stableIds = true) }
        assertEquals(MarkerBinding.Unresolved, restored.state.value.bookmarks.single().track.binding)
        assertNull(restored.resolve(restored.state.value.bookmarks.single().track))
    }

    @Test
    fun aMatchedEndpointIsClampedToTheSlightlyShorterTargetWithoutInvalidatingTheBackup() {
        val source = InMemoryKeyValueStore()
        TrackMarkers(source).apply {
            setLibrary(listOf(original), stableIds = true)
            add(original, original.durationMs, "Ending")
        }
        val text = DataBackup(source).export(listOf(original)).text
        val shorter = original.copy(durationMs = original.durationMs - 1_000)
        val target = InMemoryKeyValueStore()
        DataBackup(target).apply(DataBackup(target).preview(text, listOf(shorter)))
        val restored = TrackMarkers(target).apply { setLibrary(listOf(shorter), stableIds = true) }
        val bookmark = restored.state.value.bookmarks.single()
        assertEquals(shorter.durationMs, bookmark.positionMs)
        assertEquals("Ending", bookmark.title)
        assertEquals(shorter, restored.resolve(bookmark.track))
    }

    @Test
    fun invalidMarkerRecordsAreRejectedBeforeRestoreChangesAnyKey() {
        val valid = InMemoryKeyValueStore()
        TrackMarkers(valid) { NOW }.apply {
            setLibrary(listOf(original))
            add(original, 250_000L, "One")
        }
        val encoded = requireNotNull(valid.getString(TrackMarkersCodec.BOOKMARKS_KEY))
        val corrupted = encoded.dropLast(1)
        assertFalse(TrackMarkersCodec.canonicalBookmarks(corrupted))
        val target = InMemoryKeyValueStore().apply { putString("favorites", "still-here") }
        val badFile = handBuilt(listOf(TrackMarkersCodec.BOOKMARKS_KEY to corrupted))
        assertFailsWith<BackupException> { DataBackup(target).preview(badFile, listOf(original)) }
        assertEquals("still-here", target.getString("favorites"))
    }

    @Test
    fun oldV1FilesRemainReadableAndClearAbsentMarkerKeysOnRestore() {
        val target = InMemoryKeyValueStore()
        TrackMarkers(target) { NOW }.apply {
            setLibrary(listOf(original))
            checkpoint(original, 150_000L, 600_000L, true)
            add(original, 150_000L, "Old marker")
        }
        val legacyText = DataBackup(InMemoryKeyValueStore().apply {
            putString("settings.crossfade_duration_ms", "1250")
        }) { NOW }.export(listOf(original)).text
        val preview = DataBackup(target).preview(legacyText, listOf(original))
        assertEquals(0, preview.counts.bookmarks)
        assertEquals(0, preview.counts.trackProgress)
        DataBackup(target).apply(preview)
        assertNull(target.getString(TrackMarkersCodec.BOOKMARKS_KEY))
        assertNull(target.getString(TrackMarkersCodec.PROGRESS_KEY))
        assertEquals("1250", target.getString("settings.crossfade_duration_ms"))
        assertFalse(AppSettings(target).state.value.resumePerTrack)
    }
}
