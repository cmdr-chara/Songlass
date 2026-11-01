"""Negative cases for release identity, version and debuggable-artifact gates."""
from __future__ import annotations

import importlib.util
import unittest
from unittest.mock import patch
import tempfile
import subprocess
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "ci/android_release.py"
SPEC = importlib.util.spec_from_file_location("android_release", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
release = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(release)


class AndroidReleaseTest(unittest.TestCase):
    certificate = "ab" * 32
    manifest = "package: name='dev.cmdrchara.songlass' versionCode='2' versionName='0.2.0' platformBuildVersionName='17'\n"
    signature = "Signer #1 certificate SHA-256 digest: " + certificate + "\n"

    def test_valid_stable_and_prerelease_versions(self):
        for name in ["0.1.0", "1.2.3-beta.2", "2.0.0-rc.1"]:
            self.assertEqual(release.read_version(f'versionCode = 2\nversionName = "{name}"\n'), (name, 2))

    def test_invalid_ambiguous_and_unsafe_versions_fail(self):
        for text in ['versionCode = 0\nversionName = "0.1.0"',
                     'versionCode = 2100000001\nversionName = "0.1.0"',
                     'versionCode = 1\nversionName = "../../outside"',
                     'versionCode = 1\nversionName = "0.1.0; echo example"',
                     'versionCode = 1\nversionName = "01.1.0"',
                     'versionCode = 1\nversionName = "0.1.0"\nversionName = "0.2.0"']:
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.read_version(text)

    def test_matching_tag_and_manual_main_are_allowed(self):
        release.validate_event("push", "refs/tags/v0.2.0", "0.2.0")
        release.validate_event("workflow_dispatch", "refs/heads/main", "0.2.0")

    def test_mismatched_tag_and_untrusted_events_are_rejected(self):
        for event, ref in [("push", "refs/tags/v0.3.0"), ("push", "refs/heads/main"),
                           ("pull_request", "refs/pull/1/merge"),
                           ("workflow_dispatch", "refs/heads/topic"),
                           ("workflow_dispatch", "refs/tags/v0.2.0")]:
            with self.subTest(event=event, ref=ref), self.assertRaises(ValueError):
                release.validate_event(event, ref, "0.2.0")

    def test_correct_release_identity_passes(self):
        release.verify_apk_reports(self.signature, self.manifest, self.certificate, "0.2.0", 2)

    def test_wrong_missing_or_multiple_signers_fail(self):
        for signature in ["", self.signature.replace(self.certificate, "cd" * 32),
                          self.signature + self.signature.replace("#1", "#2")]:
            with self.subTest(signature=signature), self.assertRaises(ValueError):
                release.verify_apk_reports(signature, self.manifest, self.certificate, "0.2.0", 2)

    def test_wrong_package_version_name_or_code_fails(self):
        for manifest in ["", self.manifest.replace("dev.cmdrchara.songlass", "other.player"),
                         self.manifest.replace("versionCode='2'", "versionCode='1'"),
                         self.manifest.replace("versionName='0.2.0'", "versionName='0.1.0'")]:
            with self.subTest(manifest=manifest), self.assertRaises(ValueError):
                release.verify_apk_reports(self.signature, manifest, self.certificate, "0.2.0", 2)

    def test_debuggable_apk_fails_even_with_the_right_release_certificate(self):
        with self.assertRaises(ValueError):
            release.verify_apk_reports(self.signature, self.manifest + "application-debuggable\n", self.certificate, "0.2.0", 2)

    def test_invalid_certificate_pin_fails(self):
        with self.assertRaises(ValueError):
            release.verify_apk_reports(self.signature, self.manifest, "", "0.2.0", 2)

    def test_metadata_rejects_stale_source_non_main_commits_and_non_increasing_codes(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / 'androidApp').mkdir()
            (root / 'androidApp/build.gradle.kts').write_text('versionCode = 2\nversionName = "0.2.0"\n')
            env = {'GITHUB_REPOSITORY': 'cmdr-chara/Songlass', 'GITHUB_EVENT_NAME': 'push',
                   'GITHUB_REF': 'refs/tags/v0.2.0', 'GITHUB_SHA': 'a' * 40}

            def fake_git(*args):
                if args[0] == 'rev-parse':
                    return 'a' * 40
                if args[0] == 'for-each-ref':
                    return 'refs/tags/v0.1.0'
                if args[0] == 'show':
                    return 'versionCode = 2\nversionName = "0.1.0"\n'
                raise AssertionError(args)

            with patch.object(release, 'ROOT', root), patch.dict(release.os.environ, env, clear=True), \
                    patch.object(release, 'git', side_effect=fake_git), patch.object(release.subprocess, 'run') as run:
                with self.assertRaisesRegex(ValueError, 'versionCode must exceed'):
                    release.metadata()
                with patch.dict(release.os.environ, {'GITHUB_SHA': 'b' * 40}):
                    with self.assertRaisesRegex(ValueError, 'differs from the workflow source'):
                        release.metadata()
                run.side_effect = subprocess.CalledProcessError(1, ['git', 'merge-base'])
                with self.assertRaises(subprocess.CalledProcessError):
                    release.metadata()


if __name__ == "__main__":
    unittest.main()
