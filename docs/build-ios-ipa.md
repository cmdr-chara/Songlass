# Build IPA iPhone

Use JDK 21 and Xcode 27.1 with the iOS 27.1 SDK, as configured in `.github/workflows/ios-ipa.yml`.
With the Android SDK and Gradle dependencies available, run:

```bash
bash tools/build-ios-ipa.sh
```

Output is written to a unique `build/ios-ipa/run-*` directory, including an unsigned IPA,
checksums and build logs. The script preserves previous builds and does not run on Linux.
Signing is required before installation; this is not an App Store or TestFlight package.

The workflow runs on GitHub Actions using the `xcode-27` runner and selects the stable Xcode 27.1 installation.
It runs manually and for matching source changes on `main` or build branches. It uses read-only repository permissions
and does not require signing credentials. Artifacts expire after three days.
A generated IPA or a shared Kotlin compilation does not prove iPhone runtime behavior.
