package dev.cmdrchara.songlass.core.artwork

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Fixtures mirror the documented iTunes Search API shape (public documentation, hand-written:
 * no live request was made for them). Two entries of the same album exercise ranking and
 * de-duplication, one entry is a different album by the same artist.
 */
private const val ABBEY_ROAD_RESPONSE = """
{
  "resultCount": 3,
  "results": [
    {
      "wrapperType": "collection",
      "collectionType": "Album",
      "artistId": 136975,
      "collectionId": 1440833098,
      "artistName": "The Beatles",
      "collectionName": "Abbey Road (Remastered)",
      "collectionViewUrl": "https://music.apple.com/it/album/abbey-road-remastered/1440833098",
      "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/bb/9c/1a/100x100bb.jpg",
      "trackCount": 17,
      "country": "ITA",
      "currency": "EUR",
      "releaseDate": "1969-09-26T07:00:00Z",
      "primaryGenreName": "Rock"
    },
    {
      "wrapperType": "collection",
      "collectionType": "Album",
      "artistId": 136975,
      "collectionId": 1440833099,
      "artistName": "The Beatles",
      "collectionName": "Abbey Road (Super Deluxe)",
      "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/cc/1d/2e/100x100bb.jpg",
      "trackCount": 41,
      "country": "ITA",
      "currency": "EUR"
    },
    {
      "wrapperType": "collection",
      "collectionType": "Album",
      "artistId": 136975,
      "collectionId": 401186610,
      "artistName": "The Beatles",
      "collectionName": "Let It Be (Remastered)",
      "artworkUrl100": "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/dd/3e/4f/100x100bb.jpg",
      "trackCount": 12,
      "country": "ITA",
      "currency": "EUR"
    }
  ]
}
"""

class ITunesArtworkTest {
    @Test
    fun searchUrlCarriesOnlyTheSearchTermAndItsParameters() {
        val url = ITunesArtwork.searchUrl("The Beatles", "Abbey Road")
        assertEquals(
            "https://itunes.apple.com/search?term=The%20Beatles%20Abbey%20Road" +
                "&media=music&entity=album&limit=8&country=IT",
            url,
        )
        assertTrue(url.startsWith("https://"), "the provider must be reached over HTTPS only")
    }

    @Test
    fun searchUrlEncodesNonAsciiAndPunctuationAndToleratesBadParameters() {
        val bjork = ITunesArtwork.searchUrl("Björk", "Homogenic / Live")
        assertTrue("term=Bj%C3%B6rk%20Homogenic%20%2F%20Live" in bjork, bjork)
        assertEquals(
            "https://itunes.apple.com/search?term=Kind%20of%20Blue&media=music&entity=album&limit=8&country=IT",
            ITunesArtwork.searchUrl(null, "  Kind of Blue  "),
        )
        val clamped = ITunesArtwork.searchUrl(null, "Blue", country = "italy", limit = 500)
        assertTrue("limit=50" in clamped, clamped)
        assertTrue("country=IT" in clamped, clamped)
        assertEquals(
            "https://itunes.apple.com/search?term=Blue&media=music&entity=album&limit=1&country=DE",
            ITunesArtwork.searchUrl(null, "Blue", country = "de", limit = 0),
        )
    }

    @Test
    fun matchingEntriesAreKeptAndRanked() {
        val candidates = ITunesArtwork.parseCandidates(ABBEY_ROAD_RESPONSE, "The Beatles", "Abbey Road")
        assertEquals(2, candidates.size)
        assertTrue(candidates.all { it.artist == "The Beatles" })
        assertTrue(candidates.all { "Abbey Road" in it.album })
        // "Let It Be" is a different album: matching on the album tag rejects it.
        assertTrue(candidates.none { "Let It Be" in it.album })
    }

