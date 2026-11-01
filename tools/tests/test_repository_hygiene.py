"""Deciding success/failure cases for the offline checker; no fixture files are written."""
from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "check_repository_hygiene.py"
SPEC = importlib.util.spec_from_file_location("repository_hygiene", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
hygiene = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(hygiene)
CANONICAL = (SCRIPT.parents[1] / "LICENSE").read_bytes()


class RepositoryHygieneTest(unittest.TestCase):
    def setUp(self):
        self.root = Path("/songlass-hygiene-fixture").resolve()
        self.files = {self.root / name: b"# Document\n" for name in hygiene.REQUIRED_FILES}
        self.files[self.root / "LICENSE"] = CANONICAL
        self.files[self.root / "README.md"] = b"# Songlass\n\n[Terms](CONTRIBUTING.md#terms)\n"
        self.files[self.root / "CONTRIBUTING.md"] = b"# Contributing\n\n## Terms\n\nSubmit work.\n"
        self.files[self.root / "LICENSING.md"] = (
            b"# Songlass licensing\n\nRequired Notice: Fixture.\n\n"
            b"## Contribution permission\n\nContribution permission.\n\n"
            b"## Authoritative texts and source\n\nSources.\n"
        )
        resources = self.root / hygiene.LICENSE_RESOURCES
        self.files[resources / "songlass-SOURCE-AVAILABLE-1.0.txt"] = CANONICAL
        self.files[resources / "songlass-NOTICE.txt"] = b"Required Notice: Fixture.\n"
        self.files[resources / "songlass-CONTRIBUTING-PERMISSION.txt"] = b"Contribution permission.\n"

    def read(self, path):
        try:
            return self.files[path]
        except KeyError as error:
            raise FileNotFoundError(path) from error

    def check(self):
        return hygiene.check_repository(self.root, read_bytes=self.read, exists=self.files.__contains__)

    def add_readme_link(self, link):
        self.files[self.root / "README.md"] += link.encode("utf-8") + b"\n"

    def test_valid_repository_and_local_anchor(self):
        self.assertEqual(self.check(), ([], 0))

    def test_missing_required_document_fails(self):
        self.files.pop(self.root / "CODE_OF_CONDUCT.md")
        errors, _ = self.check()
        self.assertTrue(any("CODE_OF_CONDUCT.md: cannot read" in error for error in errors))

    def test_license_pin_detects_both_copies_changed_together(self):
        changed = CANONICAL + b"Altered terms.\n"
        self.files[self.root / "LICENSE"] = changed
        self.files[self.root / hygiene.LICENSE_RESOURCES / "songlass-SOURCE-AVAILABLE-1.0.txt"] = changed
        errors, _ = self.check()
        self.assertTrue(any("pinned Songlass" in error for error in errors))

    def test_bundled_license_drift_fails(self):
        self.files[self.root / hygiene.LICENSE_RESOURCES / "songlass-SOURCE-AVAILABLE-1.0.txt"] = b"Wrong license\n"
        errors, _ = self.check()
        self.assertIn("Songlass bundled license differs from LICENSE", errors)

    def test_notice_and_contribution_permission_drift_fail(self):
        for name in ("songlass-NOTICE.txt", "songlass-CONTRIBUTING-PERMISSION.txt"):
            with self.subTest(name=name):
                path = self.root / hygiene.LICENSE_RESOURCES / name
                original = self.files[path]
                self.files[path] = b"Different terms.\n"
                errors, _ = self.check()
                self.assertTrue(any(name + ": differs" in error for error in errors))
                self.files[path] = original

    def test_missing_local_link_fails(self):
        self.add_readme_link("[Missing](docs/missing.md)")
        errors, _ = self.check()
        self.assertTrue(any("target does not exist" in error for error in errors))

    def test_documentation_index_links_are_checked(self):
        self.files[self.root / "docs/README.md"] = b"# Documentation\n\n[Missing](missing.md)\n"
        errors, _ = self.check()
        self.assertTrue(any("docs/README.md: local link target does not exist" in error for error in errors))

    def test_html_screenshot_and_anchor_link_are_checked(self):
        self.files[self.root / "docs/images/screenshots/home.png"] = b"image fixture"
        self.add_readme_link(
            '<a href="CONTRIBUTING.md#terms"><img src="docs/images/screenshots/home.png" '
            'width="240" alt="Songlass home"></a>'
        )
        self.assertEqual(self.check(), ([], 0))

    def test_missing_html_screenshot_fails(self):
        self.add_readme_link('<img src="docs/images/screenshots/missing.png" width="240" alt="Missing">')
        errors, _ = self.check()
        self.assertTrue(any("target does not exist: docs/images/screenshots/missing.png" in error for error in errors))

    def test_missing_html_anchor_link_fails(self):
        self.add_readme_link('<a href="CONTRIBUTING.md#missing">Missing heading</a>')
        errors, _ = self.check()
        self.assertTrue(any("heading does not exist: CONTRIBUTING.md#missing" in error for error in errors))

    def test_html_screenshot_cannot_escape_checkout(self):
        self.add_readme_link('<img src="../outside.png" alt="Outside">')
        errors, _ = self.check()
        self.assertTrue(any("escapes the repository: ../outside.png" in error for error in errors))

    def test_html_links_in_comments_and_scripts_are_ignored(self):
        self.add_readme_link(
            '<!-- <img src="comment.png"><a href="comment.md">Example</a> -->\n'
            '<script>const example = \'<img src="script.png"><a href="script.md">Example</a>\';</script>'
        )
        self.assertEqual(self.check(), ([], 0))

    def test_html_links_in_code_examples_are_ignored(self):
        self.add_readme_link('```html\n<img src="fenced.png">\n```\n`<a href="inline.md">Example</a>`')
        self.assertEqual(self.check(), ([], 0))

    def test_missing_markdown_heading_fails(self):
        self.add_readme_link("[Missing heading](CONTRIBUTING.md#absent)")
        errors, _ = self.check()
        self.assertTrue(any("heading does not exist" in error for error in errors))

    def test_link_cannot_escape_checkout(self):
        self.add_readme_link("[Outside](../outside.md)")
        errors, _ = self.check()
        self.assertTrue(any("escapes the repository" in error for error in errors))

    def test_missing_local_collection_is_not_exempt(self):
        self.add_readme_link("[Assets](local-assets/README.md)")
        errors, count = self.check()
        self.assertTrue(any("target does not exist" in error for error in errors))
        self.assertEqual(count, 0)

    def test_external_links_need_no_network_access(self):
        self.add_readme_link("[External](https://example.invalid/not-requested)")
        self.add_readme_link("[Mail](mailto:example@example.invalid)")
        self.assertEqual(self.check(), ([], 0))

    def test_percent_encoded_space_in_relative_target(self):
        self.files[self.root / "docs/with space.md"] = b"# Page\n"
        self.add_readme_link("[Page](docs/with%20space.md#page)")
        self.assertEqual(self.check(), ([], 0))

    def test_examples_in_code_are_not_live_links(self):
        self.add_readme_link("```md\n[Example](not-a-file.md)\n```\n`[Example](also-not-a-file.md)`")
        self.assertEqual(self.check(), ([], 0))

    def test_hidden_template_examples_are_not_live_links(self):
        self.add_readme_link("<!-- Replace ![Before](before.png) with a real screenshot. -->")
        self.assertEqual(self.check(), ([], 0))

    def test_reference_style_link_is_checked(self):
        self.add_readme_link("[missing]: missing.md")
        errors, _ = self.check()
        self.assertTrue(any("missing.md" in error for error in errors))

    def test_duplicate_headings_and_explicit_anchor(self):
        text = '# Test\n\n## Same\n\n## Same\n\n<a id="manual"></a>\n'
        self.assertEqual(hygiene.heading_ids(text), {"test", "same", "same-1", "manual"})

    def test_malformed_external_url_reports_error(self):
        self.add_readme_link("[Bad](https://[invalid)")
        errors, _ = self.check()
        self.assertTrue(any("malformed link" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
