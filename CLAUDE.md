# Songlass development

Songlass is a Kotlin and Compose Multiplatform music player. Android is the primary development target;
iOS support is experimental. Read [AGENTS.md](AGENTS.md), [CONTRIBUTING.md](CONTRIBUTING.md) and
[LICENSING.md](LICENSING.md) before making changes.

## Architecture

- `core`: library, playback, queues, playlists, settings and other domain logic.
- `designsystem`: shared visual contracts, tokens and primitives.
- `theme-liquidglass`, `theme-materialglass`, `theme-miuix`: implementations of those visual contracts.
- `shared`: screen composition, navigation and process-level application wiring.
- `androidApp`: Android application and platform integration.
- `iosApp`: iOS application and native integration.

Use [gradle/libs.versions.toml](gradle/libs.versions.toml) and the checked-in Gradle wrapper as the source
of truth for dependency and toolchain versions. Do not change them incidentally.

## Implementation contracts

- Put business logic in `core`; keep screen composables focused on presentation and interaction.
- Implement shared visual changes through the design-system contracts for all three styles. Preserve
  each style's identity and avoid style-specific branches in shared screens.
- Keep the theme composition stable when switching styles so playback, navigation and scroll state survive.
- Preserve the player artwork handoff and layout contracts during mini-player transitions.
- Keep playback state, media identity and persistence changes consistent across asynchronous operations.
- Maintain backward compatibility for saved settings, queues and backups.
- Keep user-facing strings in English and Italian resources. Use positional formatting placeholders.
- Update the settings search targets and reset groups when adding or moving preferences.
- Preserve third-party notices and use media that may be shared in tests and screenshots.

<a id="verifica"></a>

## Verification

Run the checks relevant to the change with the repository's existing tasks:

```bash
./gradlew :core:testAndroidHostTest :designsystem:testAndroidHostTest :androidApp:assembleDebug :androidApp:lintDebug
python3 -B tools/check_repository_hygiene.py
python3 -B -m unittest discover -s tools/tests -v
```

Use `--offline` only when the required dependencies are already cached. Do not use aggregate multiplatform
test tasks as a substitute for Android host tests. Report Android builds, host tests, emulator checks,
Apple compilation and physical-device checks separately. Check UI changes in each affected style and
include the evidence required by `CONTRIBUTING.md`.

For device checks, select the intended device explicitly with `adb -s <serial>`. Inspect the available
device or emulator before interacting with it, and preserve the owner's device settings and data.
Build commands and packaging instructions are linked from [README.md](README.md#build-and-checks).

## Git and publication

Keep commits focused and preserve contributors' authorship and unrelated local work. Publishing builds,
changing repository visibility or licensing, and rewriting shared history require the maintainer's request.
Keep private keys, credentials, personal media and local machine configuration out of Git.
