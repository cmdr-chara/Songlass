#!/usr/bin/env python3
"""Check Songlass community entrypoints and bundled license copies offline.

Uses only the Python standard library. All local links in the community entrypoints must resolve inside the checkout.
"""
from __future__ import annotations

import argparse
import hashlib
import re
from collections.abc import Callable
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit


REQUIRED_FILES = (
    "README.md",
    "docs/README.md",
    "AGENTS.md",
    "CLAUDE.md",
    "LICENSE",
    "LICENSING.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SECURITY.md",
    "SUPPORT.md",
    ".github/PULL_REQUEST_TEMPLATE.md",
    ".github/ISSUE_TEMPLATE/bug_report.yml",
    ".github/ISSUE_TEMPLATE/feature_request.yml",
    ".github/ISSUE_TEMPLATE/config.yml",
    ".editorconfig",
    ".gitattributes",
    ".gitignore",
)
LINK_DOCUMENTS = (
    "README.md",
    "docs/README.md",
    "AGENTS.md",
    "LICENSING.md",
    "CONTRIBUTING.md",
    "CODE_OF_CONDUCT.md",
    "SECURITY.md",
    "SUPPORT.md",
    ".github/PULL_REQUEST_TEMPLATE.md",
)
LICENSE_RESOURCES = "shared/src/commonMain/composeResources/files/licenses"
# Approved Songlass license text; changing the terms requires an explicit update.
SONGLASS_LICENSE_SHA256 = "8fa575afca9832880645234e9a278061a57f59997d6e3a902ce00328e26b817d"
INLINE_LINK = re.compile(
    r"!?\[[^\]\n]*\]\(\s*(<[^>\n]+>|[^\s)]+)(?:\s+['\"][^\n]*?['\"])?\s*\)"
)
REFERENCE_LINK = re.compile(r"(?m)^\s{0,3}\[[^\]\n]+\]:\s*(<[^>\n]+>|\S+)")


def prose_lines(text: str) -> str:
    """Omit hidden comments and fenced examples before checking rendered prose."""
    text = re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)
    result = []
    fence = None
    for line in text.splitlines():
        match = re.match(r"^\s{0,3}(`{3,}|~{3,})", line)
        if match:
            marker = match.group(1)
            if fence is None:
                fence = marker
            elif marker[0] == fence[0] and len(marker) >= len(fence):
                fence = None
            result.append("")
        elif fence is None:
            result.append(line)
    return "\n".join(result)


def markdown_targets(text: str) -> list[str]:
    prose = re.sub(r"`[^`\n]+`", "", prose_lines(text))
    return [match.group(1).strip("<>") for pattern in (INLINE_LINK, REFERENCE_LINK)
            for match in pattern.finditer(prose)]


