#!/usr/bin/env python3
"""Rebuild Songlass's offline AutoEQ index from one immutable upstream revision (stdlib only)."""
import argparse
import re
import urllib.request
from pathlib import Path
from urllib.parse import quote, unquote

PINNED_REVISION = "7ae0f56d53074872b028649617a22bbb4232feb7"


def generate(readme, revision):
    entries = []
    for line in readme.splitlines():
        if not line.startswith("- ["):
            continue
        match = re.fullmatch(r"- \[(.*)\]\(\./(.*)\)", line)
        if not match:
            raise ValueError("Unexpected AutoEQ recommended-list row")
        name, raw = match.groups()
        parts = [unquote(part) for part in raw.split("/")]
        if len(parts) != 3 or any(not part or part in (".", "..") or "/" in part or "\\" in part for part in parts):
            raise ValueError("Unexpected AutoEQ result path")
        path = "/".join(quote(part, safe="") for part in parts)
        if any(ch in name + parts[0] for ch in "\n\r\t"):
            raise ValueError("Invalid AutoEQ name")
        entries.append((name, parts[0], path))
    if not entries or len({row[2] for row in entries}) != len(entries):
        raise ValueError("Empty or duplicate AutoEQ index")
    return "# revision=" + revision + "\n" + "\n".join("\t".join(row) for row in entries) + "\n", len(entries)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--revision", default=PINNED_REVISION)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-f0-9]{40}", args.revision):
        parser.error("revision must be a complete lowercase Git SHA")
    upstream = "https://raw.githubusercontent.com/jaakkopasanen/AutoEq/" + args.revision + "/"
    def fetch(path):
        with urllib.request.urlopen(upstream + path, timeout=40) as response:
            return response.read(2 * 1024 * 1024).decode("utf-8")
    readme, license_text = fetch("results/README.md"), fetch("LICENSE")
    catalog, count = generate(readme, args.revision)
    if not license_text.startswith("MIT License\n"):
        raise ValueError("Review the changed upstream license before updating")
    root = Path(__file__).resolve().parents[1]
    dest = root / "shared/src/commonMain/composeResources/files"
    notice = ("AutoEQ recommended results, by Jaakko Pasanen.\n"
              "Source: https://github.com/jaakkopasanen/AutoEq/tree/" + args.revision + "/results\n"
              "Catalog derived from results/README.md; correction files downloaded on demand.\n"
              "Only model names, measurement-source names and result paths are bundled.\n"
              "Original raw frequency-response measurements and images are not redistributed.\n"
              "License: MIT; see autoeq-MIT.txt.\n")
    for relative, body in [("autoeq/catalog.tsv", catalog), ("licenses/autoeq-MIT.txt", license_text), ("licenses/autoeq-NOTICE.txt", notice)]:
        target = dest / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body, encoding="utf-8")
    print(f"AutoEQ {args.revision}: {count} recommended profiles, {len(catalog.encode('utf-8'))} catalog bytes")


if __name__ == "__main__":
    main()
