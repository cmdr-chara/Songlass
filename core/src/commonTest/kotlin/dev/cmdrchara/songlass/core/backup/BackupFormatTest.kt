package dev.cmdrchara.songlass.core.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupFormatTest {
    private val sources = listOf(
        SourceTrack("1", "content://a", null, "T\n", null, "", 5),
        SourceTrack("2", "content://b", "Music/x.mp3", ODD, "A", "B", 0),
    )
    private val entries = mapOf("settings.a" to "", "settings.b" to ODD, "home.c" to "-1:")

    @Test
    fun writtenTextReadsBackExactlyIncludingNullsEmptiesAndControlCharacters() {
        val parsed = BackupFormat.read(BackupFormat.write(42, entries, sources))
        assertEquals(42L, parsed.createdMs)
        assertEquals(entries, parsed.entries)
        assertEquals(sources, parsed.sources)
    }

    @Test
    fun aLeadingByteOrderMarkIsIgnored() {
        val byteOrderMark = Char(0xFEFF)
        val parsed = BackupFormat.read(byteOrderMark + BackupFormat.write(1, entries, sources))
        assertEquals(entries, parsed.entries)
    }

    @Test
    fun emptyBackupIsValid() {
        val parsed = BackupFormat.read(BackupFormat.write(0, emptyMap(), emptyList()))
        assertEquals(emptyMap(), parsed.entries)
        assertEquals(emptyList(), parsed.sources)
    }

    @Test
    fun exportIsDeterministic() {
        val store = storeOf("settings.b" to "2", "settings.a" to "1", "favorites" to "x")
        val first = DataBackup(store) { NOW }.export(emptyList()).text
        val second = DataBackup(store) { NOW }.export(emptyList()).text
        assertEquals(first, second)
    }

    @Test
    fun textThatIsNotCanonicalIsRejected() {
        // A padded length could be read as the same value in two ways.
        val padded = sealed("SONGLASS-BACKUP 1\n" + "01:H" + "1:0" + "1:0" + "1:0" + "\n")
        assertEquals(BackupError.Malformed, assertFailsWith<BackupException> { BackupFormat.read(padded) }.error)
        val declaresTooMany = handBuilt(emptyList(), declaredEntries = 9_999_999)
        assertEquals(BackupError.Malformed, assertFailsWith<BackupException> { BackupFormat.read(declaresTooMany) }.error)
    }
}
