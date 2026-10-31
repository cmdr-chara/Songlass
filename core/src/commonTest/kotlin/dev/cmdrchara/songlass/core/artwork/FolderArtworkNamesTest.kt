package dev.cmdrchara.songlass.core.artwork

import kotlin.test.*

class FolderArtworkNamesTest {
    @Test fun exactTrackAndAlbumNamesPrecedeConventionalCovers() {
        val names = FolderArtworkNames.imageNames("01 Song.flac", "Record", "Song")
        assertTrue(names.indexOf("01 Song.jpg") < names.indexOf("Record.jpg"))
        assertTrue(names.indexOf("Record.jpg") < names.indexOf("cover.jpg"))
        assertTrue(names.indexOf("folder.jpg") > names.indexOf("cover.jpg"))
    }
    @Test fun aSelectedGeneralFolderNeverSuppliesItsGenericCoverToEverySong() {
        val names = FolderArtworkNames.imageNames("Bad Weekend.m4a", null, "Bad Weekend", generic = false)
        assertTrue("Bad Weekend.jpg" in names)
        assertFalse("cover.jpg" in names)
        assertFalse("folder.png" in names)
    }
    @Test fun onlyExplicitDiscFoldersInheritParentArtwork() {
        assertTrue(FolderArtworkNames.isDiscDirectory("CD 1"))
        assertTrue(FolderArtworkNames.isDiscDirectory("Disc-02"))
        assertFalse(FolderArtworkNames.isDiscDirectory("Pop & Electronic"))
        assertFalse(FolderArtworkNames.isDiscDirectory("Music"))
    }
    @Test fun anExactCoverAndAlbumBeyondTheFirst256RowsAreStillFound() {
        val files = (1..600).map { "unrelated-$it.jpg" } + "Bad Weekend.jpg"
        val matches = files.filter { FolderArtworkNames.isRequestedEntry(it, false,
            FolderArtworkNames.imageNames("Bad Weekend.m4a", null, generic = false), emptyList()) }
        assertEquals(listOf("Bad Weekend.jpg"), matches)
        val directories = (1..600).map { "Other artist $it" } + "Krubb Wenkroist"
        assertEquals(listOf("Krubb Wenkroist"), directories.filter {
            FolderArtworkNames.isRequestedEntry(it, true, emptyList(), listOf("Krubb Wenkroist"))
        })
    }
    @Test fun metadataCannotIntroducePathsAndSupportedNamesAreDeduplicated() {
        val names = FolderArtworkNames.imageNames("song.flac", "../Elsewhere", "song")
        assertTrue(names.none { '/' in it || '\\' in it })
        assertEquals(1, names.count { it == "song.jpg" })
    }
}