    @Test
    fun artworkUrlsAreUpgradedToAUsableSizeAndKeepAPreview() {
        val candidate = ITunesArtwork.parseCandidates(ABBEY_ROAD_RESPONSE, "The Beatles", "Abbey Road").first()
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/bb/9c/1a/600x600bb.jpg",
            candidate.imageUrl,
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music124/v4/bb/9c/1a/100x100bb.jpg",
            candidate.previewUrl,
        )
        assertEquals("1440833098", candidate.providerId)
    }

    @Test
    fun rankingRewritesOnlyTheSizeToken() {
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/a/1000x1000bb.jpg",
            ITunesArtwork.urlForResolution("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/a/100x100bb.jpg", 1000),
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music/v4/a/600x600sr.png",
            ITunesArtwork.urlForResolution("https://is1-ssl.mzstatic.com/image/thumb/Music/v4/a/100x100sr.png", 600),
        )
        assertNull(ITunesArtwork.urlForResolution("https://example.com/cover.jpg", 600))
        assertNull(ITunesArtwork.urlForResolution("http://example.com/100x100bb.jpg", 600))
        assertNull(ITunesArtwork.urlForResolution("https://example.com/100x100bb.jpg", 0))
        assertNull(ITunesArtwork.urlForResolution("https://example.com/100x100bb.jpg?token=1", 600))
    }

    @Test
    fun anArtistTagThatContradictsTheResultRejectsIt() {
        assertTrue(ITunesArtwork.parseCandidates(ABBEY_ROAD_RESPONSE, "Daft Punk", "Abbey Road").isEmpty())
        // A missing artist tag must not block an album-only match.
        assertEquals(2, ITunesArtwork.parseCandidates(ABBEY_ROAD_RESPONSE, null, "Abbey Road").size)
    }

    @Test
    fun localEditionSuffixesStillMatchTheProviderTitle() {
        val candidates = ITunesArtwork.parseCandidates(
            """{"results":[{"collectionName":"Discovery","artistName":"Daft Punk",
               "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/a/100x100bb.jpg"}]}""",
            "Daft Punk", "Discovery (Deluxe Edition)",
        )
        assertEquals(1, candidates.size)
    }

    @Test
    fun emptyAndMalformedPayloadsYieldNoCandidates() {
        assertTrue(ITunesArtwork.parseCandidates("""{"resultCount":0,"results":[]}""", "A", "B").isEmpty())
        assertTrue(ITunesArtwork.parseCandidates("<html>not json</html>", "A", "B").isEmpty())
        assertTrue(ITunesArtwork.parseCandidates("""{"results":"nope"}""", "A", "B").isEmpty())
        assertTrue(ITunesArtwork.parseCandidates("", "A", "B").isEmpty())
        assertTrue(ITunesArtwork.parseCandidates("""{"results":[null,3,"x"]}""", "A", "B").isEmpty())
        // An album tag is required: without it nothing can be matched.
        assertTrue(ITunesArtwork.parseCandidates(ABBEY_ROAD_RESPONSE, "The Beatles", " ").isEmpty())
    }

    @Test
    fun malformedEntriesAreSkippedWithoutLosingTheValidOnes() {
        val json = """
        {"results":[
          {"collectionName":"Discovery","artistName":"Daft Punk"},
          {"collectionName":"Discovery","artistName":"Daft Punk","artworkUrl100":"http://insecure.example/100x100bb.jpg"},
          {"collectionName":"Discovery","artistName":"Daft Punk","artworkUrl100":"not a url"},
          {"wrapperType":"track","collectionName":"Discovery","artistName":"Daft Punk","artworkUrl100":"https://is1-ssl.mzstatic.com/a/100x100bb.jpg"},
          {"collectionName":"Discovery","artistName":"Daft Punk","artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/700x700bb.jpg"}
        ]}
        """
        val candidates = ITunesArtwork.parseCandidates(json, "Daft Punk", "Discovery")
        assertEquals(1, candidates.size)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/600x600bb.jpg", candidates.single().imageUrl)
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/100x100bb.jpg", candidates.single().previewUrl)
    }

    @Test
    fun duplicatesCollapseAndTheRequestedLimitIsRespected() {
        val entry = """{"collectionName":"Discovery","artistName":"Daft Punk",
            "collectionId":7,"artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music/a/100x100bb.jpg"}"""
        val repeated = """{"results":[$entry,$entry,$entry]}"""
        assertEquals(1, ITunesArtwork.parseCandidates(repeated, "Daft Punk", "Discovery").size)
        assertEquals(1, ITunesArtwork.parseCandidates(ABBEY_ROAD_RESPONSE, "The Beatles", "Abbey Road", limit = 1).size)
    }

    @Test
    fun artworkUrl60IsUsedWhenTheHundredPixelOneIsMissing() {
        val json = """{"results":[{"collectionName":"Discovery","artistName":"Daft Punk",
            "artworkUrl60":"https://is1-ssl.mzstatic.com/image/thumb/Music/b/60x60bb.jpg"}]}"""
        val candidate = ITunesArtwork.parseCandidates(json, "Daft Punk", "Discovery").single()
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music/b/600x600bb.jpg", candidate.imageUrl)
    }
}

