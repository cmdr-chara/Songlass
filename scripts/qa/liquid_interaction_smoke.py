#!/usr/bin/env python3
"""Native Liquid press/release + tab drag evidence for an exact candidate APK.

Standalone mode installs the candidate and generates/scans Audit's original media.
Alternatively reuse a completed comparison on its still-running emulator.
Original PNGs remain unmodified; hashes are evidence, not visual assertions.
This does not test gesture cancellation or measure FPS.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

from platform_audit import Audit, PACKAGE, digest, save_json, validate_png


def navigation(audit: Audit, name: str) -> dict:
    remote = "/sdcard/songlass-liquid-smoke.xml"
    audit.shell("rm", "-f", remote)
    if "dumped to:" not in audit.shell("uiautomator", "dump", remote, timeout=30):
        raise RuntimeError("Fresh native hierarchy unavailable")
    xml = audit.shell("cat", remote)
    (audit.output / f"{name}.xml").write_text(xml)
    app_nodes = [node for node in ET.fromstring(xml).iter("node") if node.get("package") == PACKAGE]
    app_labels = {value for node in app_nodes for key in ("text", "content-desc") if (value := node.get(key))}
    if not any("Songlass Karaoke QA" in label for label in app_labels) or not {"Play", "Riproduci"}.intersection(app_labels) or {"Pause", "Pausa"}.intersection(app_labels):
        raise RuntimeError("The real QA track must remain visible and paused")
    matches = {"home": [], "library": []}
    for node in app_nodes:
        # Android may omit the click action from the selected tab's semantics.
        # The destination tab must still be actionable; never infer a disabled tab.
        if (node.get("enabled") != "true" or node.get("focusable") != "true"
                or (node.get("selected") != "true" and node.get("clickable") != "true")):
            continue
        labels = {child.get(key) for child in node.iter("node") for key in ("text", "content-desc")}
        for key, titles in (("home", {"Home"}), ("library", {"Library", "Libreria"})):
            if not titles.intersection(labels):
                continue
            bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
            if bounds:
                box = tuple(map(int, bounds.groups()))
                if 0 <= box[0] < box[2] <= audit.viewport[0] and audit.viewport[1] * .65 < box[1] < box[3] <= audit.viewport[1]:
                    matches[key].append({"bounds": box, "selected": node.get("selected") == "true"})
    if any(len(nodes) != 1 for nodes in matches.values()):
        raise RuntimeError("Expected one enabled Home and Library tab; unselected tabs must be clickable")
    result = {key: nodes[0] for key, nodes in matches.items()}
    home, library = result["home"]["bounds"], result["library"]["bounds"]
    if min(home[3], library[3]) <= max(home[1], library[1]) or not (home[2] <= library[0] or library[2] <= home[0]):
        raise RuntimeError("Tab bounds do not identify separate slots in the same capsule")
    return result


def health(audit: Audit, name: str) -> None:
    logs = audit.adb("logcat", "-d", "-v", "threadtime").decode("utf-8", errors="replace")
    (audit.output / f"{name}.log").write_text(logs)
    audit.assert_healthy(logs)


def frame(audit: Audit, name: str, evidence: list) -> None:
    png = audit.adb("exec-out", "screencap", "-p")
    validate_png(png, audit.viewport)
    path = audit.output / f"{name}.png"
    path.write_bytes(png)
    evidence.append({"frame": path.name, "sha256": digest(path), "monotonic_seconds": time.monotonic()})


def settled(audit: Audit, selected: str, name: str, evidence: list) -> dict:
    deadline, previous = time.monotonic() + 30, None
    while time.monotonic() < deadline:
        state = navigation(audit, name)
        correct = all(tab["selected"] == (key == selected) for key, tab in state.items())
        if correct and state == previous:
            health(audit, name)
            frame(audit, name, evidence)
            evidence[-1]["stable_tab_semantics"] = state
            evidence[-1]["paused_track"] = "Songlass Karaoke QA"
            return state
        previous = state
        time.sleep(.7)
    raise RuntimeError(f"Tab selection/bounds did not settle on {selected}")


def center(tab: dict) -> tuple[int, int]:
    left, top, right, bottom = tab["bounds"]
    return (left + right) // 2, (top + bottom) // 2


def gesture(audit: Audit, start: tuple, end: tuple, name: str, evidence: list, delay: float) -> None:
    command = ["adb", "-s", audit.serial, "shell",
               shlex.join(["input", "touchscreen", "swipe", *map(str, (*start, *end)), "1600"])]
    process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        time.sleep(delay)
        if process.poll() is not None:
            raise RuntimeError("Gesture ended before the requested held frame")
        frame(audit, name, evidence)
        if process.poll() is not None:
            raise RuntimeError("Gesture ended during capture; held-state evidence is inconclusive")
        evidence[-1]["input_active_before_and_after_capture"] = True
    finally:
        try:
            stdout, stderr = process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            process.communicate()
            raise RuntimeError("Input command did not finish; no gesture success claimed")
        (audit.output / f"{name}-input.log").write_text(shlex.join(command) + "\n" + (stdout + stderr).decode(errors="replace"))
    if process.returncode:
        raise RuntimeError(f"Input command failed: {process.returncode}")
    health(audit, name)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--audit-output", type=Path, help="Completed comparison on this running emulator")
    mode.add_argument("--candidate-apk", type=Path, help="Standalone mode: install this actual debug APK")
    parser.add_argument("--candidate-ref", help="Exact 40-character Git SHA for standalone mode")
    parser.add_argument("--appearance", choices=("light", "dark"), default="dark", help="Liquid appearance for the gesture evidence")
    parser.add_argument("--variant", choices=("normal", "reduced-motion", "reduced-transparency"), default="normal", help="Accessibility variant for the gesture evidence")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--avd-name")
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    standalone = args.candidate_apk is not None
    if standalone:
        if not args.candidate_apk.is_file() or not re.fullmatch(r"[0-9a-f]{40}", args.candidate_ref or ""):
            parser.error("Standalone mode requires an existing APK and its exact candidate Git SHA")
        execution = {"allow_software_cpu": False, "viewport": (1080, 2400), "density_dpi": 420}
        candidate = {"ref": args.candidate_ref, "sha256": digest(args.candidate_apk)}
    else:
        if args.candidate_ref:
            parser.error("--candidate-ref belongs to standalone mode")
        prior = json.loads((args.audit_output / "result.json").read_text())
        execution = json.loads((args.audit_output / "environment/execution.json").read_text())
        if prior["status"] != "CAPTURE_COMPLETE_REVIEW_PENDING" or execution["serial"] != args.serial:
            parser.error("A completed comparison for this exact emulator serial is required")
        candidate = json.loads((args.audit_output / "apks.json").read_text())["after"]
    if args.output.exists() and any(args.output.iterdir()):
        parser.error("Use a new output directory; existing evidence must not be overwritten")
    audit = Audit(args.output.resolve(), args.serial, avd_name=args.avd_name,
                  allow_software_cpu=execution["allow_software_cpu"], viewport=tuple(execution["viewport"]), density=execution["density_dpi"])
    evidence, failure, status = [], None, "FAILED"
    try:
        save_json(audit.output / "candidate.json", {"mode": "standalone" if standalone else "completed-comparison", **candidate})
        audit.guard_emulator()
        if standalone:
            audit.seed_media(audit.generate_fixtures())
            installed = audit.adb("install", "-r", "-t", str(args.candidate_apk.resolve()), timeout=180).decode()
            if "Success" not in installed:
                raise RuntimeError(f"Cannot install candidate APK: {installed}")
        apk = audit.shell("pm", "path", PACKAGE).splitlines()
        if len(apk) != 1 or not apk[0].startswith("package:") or audit.shell("sha256sum", apk[0][8:]).split()[0] != candidate["sha256"]:
            raise RuntimeError("Installed APK does not match the recorded candidate hash")
        profile = dict(style="liquid_glass", appearance=args.appearance, variant=args.variant, cases=("player", "home"))
        audit.configure(profile, audit.output)
        audit.stage("player", audit.output)
        audit.stage("home", audit.output)
        state = settled(audit, "home", "01-resting", evidence)
        gesture(audit, center(state["home"]), center(state["home"]), "02-pressed", evidence, .4)
        state = settled(audit, "home", "03-released", evidence)
        gesture(audit, center(state["home"]), center(state["library"]), "04-drag-library", evidence, .8)
        state = settled(audit, "library", "05-library", evidence)
        gesture(audit, center(state["library"]), center(state["home"]), "06-drag-home", evidence, .8)
        settled(audit, "home", "07-returned", evidence)
        status = "SMOKE_COMPLETE_REVIEW_PENDING"
    except Exception as exc:
        failure = f"{type(exc).__name__}: {exc}"
        (audit.output / "failure.txt").write_text(traceback.format_exc())
        try:
            health(audit, "failure")
        except Exception:
            pass
        try:
            frame(audit, "failure", [])
        except Exception:
            pass
    finally:
        save_json(audit.output / "result.json", {"status": status, "failure": failure, "evidence": evidence, "candidate": candidate,
                  "mode": "standalone" if standalone else "completed-comparison", "appearance": args.appearance, "variant": args.variant,
                  "visual_review": "pending; inspect full original PNGs for lens response and return to rest",
                  "stability_gate": "repeated tab selection and bounds; whole-frame hashes are informational",
                  "limitations": "No cancellation, frame-rate, or visual-equivalence assertion"})
        (audit.output / "summary.md").write_text(
            f"## Liquid interaction smoke\n\nStatus: **{status}**. Evidence frames: {len(evidence)}/7.\n\n"
            f"Candidate: `{candidate['ref']}`. APK SHA-256: `{candidate['sha256']}`.\n\n"
            f"Native press/release and Home → Library → Home drag on Liquid {args.appearance} ({args.variant}), with the real QA track paused. "
            "Inspect original PNGs for the optical response and return to rest; visual review remains pending. "
            "No baseline comparison, gesture cancellation or FPS claim is made by this smoke.\n"
            + (f"\nFailure: {failure}\n" if failure else ""), encoding="utf-8")
        audit.commands.close()
    print(status if failure is None else failure, flush=True)
    return 1 if failure else 0


if __name__ == "__main__":
    raise SystemExit(main())
