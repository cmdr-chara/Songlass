package dev.cmdrchara.songlass.core.sound

import dev.cmdrchara.songlass.core.model.Track
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class DefaultSoundPolicyTest {
    private val track = Track("opaque-registry-id", "Song", "content://media/external/audio/media/23",
        fileName = "song.flac", folder = "Music/Album", dateAddedMs = 12_000, durationMs = 40_000,
        absoluteFilePath = "/storage/emulated/0/Music/Album/song.flac")
    private val facts = DefaultSoundSourceFacts(track.uri, "song.flac", "Music/Album/", 12_000, 40_000,
        "/storage/emulated/0/Music/Album/song.flac", "row23:external_primary:version-a:generation4")

    @Test fun selectedFileRequiresStrongPathAndPreviouslyKnownFacts() {
        assertTrue(DefaultSoundPolicy.matches(track, facts))
        assertFalse(DefaultSoundPolicy.matches(track, facts.copy(canonicalPath = "/storage/emulated/0/Music/Other/song.flac")))
        assertFalse(DefaultSoundPolicy.matches(track, facts.copy(fileName = "replacement.flac")))
        assertFalse(DefaultSoundPolicy.matches(track, facts.copy(folder = "Music/Other")))
        assertFalse(DefaultSoundPolicy.matches(track, facts.copy(dateAddedMs = 13_000)))
        assertFalse(DefaultSoundPolicy.matches(track, facts.copy(durationMs = 41_001)))
        assertFalse(DefaultSoundPolicy.matches(track.copy(fileName = null, folder = null, absoluteFilePath = null), facts))
        assertFalse(DefaultSoundPolicy.matches(track.copy(absoluteFilePath = "/storage/../song.flac"), facts))
        assertFalse(DefaultSoundPolicy.matches(track, facts.copy(providerIdentity = "")))
        assertFalse(DefaultSoundPolicy.matches(track.copy(absoluteFilePath = null), facts.copy(canonicalPath = "/different/location/song.flac")))
    }

    @Test fun providerReassignmentOrMetadataChangeInvalidatesHeldWitness() {
        assertTrue(DefaultSoundPolicy.unchanged(facts, facts.copy()))
        assertFalse(DefaultSoundPolicy.unchanged(facts, facts.copy(providerIdentity = "row23:external_primary:version-b:generation4")))
        assertFalse(DefaultSoundPolicy.unchanged(facts, facts.copy(providerIdentity = "row23:external_primary:version-a:generation5")))
        assertFalse(DefaultSoundPolicy.unchanged(facts, facts.copy(dateAddedMs = 13_000)))
        assertFalse(DefaultSoundPolicy.unchanged(facts, facts.copy(durationMs = 40_001)))
        assertFalse(DefaultSoundPolicy.unchanged(facts, facts.copy(uri = "content://media/external/audio/media/24")))
    }

    @Test fun defaultPermissionAndProviderAliasesAreNeverAcceptedAsSources() {
        listOf("content://settings/system/ringtone", "content://settings/system/alarm_alert",
            "content://media/external/audio/media/23?canonical=1", "content://media/external/audio/media/23#alias",
            "content://other/external/audio/media/23", "content://media/external/audio/media/0",
            "content://media/external/audio/media/23/", "file:///storage/emulated/0/song.flac").forEach {
            assertFalse(DefaultSoundPolicy.isSupportedSource(it), it)
        }
        assertTrue(DefaultSoundPolicy.isSupportedSource("content://media/external_primary/audio/media/23"))
    }

    @Test fun durationToleranceCannotOverflow() {
        assertTrue(DefaultSoundPolicy.sameDuration(40_000, 39_000))
        assertTrue(DefaultSoundPolicy.sameDuration(40_000, 41_000))
        assertFalse(DefaultSoundPolicy.sameDuration(40_000, 41_001))
        assertFalse(DefaultSoundPolicy.sameDuration(40_000, Long.MIN_VALUE))
        assertFalse(DefaultSoundPolicy.sameDuration(40_000, Long.MAX_VALUE))
    }

    @Test fun exactCopyRejectsShortExtraZeroAndOverflowingChunks() {
        val copy = DefaultSoundCopyBudget(6)
        copy.consume(4)
        assertFailsWith<IllegalStateException> { copy.requireComplete() }
        assertFailsWith<IllegalArgumentException> { copy.consume(3) }
        assertFailsWith<IllegalArgumentException> { copy.consume(0) }
        copy.consume(2)
        copy.requireComplete()
        assertFailsWith<IllegalArgumentException> { copy.consume(1) }
        assertFailsWith<IllegalArgumentException> { DefaultSoundCopyBudget(0) }
        val huge = DefaultSoundCopyBudget(Long.MAX_VALUE)
        huge.consume(Int.MAX_VALUE)
        assertTrue(huge.copiedBytes == Int.MAX_VALUE.toLong())
    }
}
