# CUE sheet import

On Android, open **Library → Import CUE sheet**, choose a UTF-8 `.cue` document and confirm the number of virtual tracks. The audio files referenced by `FILE` must already be in Songlass's successfully scanned library. The importer rejects missing or ambiguous matches and invalid boundaries instead of importing a partial album. A sheet can reference multiple files.

The initial implementation accepts `AUDIO` tracks with one `INDEX 01` each and `FILE` types `WAVE`, `FLAC`, `MP3` or `AIFF`. Offsets use 75 CD frames per second, converted to milliseconds for the player. `INDEX 00` pregaps stay with the preceding segment; generated `PREGAP` silence is not added. The original audio files stay in the library, and no audio is split, rewritten or copied. The CUE metadata supplies virtual track titles, performers and album grouping.

Songlass stores imported sheet text and resolves its references again on library refresh. Importing identical text does not duplicate tracks. Different sheets cannot silently redefine an existing cut. Reading position and seeks are relative to each virtual track. Local synchronized lyrics are clipped and shifted to the cut; online and manually imported lyrics describe the virtual track itself. Physical-file tag editing is disabled for virtual tracks.

M3U has no portable way to encode these segment boundaries. CUE tracks are therefore counted as skipped in M3U exports, rather than exporting the whole source file accidentally. Keep the original CUE document alongside its audio for use in other players.

Use the same Library action to remove a saved sheet. Removing it drops only virtual tracks; audio files remain untouched. Playlist references to removed cuts remain missing until the sheet is imported again.

Embedded CUE tags, legacy text encodings and synthesized pregap silence are not supported. The iOS file picker remains unavailable; the shared parser and AVFoundation segment mapping do not constitute verified iPhone support. USB DAC and physical-device audio validation are separate from emulator checks.
