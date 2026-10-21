package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Outcome of choosing a backup file to restore from. */
sealed interface BackupFileImport {
    /** The file's text, read completely and within the backup size limit. */
    class Loaded(val text: String) : BackupFileImport {
        // Backups hold personal data: keep the text out of anything that prints this.
        override fun toString(): String = "Loaded(${text.length} chars)"
    }

    /** The user closed the picker without choosing anything. */
    data object Cancelled : BackupFileImport

    /** The file could not be read, is not valid UTF-8 text, or is over the size limit ([tooLarge]). */
    data class Failed(val tooLarge: Boolean = false) : BackupFileImport
}

/** Outcome of saving a backup file. */
sealed interface BackupFileExport {
    /** All of the content reached the chosen location. */
    data object Saved : BackupFileExport

    /** The user closed the picker without choosing a location; nothing was written. */
    data object Cancelled : BackupFileExport

    /** The old picker lost its UI owner before accepting a location. Start a new export explicitly. */
    data object Interrupted : BackupFileExport

    /** The location could not be written; a partly written file is removed where the system allows. */
    data object Failed : BackupFileExport
}

/**
 * Lets the user pick a backup file (any type: backups have no registered MIME type) and reports
 * it through [onResult], exactly once per launch. Null where the platform has no picker yet.
 */
@Composable
expect fun rememberBackupImporter(onResult: (BackupFileImport) -> Unit): (() -> Unit)?

/**
 * Lets the user choose where to save [content] under the suggested file name and reports the
 * outcome through [onResult], exactly once per launch, after the write has finished or failed.
 * Null where the platform has no picker yet.
 */
@Composable
expect fun rememberBackupExporter(onResult: (BackupFileExport) -> Unit): ((fileName: String, content: String) -> Unit)?
