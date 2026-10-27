package dev.cmdrchara.songlass.core.net

/**
 * iOS has no tested HTTP engine yet.
 *
 * The iPhone build is still a mute demo (no library scan, no player, no network features) and no
 * Ktor engine has been exercised on device. Returning null is deliberate: callers must degrade
 * gracefully instead of pretending cover lookup or scrobbling are available.
 *
 * When an engine is integrated, add an actual with the same contract (HTTPS only, bounded body,
 * timeouts, failures mapped to [HttpResult]) and update this comment together with
 * `docs/design/network-foundation-2026-10-27.md`.
 */
actual fun platformHttpTransport(): HttpTransport? = null