/**
 * Shape observed from a live response on 27 October 2026 (`term=The Beatles Abbey Road`,
 * `country=IT`, `limit=2`), trimmed to the fields this feature reads.
 *
 * It matters because the real artwork URL carries a `.jpg` inside the path and the size token only
 * at the end: a naive rewrite would corrupt such a URL. The response is provider metadata, not
 * user data, and no request was made by the app to obtain it.
 */
private const val LIVE_SHAPE_RESPONSE = """
{"resultCount":2,"results":[
 {"wrapperType":"collection","collectionType":"Album","artistId":136975,"collectionId":1474815798,
  "artistName":"The Beatles","collectionName":"Abbey Road (2019 Mix)",
  "artworkUrl60":"https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/48/53/43/485343e3-dd6a-0034-faec-f4b6403f8108/13UMGIM63890.rgb.jpg/60x60bb.jpg",
  "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/48/53/43/485343e3-dd6a-0034-faec-f4b6403f8108/13UMGIM63890.rgb.jpg/100x100bb.jpg",
  "trackCount":17,"country":"ITA","currency":"EUR","primaryGenreName":"Rock"},
 {"wrapperType":"collection","collectionType":"Album","artistId":136975,"collectionId":1441164426,
  "artistName":"The Beatles","collectionName":"Abbey Road (Remastered)",
  "artworkUrl60":"https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/df/db/61/dfdb615d-47f8-06e9-9533-b96daccc029f/18UMGIM31076.rgb.jpg/60x60bb.jpg",
  "artworkUrl100":"https://is1-ssl.mzstatic.com/image/thumb/Music112/v4/df/db/61/dfdb615d-47f8-06e9-9533-b96daccc029f/18UMGIM31076.rgb.jpg/100x100bb.jpg",
  "trackCount":19,"country":"ITA","currency":"EUR","primaryGenreName":"Rock"}
]}
"""

class ITunesArtworkLiveShapeTest {
    @Test
    fun aRealResponseShapeIsParsedAndItsArtworkUrlRewritten() {
        val candidates = ITunesArtwork.parseCandidates(LIVE_SHAPE_RESPONSE, "The Beatles", "Abbey Road")
        assertEquals(2, candidates.size)
        val first = candidates.first()
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/48/53/43/485343e3-dd6a-0034-faec-f4b6403f8108/13UMGIM63890.rgb.jpg/600x600bb.jpg",
            first.imageUrl,
        )
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/48/53/43/485343e3-dd6a-0034-faec-f4b6403f8108/13UMGIM63890.rgb.jpg/100x100bb.jpg",
            first.previewUrl,
        )
        assertTrue("Abbey Road" in first.album)
    }
}
