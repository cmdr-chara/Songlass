package dev.cmdrchara.songlass.core.artwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArtworkCoversTest {
    @Test
    fun precedenceIsLocalThenDownloadedThenGenerated() {
        // No local cover and nothing downloaded: the app draws its generated placeholder.
        assertEquals(ArtworkSource.Generated, ArtworkCovers.source(hasLocalCover = false, hasDownloadedCover = false, applied = false))
        // A local cover always wins over a download that was merely cached.
        assertEquals(ArtworkSource.Local, ArtworkCovers.source(hasLocalCover = true, hasDownloadedCover = true, applied = false))
        assertEquals(ArtworkSource.Local, ArtworkCovers.source(hasLocalCover = true, hasDownloadedCover = false, applied = false))
        // Without a local cover the downloaded one is better than nothing.
        assertEquals(ArtworkSource.Downloaded, ArtworkCovers.source(hasLocalCover = false, hasDownloadedCover = true, applied = false))
        // An explicit choice is exactly that: it replaces the local cover until it is removed.
        assertEquals(ArtworkSource.Downloaded, ArtworkCovers.source(hasLocalCover = true, hasDownloadedCover = true, applied = true))
    }

    @Test
    fun theLogicalAlbumIsIdentifiedByArtistAndAlbumTagsOnly() {
        val subject = ArtworkSubject.of("  The Beatles ", " Abbey Road ")
        assertEquals(ArtworkSubject("The Beatles", "Abbey Road"), subject)
        assertNull(ArtworkSubject.of("The Beatles", "   "))
        assertNull(ArtworkSubject.of("The Beatles", null))
        // A missing artist tag is allowed: the album alone still identifies the cover.
        assertEquals(ArtworkSubject("", "Abbey Road"), ArtworkSubject.of(null, "Abbey Road"))
    }

    @Test
    fun keysFoldCaseAccentsAndPunctuation() {
        val plain = artworkSubjectKey("Bjork", "Homogenic")
        assertEquals(plain, artworkSubjectKey("björk", "HOMOGENIC"))
        assertEquals(plain, artworkSubjectKey("BJÖRK", "homogénic"))
        assertEquals(artworkSubjectKey("AC/DC", "Back in Black"), artworkSubjectKey("ac dc", "Back In Black"))
        assertNotEquals(plain, artworkSubjectKey("Bjork", "Vespertine"))
        assertNotEquals(plain, artworkSubjectKey("Sigur Ros", "Homogenic"))
    }

    @Test
    fun decomposedAccentsNormaliseTheSameAsComposedOnes() {
        // "Björk" written with a combining diaeresis must fold like the precomposed character.
        assertEquals(normalizeArtworkText("björk"), normalizeArtworkText("bjo\u0308rk"))
        assertEquals("abbey road remastered", normalizeArtworkText("Abbey Road (Remastered)"))
        assertEquals("ac dc", normalizeArtworkText("AC/DC"))
        assertEquals("", normalizeArtworkText("  "))
    }

    @Test
    fun fileNamesAreDeterministicAndCarryNoTitle() {
        val key = artworkSubjectKey("The Beatles", "Abbey Road")
        val name = artworkFileName(key)
        assertEquals(name, artworkFileName(key))
        assertEquals(22 + ".img".length, name.length) // "cover-" + 16 hex digits + ".img"
        assertTrue(name.startsWith("cover-") && name.endsWith(".img"), name)
        assertTrue(name.all { it.isLetterOrDigit() || it == '-' || it == '.' }, name)
        assertTrue("abbey" !in name.lowercase() && "beatles" !in name.lowercase())
        assertNotEquals(name, artworkFileName(artworkSubjectKey("The Beatles", "Let It Be")))
    }

    @Test
    fun theNetworkMayOnlyBeUsedWhenTheUserAllowedIt() {
        assertTrue(OnlineArtworkPolicy.canUseNetwork(enabled = true, hasEngine = true, wifiOnly = false, metered = true))
        assertTrue(OnlineArtworkPolicy.canUseNetwork(enabled = true, hasEngine = true, wifiOnly = true, metered = false))
        // Off means off: no lookup and no download, whatever the connection is.
        assertTrue(!OnlineArtworkPolicy.canUseNetwork(enabled = false, hasEngine = true, wifiOnly = false, metered = false))
        // No engine (iOS today) hides the feature instead of promising a search that cannot run.
        assertTrue(!OnlineArtworkPolicy.canUseNetwork(enabled = true, hasEngine = false, wifiOnly = false, metered = false))
        // Wi-Fi only: mobile data and an unknown connection both refuse.
        assertTrue(!OnlineArtworkPolicy.canUseNetwork(enabled = true, hasEngine = true, wifiOnly = true, metered = true))
        assertTrue(!OnlineArtworkPolicy.canUseNetwork(enabled = true, hasEngine = true, wifiOnly = true, metered = null))
    }
}
