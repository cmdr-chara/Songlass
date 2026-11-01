# Build IPA iPhone

On macOS, install the JDK and the Xcode/iOS SDK versions required by `.github/workflows/ios-ipa.yml`.
With the Android SDK and Gradle dependencies available, run:

```bash
bash tools/build-ios-ipa.sh
```

Output is written to a unique `build/ios-ipa/run-*` directory, including an unsigned IPA,
checksums and build logs. The script preserves previous builds and does not run on Linux.
Signing is required before installation; this is not an App Store or TestFlight package.

The workflow can also run through GitHub Actions. It uses read-only repository permissions
and does not require signing credentials. Artifacts expire after three days.
A generated IPA or a shared Kotlin compilation does not prove iPhone runtime behavior.
