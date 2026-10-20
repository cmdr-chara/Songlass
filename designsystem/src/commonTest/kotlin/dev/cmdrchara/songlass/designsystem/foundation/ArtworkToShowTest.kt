package dev.cmdrchara.songlass.designsystem.foundation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArtworkToShowTest {
    private fun show(
        hasRequest: Boolean = true,
        loadedForRequest: Boolean = false,
        loaded: String? = null,
        cachedKnown: Boolean = false,
        cached: String? = null,
        standIn: String? = null,
    ) = artworkToShow(hasRequest, loadedForRequest, loaded, cachedKnown, cached, standIn)

    @Test
    fun cachedCoverIsShownBeforeAnyLoadCompletes() {
        assertEquals("cover", show(cachedKnown = true, cached = "cover"))
    }

    @Test
    fun knownMissingCoverShowsThePlaceholderWithoutKeepingAnOldOne() {
        assertNull(show(loaded = "previous", cachedKnown = true, cached = null))
    }

    @Test
    fun largeCoverStillDecodingShowsTheCachedSmallSize() {
        assertEquals("small", show(loaded = "previous", standIn = "small"))
    }

    @Test
    fun albumCoverStandsInForATrackCoverStillDecoding() {
        assertEquals("album", show(standIn = "album"))
    }

    @Test
    fun loadedResultWinsOverFallbacks() {
        assertEquals("large", show(loadedForRequest = true, loaded = "large", standIn = "small"))
        assertNull(show(loadedForRequest = true, loaded = null, standIn = "small"))
    }

    @Test
    fun uncachedNewCoverKeepsThePreviousImageUntilItDecodes() {
        assertEquals("previous", show(loaded = "previous"))
    }

    @Test
    fun noLoaderOrKeyShowsThePlaceholder() {
        assertNull(show(hasRequest = false, loaded = "previous", cachedKnown = true, cached = "cover"))
    }
}
