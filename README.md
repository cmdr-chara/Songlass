MY LAPTOP'S BATTERY DIED, THE MAJOR UPDATE IS DELAYED.

<p align="center">
  <img src="design/icon/styles/violet-squircle.svg" width="96" height="96" alt="Songlass icon">
</p>

<h1 align="center">Songlass</h1>

<p align="center"><strong>English</strong> · <a href="README.it.md">Italiano</a></p>

<p align="center">
  A local music player with three distinct interfaces.<br>
  Liquid Glass · Material Glass · Miuix
</p>

<p align="center">
  <a href="#screenshots">Screenshots</a> ·
  <a href="#features">Features</a> ·
  <a href="#project-status">Platform support</a> ·
  <a href="#build-and-checks">Build</a> ·
  <a href="#contributing-and-community">Contribute</a> ·
  <a href="#support-development">Support</a>
</p>

Songlass plays the music on your device, with an organized library, configurable audio and synchronized lyrics.
Switch the entire interface style while keeping your library, playback session and place in the app.

**In development for Android 13+.** iOS support is experimental.
[Source-available license](LICENSE) · [Roadmap](docs/ROADMAP.md)

**Website — WIP:** the promotional site lives in [`website/`](website/README.md). It is a development preview, separate from the app.

## Screenshots

| Liquid Glass | Material Glass | Miuix |
| --- | --- | --- |
| <a href="docs/images/screenshots/liquid-glass-player.png"><img src="docs/images/screenshots/liquid-glass-player.png" width="230" alt="Songlass player in Liquid Glass"></a> | <a href="docs/images/screenshots/material-glass-player.png"><img src="docs/images/screenshots/material-glass-player.png" width="230" alt="Songlass player in Material Glass"></a> | <a href="docs/images/screenshots/miuix-player.png"><img src="docs/images/screenshots/miuix-player.png" width="230" alt="Songlass player in Miuix"></a> |
| Refractive glass and a floating navigation bar. | Tonal surfaces and expressive Material controls. | HyperOS-style components and compact layouts. |

<details>
<summary>Appearance settings in all three styles</summary>

| Liquid Glass | Material Glass | Miuix |
| --- | --- | --- |
| <a href="docs/images/screenshots/liquid-glass-appearance.png"><img src="docs/images/screenshots/liquid-glass-appearance.png" width="230" alt="Liquid Glass appearance settings"></a> | <a href="docs/images/screenshots/material-glass-appearance.png"><img src="docs/images/screenshots/material-glass-appearance.png" width="230" alt="Material Glass appearance settings"></a> | <a href="docs/images/screenshots/miuix-appearance.png"><img src="docs/images/screenshots/miuix-appearance.png" width="230" alt="Miuix appearance settings"></a> |

</details>

Android development previews with synthetic demo audio and project-owned artwork. Open an image for full size.
Captured on AOSP at 1080×2400, density 420 and default text size.

## Features

### Library and queue

- Browse songs, albums, artists, genres and folders; pin collections and customize Home.
- Compare possible duplicate files by title, artist and duration, with file locations and playback; no automatic deletion.
- Create playlists, import and export M3U files, and build smart playlists from rules such as ratings.
- Import UTF-8 CUE sheets from Library to create virtual tracks from already indexed audio files; original files remain available. [Details and limits](docs/cue-sheets.md).
- Manage favorites, five-star ratings and listening history, with statistics and listening memories.
- Reorder the queue, add temporary tracks, and save your upcoming songs as a playlist.
- Repeat a passage with session-only A–B loop points set directly in the player.

### Playback and sound

- Gapless playback, crossfade and configurable fades for transport actions.
- ReplayGain, graphic EQ and editable parametric profiles with frequency, gain and Q, AutoEQ headphone profiles and settings for different audio outputs.
- Balance, stereo expansion, tempo, bass, treble and reverb controls.
- Per-track resume, bookmarks and a sleep timer.

### Lyrics and artwork

- Synchronized lyrics, including word-by-word highlighting when word timestamps are available.
- Import lyrics, adjust their timing and reading size, and keep the screen awake while reading.
- Embedded and folder cover art, plus automatic online lookup for missing artwork.
- Edit track tags and choose or replace downloaded covers.

### Appearance and everyday controls

- Three interface styles, light and dark appearances, and an optional Liquid Metal finish within Liquid Glass.
- Searchable settings, text scaling, reduced motion and reduced transparency controls.
- Android home-screen widgets, media notifications, lock-screen controls and Android Auto integration.
- Configurable headset actions, backup and restore, and optional Last.fm scrobbling.

