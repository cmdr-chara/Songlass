# Android builds and draft releases

## Development builds

[Android build](../.github/workflows/android.yml) runs on pushes to `main`, pull requests targeting `main`,
and manual dispatch. It runs repository checks, Python release-guard tests, Android host tests, lint and
`assembleDebug`. Download `Songlass-debug-<commit>` from the run's **Artifacts** section. Test and lint
reports are uploaded separately, including when a check fails. Artifacts expire after 14 days.

Development APKs use the runner's debug key. They are test builds, and may not update an existing APK
signed by another key. The release APK uses the stable certificate described below.

Fork pull requests run on GitHub-hosted runners with a read-only repository token and no signing secrets.
The release build does not restore CI caches.

## Signing configuration

The repository stores only the public release-certificate SHA-256 in
[`androidApp/release-signing-certificate.sha256`](../androidApp/release-signing-certificate.sha256).
Keep the private PKCS#12 keystore, alias and passwords outside Git, with an encrypted/offline backup.
Do not generate another key for each build: directly distributed Android APK updates must retain their
signing identity. If Play App Signing is introduced later, plan its signing-key configuration before
publishing to multiple stores.

The maintainer configures four GitHub Actions repository secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 encoding of the private release keystore, on one line |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Alias of the release key |
| `ANDROID_KEY_PASSWORD` | Private-key password |

Never put these values in an issue, PR, release note or workflow input. Repository recreation requires
configuring the secrets again. Changing the certificate pin is a deliberate signing-key change, not a
way to bypass a failed verification.

For a local signed build, set `SONGLASS_SIGNING_STORE_FILE`, `SONGLASS_SIGNING_STORE_PASSWORD`,
`SONGLASS_SIGNING_KEY_ALIAS` and `SONGLASS_SIGNING_KEY_PASSWORD` in the build process's environment, then run:

```bash
./gradlew --no-configuration-cache :androidApp:assembleRelease
```

All four variables must be supplied together. Without them, local release builds remain unsigned;
the release workflow rejects unsigned APKs and certificates that differ from the public pin.
Do not enable verbose/debug Gradle logging or configuration caching for a build holding signing secrets.

## Validate the release workflow without creating a release

Run **Android release** manually from `main`, or use:

```bash
gh workflow run android-release.yml --ref main
```

This runs the checks, builds the optimized signed APK and verifies its package, version and certificate.
It uploads the APK, `build-info.json` and `SHA256SUMS` as a 14-day artifact. **Manual runs never create a
GitHub release or tag.** A green run proves these build checks; it does not replace testing playback,
navigation, accessibility and hardware on devices.

## Prepare a draft release

1. Update `versionName` and `versionCode` in `androidApp/build.gradle.kts`. Use `MAJOR.MINOR.PATCH`, optionally
   followed by `-alpha.N`, `-beta.N` or `-rc.N`. The version code must exceed those of previous version tags.
2. Commit and push the reviewed change to `main`; wait for its checks and perform the device checks.
3. Create and push the matching tag. For example, when the declared version is `0.1.0`:

   ```bash
   git tag -a v0.1.0 -m "Songlass 0.1.0"
   git push origin v0.1.0
   ```

The [Android release workflow](../.github/workflows/android-release.yml) requires the tag to match
`versionName` and its commit to belong to `main`. Host tests, release lint, package/version verification
and the pinned signing certificate must pass before the draft job runs. The APK is optimized with R8.
An isolated job then receives permission to create a **draft**, with the APK, checksums, build identity
and generated release notes. It does not execute the downloaded artifacts. It does not publish a stable
release automatically or replace an existing release.

Review the draft and revise its notes before publishing. Alpha/beta/RC tags are marked as **prerelease** automatically.
Keep the separate R8 mapping artifact for crash diagnosis. Before announcing a public release, complete
the applicable items in the [roadmap](ROADMAP.md), [privacy draft](PRIVACY.md) and [store-listing draft](STORE-LISTING.md).

Official documentation: [Gradle on Actions](https://docs.github.com/en/actions/tutorials/build-and-test-code/java-with-gradle),
[Actions secrets](https://docs.github.com/en/actions/security-for-github-actions/security-guides/using-secrets-in-github-actions)
and [Android app signing](https://developer.android.com/studio/publish/app-signing).