class HtmlLinkParser(HTMLParser):
    """Collect static HTML links; script/style data and comments are not links."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.targets: list[str] = []
        self.ignored_element: str | None = None

    def handle_starttag(self, tag: str, attrs: list[tuple[str, str | None]]) -> None:
        if tag in ("script", "style"):
            self.ignored_element = tag
            return
        if self.ignored_element is not None:
            return
        attribute = {"img": "src", "a": "href"}.get(tag)
        for name, value in attrs:
            if name == attribute and value:
                self.targets.append(value)

    def handle_endtag(self, tag: str) -> None:
        if tag == self.ignored_element:
            self.ignored_element = None


def html_targets(text: str) -> list[str]:
    parser = HtmlLinkParser()
    parser.feed(re.sub(r"`[^`\n]+`", "", prose_lines(text)))
    parser.close()
    return parser.targets


def heading_ids(text: str) -> set[str]:
    """GitHub-style IDs for simple community headings, including duplicates."""
    ids = set(re.findall(r'<(?:a|span)\b[^>]*\b(?:id|name)=[\"\']([^\"\']+)', text))
    used: dict[str, int] = {}
    for line in prose_lines(text).splitlines():
        match = re.match(r"^\s{0,3}#{1,6}\s+(.+?)\s*#*\s*$", line)
        if not match:
            continue
        title = re.sub(r"<[^>]+>", "", match.group(1)).lower()
        slug = "".join(ch for ch in title if ch.isalnum() or ch.isspace() or ch in "-_")
        slug = re.sub(r"\s", "-", slug)
        number = used.get(slug, 0)
        candidate = slug if number == 0 else f"{slug}-{number}"
        while candidate in ids:
            number += 1
            candidate = f"{slug}-{number}"
        used[slug] = number + 1
        ids.add(candidate)
    return ids


def licensing_sections(text: str) -> tuple[str, str]:
    """Read the two authoritative bodies without their enclosing headings."""
    _, body = text.split("\n\n", 1)
    notice, body = body.split("\n## Contribution permission\n", 1)
    permission, _ = body.split("\n## Authoritative texts and source\n", 1)
    return notice.strip(), permission.strip()


def check_repository(
    root: Path,
    *,
    read_bytes: Callable[[Path], bytes] = Path.read_bytes,
    exists: Callable[[Path], bool] = Path.exists,
) -> tuple[list[str], int]:
    root = root.resolve()
    errors: list[str] = []
    texts: dict[str, str] = {}
    exempt_links = 0

    def read(relative: str) -> bytes | None:
        try:
            return read_bytes(root / relative)
        except (OSError, ValueError):
            errors.append(f"{relative}: cannot read required file")
            return None

    for relative in REQUIRED_FILES:
        raw = read(relative)
        if raw is None:
            continue
        try:
            text = raw.decode("utf-8")
        except UnicodeDecodeError:
            errors.append(f"{relative}: expected UTF-8 text")
            continue
        if not text.strip():
            errors.append(f"{relative}: required file is empty")
        texts[relative] = text

    license_bytes = read("LICENSE") if "LICENSE" in texts else None
    bundled_license = read(f"{LICENSE_RESOURCES}/songlass-SOURCE-AVAILABLE-1.0.txt")
    if license_bytes is not None:
        if hashlib.sha256(license_bytes).hexdigest() != SONGLASS_LICENSE_SHA256:
            errors.append("LICENSE: differs from the pinned Songlass Source-Available License 1.0 text")
        if bundled_license is not None and license_bytes != bundled_license:
            errors.append("Songlass bundled license differs from LICENSE")

    if "LICENSING.md" in texts:
        try:
            notice, permission = licensing_sections(texts["LICENSING.md"])
        except ValueError:
            errors.append("LICENSING.md: cannot identify the notice and contribution-permission sections")
        else:
            for name, expected in (("songlass-NOTICE.txt", notice),
                                   ("songlass-CONTRIBUTING-PERMISSION.txt", permission)):
                raw = read(f"{LICENSE_RESOURCES}/{name}")
                if raw is None:
                    continue
                try:
                    actual = raw.decode("utf-8").strip()
                except UnicodeDecodeError:
                    errors.append(f"{name}: expected UTF-8 text")
                    continue
                if actual != expected:
                    errors.append(f"{name}: differs from its authoritative LICENSING.md section")

    for relative in LINK_DOCUMENTS:
        if relative not in texts:
            continue
        document = root / relative
        for target in markdown_targets(texts[relative]) + html_targets(texts[relative]):
            try:
                parts = urlsplit(target)
            except ValueError:
                errors.append(f"{relative}: malformed link target: {target}")
                continue
            if parts.scheme or parts.netloc:
                continue
            path = unquote(parts.path)
            destination = (document.parent / path).resolve() if path else document
            try:
                local = destination.relative_to(root)
            except ValueError:
                errors.append(f"{relative}: local link escapes the repository: {target}")
                continue
            if not exists(destination):
                errors.append(f"{relative}: local link target does not exist: {target}")
                continue
            if parts.fragment and destination.suffix.lower() == ".md":
                try:
                    linked_text = read_bytes(destination).decode("utf-8")
                except (OSError, UnicodeDecodeError):
                    errors.append(f"{relative}: cannot read Markdown link target: {target}")
                    continue
                if unquote(parts.fragment) not in heading_ids(linked_text):
                    errors.append(f"{relative}: Markdown heading does not exist: {target}")
    return errors, exempt_links


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1],
                        help="repository root (default: this script's checkout)")
    args = parser.parse_args()
    if not args.root.is_dir():
        parser.error("repository root must be a directory")
    errors, exempt = check_repository(args.root)
    if errors:
        print(f"Repository hygiene failed ({len(errors)} issue(s)):")
        for error in errors:
            print(f"- {error}")
        return 1
    print("Repository hygiene passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
