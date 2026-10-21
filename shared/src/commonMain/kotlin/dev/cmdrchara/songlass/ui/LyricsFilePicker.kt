package dev.cmdrchara.songlass.ui

import androidx.compose.runtime.Composable

/** Opaque platform document reference; no imported text is placed in saved instance state. */
data class LyricsFileSelection(
    val reference: String,
    val releaseReadGrant: Boolean = false,
    /** Stable token saved with the selected URI, distinct from a later picker request. */
    val grantToken: String? = null,
)

interface LyricsFilePicker {
    fun open()
    /** Retains read access off the UI thread before a selected document is read. */
    suspend fun retain(selection: LyricsFileSelection): LyricsFileSelection
    suspend fun read(selection: LyricsFileSelection): String?
    /** Releases only a read grant acquired by this flow, after explicit confirmation/cancel. */
    suspend fun release(selection: LyricsFileSelection)
}

/** Register unconditionally at the app root, never inside a conditional lyrics/player page. */
@Composable
expect fun rememberLyricsFilePicker(
    requestKey: String,
    onResult: (String, LyricsFileSelection?) -> Boolean,
): LyricsFilePicker?
