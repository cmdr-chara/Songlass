#!/usr/bin/env bash
# Build a real iPhone application; the resulting IPA needs signing before installation.
set -euo pipefail

if [[ "$(uname -s)" != Darwin ]]; then
  echo "This build requires macOS with Xcode; no IPA was produced." >&2
  exit 1
fi
for command in xcodebuild xcrun ditto shasum python3; do
  command -v "$command" >/dev/null || { echo "Missing tool: $command" >&2; exit 1; }
done
xcode_identity="$(xcodebuild -version)"
ios_sdk_version="$(xcrun --sdk iphoneos --show-sdk-version)"
python3 - "$xcode_identity" "$ios_sdk_version" <<'PY'
import re, sys
match = re.search(r'Xcode\s+(\d+)\.(\d+)', sys.argv[1])
sdk = re.match(r'(\d+)\.(\d+)', sys.argv[2])
if match is None or sdk is None or tuple(map(int, match.groups())) < (27, 1) or tuple(map(int, sdk.groups())) < (27, 1):
    raise SystemExit('iPhone Duo support requires Xcode 27.1+ and the iOS 27.1+ SDK. Select that Xcode with DEVELOPER_DIR; the minimum supported iOS version stays unchanged.')
PY
if [[ "${OVERRIDE_KOTLIN_BUILD_IDE_SUPPORTED:-}" == YES ]]; then
  echo "Refusing to skip the Kotlin framework build phase." >&2
  exit 1
fi
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
out_arg="${1:-$repo_root/build/ios-ipa}"
mkdir -p "$out_arg"
out_root="$(cd "$out_arg" && pwd)"
work_dir="$(mktemp -d "$out_root/run-XXXXXXXX")"
export SONG_IPA_WORK_DIR="$work_dir"
exec > >(tee "$work_dir/build.log") 2>&1
printf 'Build directory: %s\n' "$work_dir"
cd "$repo_root"
xcodebuild -version
xcrun --sdk iphoneos --show-sdk-version
# The project's existing Gradle phase builds and embeds Shared. Do not bypass it.
xcodebuild -hideShellScriptEnvironment -project iosApp/iosApp.xcodeproj -target iosApp \
  -configuration Release -sdk iphoneos ARCHS=arm64 ONLY_ACTIVE_ARCH=NO \
  CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO CODE_SIGN_IDENTITY= DEVELOPMENT_TEAM= \
  PRODUCT_BUNDLE_IDENTIFIER=dev.cmdrchara.songlass PRODUCT_NAME=Songlass \
  ENABLE_USER_SCRIPT_SANDBOXING=NO \
  "SYMROOT=$work_dir/products" "OBJROOT=$work_dir/intermediates" \
  'OTHER_LDFLAGS=$(inherited) -framework Shared' build

app="$work_dir/products/Release-iphoneos/Songlass.app"
[[ -d "$app" && -f "$app/Info.plist" ]] || { echo "Missing built Songlass.app or Info.plist" >&2; exit 1; }
executable="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleExecutable' "$app/Info.plist")"
[[ -n "$executable" && "$executable" != */* && -f "$app/$executable" ]] || { echo "Invalid app executable" >&2; exit 1; }
file "$app/$executable" | tee "$work_dir/executable-info.txt"
file "$app/$executable" | grep -q 'Mach-O' || { echo "App executable is not Mach-O" >&2; exit 1; }
architectures="$(xcrun lipo -archs "$app/$executable")"
[[ "$architectures" == arm64 ]] || { echo "Expected device arm64, got: $architectures" >&2; exit 1; }
[[ -s "$app/Assets.car" ]] || { echo "Missing compiled asset catalog" >&2; exit 1; }
resources="$(find "$app" -type d -name compose-resources -print -quit)"
[[ -n "$resources" && -n "$(find "$resources" -type f -print -quit)" ]] || { echo "Missing populated compose-resources" >&2; exit 1; }

mkdir -p "$work_dir/package/Payload"
ditto "$app" "$work_dir/package/Payload/Songlass.app"
ipa="$work_dir/Songlass-unsigned.ipa"
(cd "$work_dir/package" && ditto -c -k --keepParent Payload "$ipa")
(cd "$work_dir" && shasum -a 256 Songlass-unsigned.ipa > Songlass-unsigned.ipa.sha256)
export SONG_IPA_FILE="$ipa" SONG_IPA_ARCHS="$architectures"
python3 - <<'PY'
import datetime, hashlib, json, os, pathlib, plistlib, subprocess, zipfile
ipa = pathlib.Path(os.environ['SONG_IPA_FILE'])
with zipfile.ZipFile(ipa) as archive:
    assert archive.testzip() is None, 'Corrupt IPA archive'
    bundle = plistlib.loads(archive.read('Payload/Songlass.app/Info.plist'))
    assert bundle['CFBundleIdentifier'] == 'dev.cmdrchara.songlass'
    assert bundle['CFBundleSupportedPlatforms'] == ['iPhoneOS'], 'Expected device iOS app'
    assert 'Payload/Songlass.app/' + bundle['CFBundleExecutable'] in archive.namelist()
    alternate = bundle.get('CFBundleIcons', {}).get('CFBundleAlternateIcons', {})
    expected = {'AppIconNight', 'AppIconLiquidLight', 'AppIconLiquidDark', 'AppIconMaterialLight', 'AppIconMaterialDark', 'AppIconMiuixLight', 'AppIconMiuixDark'}
    assert expected <= alternate.keys(), 'Missing alternate launcher icons'
info = {
    'created_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'artifact': ipa.name,
    'sha256': hashlib.sha256(ipa.read_bytes()).hexdigest(),
    'bytes': ipa.stat().st_size,
    'architectures': os.environ['SONG_IPA_ARCHS'],
    'bundle_identifier': 'dev.cmdrchara.songlass',
    'configuration': 'Release',
    'sdk': 'iphoneos',
    'sdk_version': subprocess.check_output(['xcrun', '--sdk', 'iphoneos', '--show-sdk-version'], text=True).strip(),
    'minimum_build_sdk': '27.1',
    'signing': 'unsigned; requires later sideload signing',
    'xcode': subprocess.check_output(['xcodebuild', '-version'], text=True).strip(),
    'commit': os.environ.get('GITHUB_SHA') or subprocess.check_output(['git', 'rev-parse', 'HEAD'], text=True).strip(),
    'minimum_ios': bundle.get('MinimumOSVersion'),
    'alternate_icons': sorted(alternate),
    'validation': ['real app executable is Mach-O arm64', 'Info.plist', 'Assets.car', 'populated compose-resources'],
    'limits': ['Built artifact, not device installation or runtime verification.'],
}
(ipa.parent / 'build-info.json').write_text(json.dumps(info, indent=2) + '\n')
PY
printf 'Unsigned IPA ready: %s\n' "$ipa"
