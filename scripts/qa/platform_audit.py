#!/usr/bin/env python3
"""Capture real before/after Android UI; a complete capture is not visual approval.

Requires a disposable API 33+ emulator, KVM by default, emulator '-gpu host',
adb, ffmpeg/ffprobe and the existing debug VisualAuditActivity in both APKs.
Never targets a physical device. Only original, locally synthesized media is used.
Use --fixtures-only to verify media generation without an Android device.
An explicit --allow-software-cpu permits TCG for native screenshot evidence only;
it does not change the app's graphics, health gates or visual review requirement.

References:
https://developer.android.com/studio/run/emulator-acceleration
https://github.com/ReactiveCircus/android-emulator-runner/tree/a421e43855164a8197daf9d8d40fe71c6996bb0d
"""

from __future__ import annotations

import argparse
import hashlib
import html
import json
import math
import os
from pathlib import Path
import re
import shlex
import shutil
import struct
import subprocess
import sys
import time
import traceback
import wave
import xml.etree.ElementTree as ET
import zlib


PACKAGE = "dev.cmdrchara.songlass"
ACTIVITY = f"{PACKAGE}/.debug.VisualAuditActivity"
BASELINE = "0ffb850f563373ae9bb05267718cd9e03b4ec1a9"
MEDIA_DIR = "/storage/emulated/0/Music/SonglassPlatformAudit"
STYLES = ("liquid_glass", "material_glass", "miuix")
TITLES = (
    "Songlass Karaoke QA",
    "Songlass — Luce del mattino",
    "Songlass — Una strada lunga verso il mare",
    "Songlass — Ombre e riflessi",
    "Songlass — Notturno in movimento",
    "Songlass — Ultima luce",
)


def matrix() -> list[dict]:
    profiles = []
    for style in STYLES:
        for appearance in ("light", "dark"):
            cases = ("player", "home", "library", "appearance")
            if appearance == "light" and style in ("liquid_glass", "miuix"):
                cases += ("album",)
            profiles.append(dict(style=style, appearance=appearance, variant="normal",
                                 cases=cases))
        profiles.append(dict(style=style, appearance="light", variant="large-font",
                             cases=("library", "appearance")))
        profiles.append(dict(style=style, appearance="dark", variant="reduced-motion",
                             cases=("player",)))
        profiles.append(dict(style=style, appearance="dark", variant="reduced-transparency",
                             cases=("home",)))
    return profiles


def digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def save_json(path: Path, value: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


class Audit:
    def __init__(self, output: Path, serial: str, *, allow_software_cpu: bool = False,
                 avd_name: str | None = None, viewport: tuple[int, int] = (1080, 2400), density: int = 420):
        self.output = output
        self.serial = serial
        self.allow_software_cpu = allow_software_cpu
        self.avd_name = avd_name
        self.viewport = viewport
        self.density = density
        self.entries: list[dict] = []
        self.output.mkdir(parents=True, exist_ok=True)
        self.commands = (output / "commands.log").open("a", encoding="utf-8")

    def run(self, args: list[str], *, data: bytes | None = None, timeout: int = 60,
            check: bool = True) -> bytes:
        self.commands.write(f"\n$ {shlex.join(map(str, args))}\n")
        self.commands.flush()
        result = subprocess.run(args, input=data, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout, check=False)
        self.commands.write(result.stderr.decode("utf-8", errors="replace"))
        if result.returncode:
            self.commands.write(result.stdout.decode("utf-8", errors="replace"))
            self.commands.flush()
            if check:
                raise RuntimeError(f"Command failed ({result.returncode}): {shlex.join(args)}")
        return result.stdout

    def adb(self, *args: str, **kwargs) -> bytes:
        return self.run(["adb", "-s", self.serial, *args], **kwargs)

    def shell(self, *args: str, **kwargs) -> str:
        # adb shell itself invokes a shell. Quote once for the remote shell,
        # including SQL, preferences and command arguments with spaces.
        return self.adb("shell", shlex.join(args), **kwargs).decode("utf-8", errors="replace").strip()

    def guard_emulator(self) -> None:
        if not re.fullmatch(r"emulator-\d+", self.serial):
            raise RuntimeError("Refusing to modify anything except an explicitly selected emulator")
        if self.shell("getprop", "ro.kernel.qemu") != "1":
            raise RuntimeError("Selected device is not an Android emulator")
        if int(self.shell("getprop", "ro.build.version.sdk")) < 33:
            raise RuntimeError("API 33 or newer is required")
        processes = self.run(["ps", "-eo", "args"]).decode()
        port = self.serial.removeprefix("emulator-")
        candidates = []
        for line in processes.splitlines():
            try:
                command = shlex.split(line)
            except ValueError:
                continue
            if not command or not (Path(command[0]).name.startswith("qemu-system-") or Path(command[0]).name == "emulator"):
                continue
            def option(flag: str, fallback: str = "") -> str:
                return command[command.index(flag) + 1] if flag in command and command.index(flag) + 1 < len(command) else fallback
            process_port = option("-port", option("-ports", "5554").split(",")[0])
            process_avd = option("-avd", next((arg[1:] for arg in command if arg.startswith("@")), ""))
            if process_port == port and (not self.avd_name or process_avd == self.avd_name):
                candidates.append((line, option("-gpu"), option("-accel", "off" if "-no-accel" in command else "")))
        if not candidates or any(gpu != "host" for _, gpu, _ in candidates):
            raise RuntimeError("Cannot verify the selected emulator serial uses '-gpu host'")
        modes = {accel for _, _, accel in candidates}
        if modes == {"on"}:
            if not os.access("/dev/kvm", os.R_OK | os.W_OK):
                raise RuntimeError("KVM was requested but is unavailable")
            cpu_mode = "KVM"
        elif modes == {"off"} and self.allow_software_cpu:
            cpu_mode = "TCG (software CPU; static native UI evidence only, no performance claim)"
        else:
            raise RuntimeError("Require '-accel on' with KVM, or explicit --allow-software-cpu with '-accel off'")
        emulator_commands = "\n".join(line for line, _, _ in candidates)
        renderer_dump = self.shell("dumpsys", "SurfaceFlinger")
        renderer = "\n".join(line for line in renderer_dump.splitlines() if "GLES:" in line)
        if not renderer or "swiftshader" in renderer.lower():
            raise RuntimeError(f"Host GLES renderer unavailable or SwiftShader selected: {renderer!r}")
        env = self.output / "environment"
        env.mkdir(exist_ok=True)
        (env / "emulator-command.txt").write_text(emulator_commands + "\n")
        (env / "surfaceflinger.txt").write_text(renderer_dump)
        (env / "device-properties.txt").write_text(self.shell("getprop"))
        (env / "renderer.txt").write_text(renderer + "\n")
        save_json(env / "execution.json", {"serial": self.serial, "cpu_mode": cpu_mode,
                                           "allow_software_cpu": self.allow_software_cpu,
                                           "gpu_mode": "host", "gles": renderer,
                                           "viewport": self.viewport, "density_dpi": self.density,
                                           "app_graphics_quality": "unchanged"})
        emulator = Path(os.environ.get("ANDROID_SDK_ROOT", os.environ.get("ANDROID_HOME", ""))) / "emulator/emulator"
        if not emulator.is_file():
            raise RuntimeError("Cannot locate SDK emulator to record its version and KVM status")
        (env / "emulator-version.txt").write_bytes(self.run([str(emulator), "-version"]))
        (env / "acceleration.txt").write_bytes(self.run([str(emulator), "-accel-check"], check=cpu_mode == "KVM"))
        self.shell("wm", "size", f"{self.viewport[0]}x{self.viewport[1]}")
        self.shell("wm", "density", str(self.density))
        self.shell("settings", "put", "system", "accelerometer_rotation", "0")
        self.shell("settings", "put", "system", "user_rotation", "0")
        self.shell("settings", "put", "system", "screen_off_timeout", "1800000")
        self.shell("svc", "power", "stayon", "true")
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")
        self.shell("settings", "put", "global", "sysui_demo_allowed", "1")
        for values in (("command", "enter"), ("command", "clock", "hhmm", "0941"),
                       ("command", "battery", "level", "100", "plugged", "false"),
                       ("command", "notifications", "visible", "false")):
            command = ["am", "broadcast", "-a", "com.android.systemui.demo"]
            for index in range(0, len(values), 2):
                command.extend(("--es", values[index], values[index + 1]))
            self.shell(*command)

    def generate_fixtures(self) -> list[Path]:
        destination = self.output / "fixtures"
        destination.mkdir(exist_ok=True)
        files = []
        provenance = []
        for index, title in enumerate(TITLES):
            stem = f"songlass-{index + 1:02d}"
            wav = destination / f"{stem}.wav"
            cover = destination / f"{stem}.png"
            flac = destination / f"{stem}.flac"
            rate, duration, frequency = 44100, 40, 220 + index * 33
            # Original quiet harmonic tones, not a recording or a copied song.
            one_second = b"".join(struct.pack("<h", round(2400 * math.sin(2 * math.pi * frequency * frame / rate)
                                                         + 600 * math.sin(4 * math.pi * frequency * frame / rate)))
                                   for frame in range(rate))
            with wave.open(str(wav), "wb") as recording:
                recording.setparams((1, 2, rate, 0, "NONE", "not compressed"))
                for _ in range(duration):
                    recording.writeframes(one_second)
            write_original_cover(cover, index // 2)
            album = ("Songlass · Alba", "Songlass · Riflessi", "Songlass · Notturno")[index // 2]
            artist = "Songlass Studio" if index < 4 else "Songlass Ensemble"
            self.run(["ffmpeg", "-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                      "-i", str(wav), "-i", str(cover), "-map", "0:a:0", "-map", "1:v:0",
                      "-c:a", "flac", "-c:v", "copy", "-disposition:v:0", "attached_pic",
                      "-metadata", f"title={title}", "-metadata", f"artist={artist}",
                      "-metadata", f"album_artist={artist}", "-metadata", f"album={album}",
                      "-metadata", f"track={index % 2 + 1}", "-metadata", "date=2026",
                      "-metadata", "genre=Original QA synthesis", "-metadata:s:v:0", "title=Cover (front)",
                      "-metadata:s:v:0", "comment=Cover (front)", str(flac)])
            probe = json.loads(self.run(["ffprobe", "-v", "error", "-show_format", "-show_streams",
                                        "-of", "json", str(flac)]))
            tags = {key.lower(): value for key, value in probe["format"]["tags"].items()}
            if tags.get("title") != title or abs(float(probe["format"]["duration"]) - duration) > .01:
                raise RuntimeError(f"Generated FLAC validation failed: {flac}")
            if not any(stream.get("disposition", {}).get("attached_pic") == 1 for stream in probe["streams"]):
                raise RuntimeError(f"Generated FLAC has no embedded cover: {flac}")
            wav.unlink()
            os.utime(flac, (1767225600, 1767225600))
            files.append(flac)
            provenance.append(dict(file=flac.name, sha256=digest(flac), title=title, artist=artist,
                                   album=album, duration_seconds=duration, sample_rate=rate,
                                   synthesis_hz=frequency, cover_sha256=digest(cover), ffprobe=probe))
        save_json(destination / "provenance.json", {
            "origin": "Original deterministic harmonic tones and procedural geometric covers generated by this script; no external media.",
            "comparison": "The exact same six FLAC files and MediaStore rows are used for both APKs.",
            "fixtures": provenance,
        })
        return files

    def seed_media(self, files: list[Path]) -> None:
        self.shell("mkdir", "-p", MEDIA_DIR)
        for path in files:
            remote = f"{MEDIA_DIR}/{path.name}"
            self.adb("push", "-a", str(path), remote)
            remote_hash = self.shell("sha256sum", remote).split()[0]
            if remote_hash != digest(path):
                raise RuntimeError(f"Device fixture differs from original: {remote}")
            self.shell("am", "broadcast", "-a", "android.intent.action.MEDIA_SCANNER_SCAN_FILE",
                       "-d", f"file://{remote}")
        deadline = time.monotonic() + 60
        query = ""
        while time.monotonic() < deadline:
            query = self.shell("content", "query", "--uri", "content://media/external/audio/media",
                               "--projection", "_id:title:duration:_data", "--where", f"_data LIKE '{MEDIA_DIR}/%'")
            if all(title in query for title in TITLES):
                (self.output / "environment" / "mediastore.txt").write_text(query)
                return
            time.sleep(1)
        raise RuntimeError(f"MediaStore did not index all six original FLAC fixtures: {query}")

    def configure(self, profile: dict, destination: Path) -> None:
        self.shell("am", "force-stop", PACKAGE)
        if self.shell("pm", "clear", PACKAGE) != "Success":
            raise RuntimeError("Unable to reset disposable app data between profiles")
        self.shell("pm", "grant", PACKAGE, "android.permission.READ_MEDIA_AUDIO")
        font = "1.5" if profile["variant"] == "large-font" else "1.0"
        animation = "0" if profile["variant"] == "reduced-motion" else "1"
        self.shell("settings", "put", "system", "font_scale", font)
        if float(self.shell("settings", "get", "system", "font_scale")) != float(font):
            raise RuntimeError("Font scale did not apply")
        for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
            self.shell("settings", "put", "global", key, animation)
            if float(self.shell("settings", "get", "global", key)) != float(animation):
                raise RuntimeError(f"Animation scale did not apply: {key}")
        self.shell("cmd", "uimode", "night", "yes" if profile["appearance"] == "dark" else "no")
        prefs = {
            "settings.appearance": profile["appearance"],
            "settings.interface_style": profile["style"],
            "settings.reduce_motion": str(profile["variant"] == "reduced-motion").lower(),
            "settings.reduce_transparency": str(profile["variant"] == "reduced-transparency").lower(),
            "settings.artwork_style": "full_bleed",
            "settings.amll_enabled": "false",
            "settings.lrclib_enabled": "false",
        }
        root = ET.Element("map")
        for key, value in prefs.items():
            ET.SubElement(root, "string", name=key).text = value
        xml = ET.tostring(root, encoding="utf-8", xml_declaration=True)
        # Only this disposable, stopped debug app is changed; no production
        # code, fake graph, exported preferences API or runtime dependency.
        self.shell("run-as", PACKAGE, "sh", "-c",
                   "mkdir -p shared_prefs && cat > shared_prefs/songlass.xml", data=xml)
        actual = self.shell("run-as", PACKAGE, "cat", "shared_prefs/songlass.xml")
        readback = {node.attrib["name"]: node.text for node in ET.fromstring(actual)}
        if readback != prefs:
            raise RuntimeError("Preference readback differs from requested profile")
        destination.mkdir(parents=True, exist_ok=True)
        save_json(destination / "profile.json", {**profile, "font_scale": font,
                                                "system_animation_scale": animation, "preferences": readback})
        (destination / "preferences.xml").write_text(actual)

    def stage(self, case: str, destination: Path) -> None:
        self.adb("logcat", "-c")
        started = self.shell("am", "start", "-W", "-n", ACTIVITY, "--es", "case", case)
        (destination / f"{case}-launch.txt").write_text(started)
        if "Error:" in started or "Exception" in started:
            raise RuntimeError(f"VisualAuditActivity did not start: {started}")
        deadline = time.monotonic() + 60
        logs = ""
        while time.monotonic() < deadline:
            logs = self.adb("logcat", "-d", "-v", "threadtime").decode("utf-8", errors="replace")
            self.assert_healthy(logs)
            ready = re.search(rf"VisualAudit\s*:\s*READY {re.escape(case)} tracks=(\d+) albums=(\d+)", logs)
            if ready:
                if int(ready.group(1)) != len(TITLES) or int(ready.group(2)) != 3:
                    raise RuntimeError(f"Unexpected library, expected six real tracks/three albums: {ready.group(0)}")
                (destination / f"{case}-ready.log").write_text(logs)
                return
            time.sleep(.5)
        (destination / f"{case}-timeout.log").write_text(logs)
        raise RuntimeError(f"Timed out waiting for a real READY {case}; no placeholder capture accepted")

    @staticmethod
    def assert_healthy(logs: str) -> None:
        if re.search(r"VisualAudit\s*:\s*UNAVAILABLE\b", logs):
            raise RuntimeError("VisualAuditActivity reported UNAVAILABLE")
        if re.search(r"NavigationGlass\s*:\s*(?:Custom|Ink) shader unavailable\b", logs):
            raise RuntimeError("Navigation glass shader unavailable; an unintended fallback is not valid optical evidence")
        if "FATAL EXCEPTION" in logs or re.search(r"\bANR in\b", logs):
            raise RuntimeError("Emulator/app crash or ANR detected; capture is invalid")

    def capture(self, phase: str, profile: dict, case: str, destination: Path) -> None:
        self.stage(case, destination)
        # Paused audio, local embedded artwork and disabled remote lyrics give a static scene.
        # Wait for three identical frames, not just the READY log emitted before
        # Compose has necessarily drawn the new route and loaded its cover.
        previous, repeats = b"", 0
        deadline = time.monotonic() + 20
        time.sleep(1)
        while time.monotonic() < deadline:
            frame = self.adb("exec-out", "screencap", "-p")
            validate_png(frame, self.viewport)
            repeats = repeats + 1 if frame == previous else 0
            previous = frame
            if repeats >= 2:
                break
            time.sleep(.7)
        else:
            (destination / f"{case}-unstable.png").write_bytes(previous)
            raise RuntimeError(f"Scene did not settle: {profile['style']}/{profile['variant']}/{case}")
        # An unsuccessful dump can exit without replacing an old XML file.
        # Remove it first so a previous screen cannot satisfy this screen's gate.
        self.shell("rm", "-f", "/sdcard/songlass-platform-audit.xml")
        dumped = self.shell("uiautomator", "dump", "/sdcard/songlass-platform-audit.xml", timeout=30)
        if "dumped to:" not in dumped:
            raise RuntimeError(f"Native hierarchy dump failed: {dumped}")
        hierarchy = self.shell("cat", "/sdcard/songlass-platform-audit.xml")
        nodes = [node for node in ET.fromstring(hierarchy).iter("node") if node.get("package") == PACKAGE]
        labels = [value for node in nodes for key in ("text", "content-desc") if (value := node.get(key))]
        if len(labels) < 3:
            raise RuntimeError("Missing accessible Songlass content in the native hierarchy")
        required = {
            "home": ("Home",),
            "library": ("Library", "Libreria"),
            "appearance": ("Appearance", "Aspetto"),
            "player": ("Songlass Karaoke QA",),
            "album": ("Songlass · Alba", "Songlass · Riflessi", "Songlass · Notturno"),
        }[case]
        if not any(any(expected in label for expected in required) for label in labels):
            raise RuntimeError(f"Unexpected route hierarchy for {case}: {labels}")
        top = self.shell("dumpsys", "activity", "activities")
        if not any(("mResumedActivity" in line or "topResumedActivity" in line) and "VisualAuditActivity" in line for line in top.splitlines()):
            raise RuntimeError("VisualAuditActivity is not the resumed native activity")
        logs = self.adb("logcat", "-d", "-v", "threadtime").decode("utf-8", errors="replace")
        self.assert_healthy(logs)
        screenshot = destination / f"{case}.png"
        screenshot.write_bytes(previous)
        (destination / f"{case}.xml").write_text(hierarchy)
        (destination / f"{case}.log").write_text(logs)
        self.entries.append(dict(phase=phase, profile=profile_name(profile), case=case,
                                 screenshot=str(screenshot.relative_to(self.output)), sha256=digest(screenshot),
                                 accessible_labels=labels))
        save_json(self.output / "captures.json", self.entries)
        print(f"Captured {phase}/{profile_name(profile)}/{case}", flush=True)

    def compare(self, before: Path, after: Path) -> None:
        if (self.output / "before").exists() or (self.output / "after").exists():
            raise RuntimeError("Refusing to mix existing screenshots with this audit; use a new output directory")
        self.guard_emulator()
        files = self.generate_fixtures()
        self.seed_media(files)
        for phase, apk in (("before", before), ("after", after)):
            installed = self.adb("install", "-r", "-t", str(apk), timeout=180).decode()
            if "Success" not in installed:
                raise RuntimeError(f"Cannot install {phase} APK: {installed}")
            for profile in matrix():
                destination = self.output / phase / profile_name(profile)
                self.configure(profile, destination)
                if profile["cases"][0] != "player":
                    # Seed the same real paused track, so home/library include
                    # the navigation + mini-player relationship in both builds.
                    self.stage("player", destination)
                for case in profile["cases"]:
                    self.capture(phase, profile, case, destination)
            for file in files:
                if self.shell("sha256sum", f"{MEDIA_DIR}/{file.name}").split()[0] != digest(file):
                    raise RuntimeError("A fixture changed during the audit")
        expected = 2 * sum(len(profile["cases"]) for profile in matrix())
        if len(self.entries) != expected:
            raise RuntimeError(f"Incomplete capture set: {len(self.entries)} of {expected}")
        self.write_index()

    def write_index(self) -> None:
        rows = []
        for profile in matrix():
            for case in profile["cases"]:
                name = f"{profile_name(profile)}/{case}"
                rows.append(f"<section><h2>{html.escape(name)}</h2><div>"
                            f'<figure><figcaption>Before</figcaption><a href="before/{name}.png"><img src="before/{name}.png" alt="Before {html.escape(name)}"></a></figure>'
                            f'<figure><figcaption>After</figcaption><a href="after/{name}.png"><img src="after/{name}.png" alt="After {html.escape(name)}"></a></figure></div></section>')
        (self.output / "index.html").write_text(
            '<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
            '<title>Songlass native UI comparison</title><style>body{font:16px system-ui;margin:24px;background:#eee;color:#111}'
            'section{margin:32px 0}section div{display:flex;gap:16px}figure{margin:0;width:min(44vw,420px)}'
            'img{width:100%;height:auto}figcaption{font-weight:700;margin-bottom:8px}</style>'
            '<h1>Songlass · native before/after</h1><p>Original emulator screenshots. Capture complete; human visual review pending. '
            'Check hierarchy, space, material, contrast and large text. Static frames do not prove motion, touch targets or device performance.</p>'
            + "\n".join(rows) + "</html>", encoding="utf-8")


def profile_name(profile: dict) -> str:
    return f"{profile['style']}-{profile['appearance']}-{profile['variant']}"


def write_original_cover(path: Path, palette: int) -> None:
    size = 384
    colors = ((95, 59, 164), (28, 118, 128), (168, 75, 47))
    red, green, blue = colors[palette]
    raw = bytearray()
    for y in range(size):
        raw.append(0)
        for x in range(size):
            light = .55 + .45 * (1 - y / size)
            circle = (x - 230) ** 2 + (y - 135) ** 2 < 92 ** 2
            stripe = 50 < x < 330 and 250 < y < 290 and (x // 16) % 2 == 0
            raw.extend(min(255, round(value * light + (45 if circle else 0) + (60 if stripe else 0)))
                       for value in (red, green, blue))
    def chunk(kind: bytes, payload: bytes) -> bytes:
        return struct.pack(">I", len(payload)) + kind + payload + struct.pack(">I", zlib.crc32(kind + payload))
    path.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 2, 0, 0, 0))
                     + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def validate_png(data: bytes, viewport: tuple[int, int]) -> None:
    if len(data) < 10_000 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise RuntimeError("Missing, blank or invalid native PNG screenshot")
    if struct.unpack(">II", data[16:24]) != viewport:
        raise RuntimeError("Unexpected screenshot dimensions; before/after viewport must match")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--serial", default=os.environ.get("ANDROID_SERIAL", ""))
    parser.add_argument("--allow-software-cpu", action="store_true",
                        help="Explicitly permit TCG for static native evidence; CI retains KVM by default")
    parser.add_argument("--avd-name", help="Optional additional check of the selected serial's AVD name")
    parser.add_argument("--viewport", default="1080x2400", help="Matching WIDTHxHEIGHT pixels for both APKs")
    parser.add_argument("--density", type=int, default=420, help="Matching emulator density in dpi")
    parser.add_argument("--before-apk", type=Path)
    parser.add_argument("--after-apk", type=Path)
    parser.add_argument("--before-ref", default=BASELINE)
    parser.add_argument("--after-ref")
    parser.add_argument("--fixtures-only", action="store_true")
    args = parser.parse_args()
    if not re.fullmatch(r"\d{3,4}x\d{3,4}", args.viewport):
        parser.error("--viewport must be WIDTHxHEIGHT pixels")
    viewport = tuple(map(int, args.viewport.split("x")))
    if not all(320 <= value <= 4096 for value in viewport) or not 80 <= args.density <= 640:
        parser.error("Unsupported viewport or density")
    audit = Audit(args.output.resolve(), args.serial, allow_software_cpu=args.allow_software_cpu,
                  avd_name=args.avd_name, viewport=viewport, density=args.density)
    outcome, failure = "FAILED", None
    try:
        for command in ("ffmpeg", "ffprobe") if args.fixtures_only else ("adb", "ffmpeg", "ffprobe"):
            if not shutil.which(command):
                raise RuntimeError(f"Required tool unavailable: {command}")
        save_json(audit.output / "matrix.json", matrix())
        if args.fixtures_only:
            audit.generate_fixtures()
            outcome = "FIXTURES_VALIDATED_ONLY"
        else:
            if args.before_ref != BASELINE or not args.after_ref or not re.fullmatch(r"[0-9a-f]{40}", args.after_ref):
                raise RuntimeError("Exact baseline and candidate Git identities are required")
            if not args.before_apk or not args.after_apk or not args.before_apk.is_file() or not args.after_apk.is_file():
                raise RuntimeError("Both real debug APKs are required")
            save_json(audit.output / "apks.json", {
                "before": dict(ref=args.before_ref, sha256=digest(args.before_apk)),
                "after": dict(ref=args.after_ref, sha256=digest(args.after_apk)),
            })
            audit.compare(args.before_apk.resolve(), args.after_apk.resolve())
            outcome = "CAPTURE_COMPLETE_REVIEW_PENDING"
    except Exception as exc:
        failure = f"{type(exc).__name__}: {exc}"
        (audit.output / "failure.txt").write_text(traceback.format_exc())
        # Diagnostics are best effort; they never replace the nonzero result.
        if re.fullmatch(r"emulator-\d+", args.serial):
            for name, command in (("failure-logcat.txt", ("logcat", "-d", "-v", "threadtime")),
                                  ("failure-screen.png", ("exec-out", "screencap", "-p"))):
                try:
                    (audit.output / name).write_bytes(audit.adb(*command, timeout=10))
                except Exception:
                    pass
        print(failure, file=sys.stderr, flush=True)
    finally:
        expected = 2 * sum(len(profile["cases"]) for profile in matrix())
        result = dict(status=outcome, captured=len(audit.entries), expected=expected,
                      software_cpu_permitted=args.allow_software_cpu, viewport=viewport, density_dpi=args.density,
                      visual_review="pending", failure=failure)
        save_json(audit.output / "result.json", result)
        (audit.output / "summary.md").write_text(
            f"## Songlass native UI audit\n\nStatus: **{outcome}**. Captures: {len(audit.entries)}/{expected}.\n\n"
            f"Before: `{args.before_ref}`. After: `{args.after_ref or 'not supplied'}`.\n\n"
            "Normal: 3 styles × light/dark × home/library/player/appearance. Targeted: 150% text on library/appearance, "
            "reduced motion on player, reduced transparency on home for all 3 styles; normal light album detail for Liquid/Miuix.\n\n"
            "Open `index.html` in the downloaded artifact for matching original images. Native hierarchy XML, READY logs, "
            "APK/fixture hashes and the actual host renderer are included when captured.\n\n"
            "Human visual review is still required. No physical GPU/device performance, gesture or full accessibility certification is claimed.\n"
            + (f"\nFailure: {failure}\n" if failure else ""), encoding="utf-8")
        audit.commands.close()
    return 1 if failure else 0


if __name__ == "__main__":
    raise SystemExit(main())