## Project status

| Platform | Status |
| --- | --- |
| **Android 13+** | Primary development target. Build a debug APK using the instructions below. |
| **iOS** | Experimental. An unsigned iPhone IPA is available in [Releases](https://github.com/cmdr-chara/Songlass/releases). Signing is required before installation; physical-device validation remains open. |

Android Auto, audio hardware, accessibility and release verification still have open checks in the
[roadmap](docs/ROADMAP.md). Xiaomi media-island behavior depends on the capabilities exposed by the device.

### Network and privacy

Local playback does not require an account or a music server. Online artwork and lyrics can contact external
services; both are enabled by default, with online artwork restricted to unmetered connections by default.
Last.fm is opt-in and requires your own API credentials. See the [data and privacy draft](docs/PRIVACY.md)
for the services, data and settings involved.

## Build and checks

Use **JDK 17 or later**, **Android SDK 37** and the included Gradle wrapper. Configure your SDK location in
local `local.properties` or environment variables. Dependency versions are in [gradle/libs.versions.toml](gradle/libs.versions.toml).

```bash
./gradlew :androidApp:assembleDebug
```

The development APK is written to `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.

GitHub Actions also builds a downloadable debug APK for pushes and pull requests. Version tags prepare
signed Android releases **as drafts**. See [Android builds and releases](docs/android-builds.md) for artifacts,
signing and the release process.

Run Android host tests and lint:

```bash
./gradlew :core:testAndroidHostTest :designsystem:testAndroidHostTest :androidApp:lintDebug
```

For iOS packaging, use the [iOS build guide](docs/build-ios-ipa.md).
Development architecture and device rules are in [CLAUDE.md](CLAUDE.md).

<details>
<summary>Documentation checks</summary>

These checks use Python 3 and its standard library:

```bash
python3 tools/check_repository_hygiene.py
python3 -m unittest discover -s tools/tests -p 'test_repository_hygiene.py' -v
```

</details>

<details>
<summary>Repository map</summary>

| Path | Responsibility |
| --- | --- |
| `core` | Library, playback, queue, playlists, lyrics and settings logic |
| `designsystem` | Shared UI contracts and layout foundations |
| `theme-liquidglass`, `theme-materialglass`, `theme-miuix` | Components for each interface style |
| `shared` | Screens, resources and application wiring |
| `androidApp`, `iosApp` | Native applications and platform integration |
| `docs` | Documentation, screenshots, roadmap and build guides |
| `tools`, `scripts/qa` | Build and verification tools |

</details>

## Contributing and community

Bug reports, translations, accessibility improvements and code contributions are welcome.
Read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting work. Working forks and review builds are explicitly
permitted for contributing to Songlass under the [license](LICENSE).

- Disclose AI assistance and explain how you checked the result.
- Include before/after screenshots for UI design changes, covering the affected styles.
- Follow the [Code of Conduct](CODE_OF_CONDUCT.md). Coding agents also read [AGENTS.md](AGENTS.md).

For questions and feature requests, see [SUPPORT.md](SUPPORT.md). Report vulnerabilities through [SECURITY.md](SECURITY.md).

## Support development

Donations are optional. You can support Songlass using the addresses below.
Use the network shown for each address.

**Bitcoin (BTC) · Bitcoin network**

```text
bc1q00duwpvkuc6g6xw590k62pwdha953dxz7rzv0k
```

**Solana (SOL) · Solana network**

```text
8PKRGoLVwpbL39FmmMV3V6ps71a2iq4YcaoGsRthMZzi
```

**Litecoin (LTC) · Litecoin network**

```text
LSwuNWoHo3wBxzSBZwead2CBxLGLgxEhvu
```

## License

Songlass uses the **[Songlass Source-Available License 1.0](LICENSE)**. It is source-available, not OSI-approved open source.

Personal noncommercial use and modifications are permitted, as are working forks, pull requests and review builds
for contributing to Songlass, including paid contribution work. Other redistribution, sale or publishing a separate
player, including free or renamed copies, requires prior written permission.

Read [LICENSING.md](LICENSING.md) for the scope. Contributors retain copyright; third-party components keep their own licenses and notices.

## Documentation

Start with the [documentation index](docs/README.md) or [roadmap](docs/ROADMAP.md).
The [privacy policy](docs/PRIVACY.md) and [store listing](docs/STORE-LISTING.md) are development drafts.
