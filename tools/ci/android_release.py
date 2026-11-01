#!/usr/bin/env python3
"""Validate Android release inputs and stage a verified APK using the standard library."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
VERSION = r"(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)\.(?:0|[1-9]\d*)(?:-(?:alpha|beta|rc)\.(?:0|[1-9]\d*))?"


def read_version(text: str) -> tuple[str, int]:
    names = re.findall(r'^\s*versionName\s*=\s*"([^"\n]+)"\s*$', text, re.MULTILINE)
    codes = re.findall(r"^\s*versionCode\s*=\s*(\d+)\s*$", text, re.MULTILINE)
    if len(names) != 1 or len(codes) != 1:
        raise ValueError("Expected one literal versionName and versionCode in androidApp/build.gradle.kts")
    if not re.fullmatch(VERSION, names[0]) or not 1 <= int(codes[0]) <= 2_100_000_000:
        raise ValueError("Invalid Android version: use SemVer (optional alpha.N/beta.N/rc.N) and a positive versionCode")
    return names[0], int(codes[0])


def validate_event(event: str, ref: str, name: str) -> None:
    if event == "push" and ref == f"refs/tags/v{name}":
        return
    if event == "workflow_dispatch" and ref == "refs/heads/main":
        return
    raise ValueError("Release builds require a v<versionName> tag or a manual run on main")


def git(*args: str) -> str:
    return subprocess.check_output(["git", "-C", str(ROOT), *args], text=True).strip()


def metadata() -> tuple[str, int]:
    name, code = read_version((ROOT / "androidApp/build.gradle.kts").read_text())
    validate_event(os.environ.get("GITHUB_EVENT_NAME", ""), os.environ.get("GITHUB_REF", ""), name)
    if os.environ.get("GITHUB_REPOSITORY") != "cmdr-chara/Songlass":
        raise ValueError("Official signing is restricted to cmdr-chara/Songlass")
    subprocess.run(["git", "-C", str(ROOT), "merge-base", "--is-ancestor", "HEAD", "origin/main"], check=True)
    if os.environ.get("GITHUB_SHA") != git("rev-parse", "HEAD"):
        raise ValueError("Checked-out commit differs from the workflow source")
    for ref in git("for-each-ref", "--format=%(refname)", "refs/tags/v*").splitlines():
        tag = ref.removeprefix("refs/tags/")
        if tag == f"v{name}" or not re.fullmatch("v" + VERSION, tag):
            continue
        previous_name, previous_code = read_version(git("show", f"{ref}:androidApp/build.gradle.kts"))
        if code <= previous_code:
            raise ValueError(f"versionCode must exceed {previous_code} used by {tag} ({previous_name})")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as stream:
            stream.write(f"tag=v{name}\nversion_name={name}\nversion_code={code}\n")
    print(f"Validated v{name}, versionCode {code}, source {git('rev-parse', 'HEAD')}")
    return name, code


def verify_apk_reports(signature: str, manifest: str, expected_certificate: str, name: str, code: int) -> None:
    certificate = expected_certificate.strip().lower()
    if not re.fullmatch(r"[0-9a-f]{64}", certificate):
        raise ValueError("Invalid pinned release certificate")
    signers = re.findall(r"^Signer #\d+ certificate SHA-256 digest: ([0-9a-f]+)$", signature, re.MULTILINE | re.IGNORECASE)
    if len(signers) != 1 or signers[0].lower() != certificate:
        raise ValueError("APK certificate differs from the pinned release key")
    package = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'", manifest, re.MULTILINE)
    if not package or package.groups() != ("dev.cmdrchara.songlass", str(code), name):
        raise ValueError("APK package/version differs from the declared release")
    if re.search(r"^application-debuggable(?:\s|$)", manifest, re.MULTILINE):
        raise ValueError("A debuggable APK cannot be released")


def package() -> None:
    name, code = metadata()
    sdk = Path(os.environ["ANDROID_HOME"]) / "build-tools/37.0.0"
    apk = ROOT / "androidApp/build/outputs/apk/release/androidApp-release.apk"
    signature = subprocess.check_output([str(sdk / "apksigner"), "verify", "--verbose", "--print-certs", str(apk)], text=True)
    manifest = subprocess.check_output([str(sdk / "aapt2"), "dump", "badging", str(apk)], text=True)
    certificate = (ROOT / "androidApp/release-signing-certificate.sha256").read_text().strip()
    verify_apk_reports(signature, manifest, certificate, name, code)
    destination = ROOT / "build/release-assets"
    destination.mkdir(parents=True, exist_ok=False)
    filename = f"Songlass-v{name}.apk"
    shutil.copyfile(apk, destination / filename)
    info = {
        "version_name": name, "version_code": code,
        "package": "dev.cmdrchara.songlass", "source_commit": git("rev-parse", "HEAD"),
        "certificate_sha256": certificate,
        "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
        "workflow_run": os.environ.get("GITHUB_RUN_ID"),
    }
    (destination / "build-info.json").write_text(json.dumps(info, indent=2) + "\n")
    with (destination / "SHA256SUMS").open("w") as stream:
        for path in [destination / filename, destination / "build-info.json"]:
            stream.write(f"{hashlib.sha256(path.read_bytes()).hexdigest()}  {path.name}\n")
    print(f"Verified release certificate and APK; staged {filename} and SHA256SUMS")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["metadata", "package"])
    args = parser.parse_args()
    try:
        metadata() if args.command == "metadata" else package()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Android release validation failed: {error}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
