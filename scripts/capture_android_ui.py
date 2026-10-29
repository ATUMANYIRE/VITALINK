"""Run the opt-in native screenshot journey and export unmodified device PNGs.

Install both debug APKs first. Configure screen size/font scale and connectivity
on the selected test device explicitly; this helper does not erase app records.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--mode", choices=("journey", "layout"), default="journey")
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) /
                                            "Android/Sdk/platform-tools/adb.exe"))
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,40}", args.prefix):
        parser.error("prefix must be 1–40 ASCII letters, digits, hyphens or underscores")
    output = ROOT / "evidence/android-ui/screenshots" / args.prefix
    output.mkdir(parents=True, exist_ok=True)

    def adb(*command, timeout=30):
        result = subprocess.run([args.adb, "-s", args.serial, *command],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr.decode(errors="replace"))
        return result.stdout

    environment = {name: adb("shell", *command).decode().strip() for name, command in {
        "device": ("getprop", "ro.product.model"), "api": ("getprop", "ro.build.version.sdk"),
        "size": ("wm", "size"), "density": ("wm", "density"),
        "fontScale": ("settings", "get", "system", "font_scale"),
        "airplaneMode": ("settings", "get", "global", "airplane_mode_on"),
    }.items()}
    for label, relative in {
        "hostApkSha256": "apps/android/app/build/outputs/apk/debug/app-debug.apk",
        "hostAndroidTestApkSha256": "apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk",
    }.items():
        artifact = ROOT / relative
        if artifact.is_file():
            environment[label] = hashlib.sha256(artifact.read_bytes()).hexdigest()
    started = time.monotonic()
    command = ["shell", "am", "instrument", "-w", "-r", "-e", "class",
               "org.vitalink.app.NativeScreenshotJourneyTest", "-e", "uiCaptureMode", args.mode,
               "-e", "screenPrefix", args.prefix,
               "org.vitalink.app.debug.test/androidx.test.runner.AndroidJUnitRunner"]
    log = adb(*command, timeout=420).decode(errors="replace").replace("\r", "").rstrip() + "\n"
    (output / "instrumentation.txt").write_text(log, encoding="utf-8")
    (output / "environment.json").write_text(json.dumps({**environment, "serial": args.serial,
        "elapsedSeconds": round(time.monotonic() - started, 2), "command": command}, indent=2))
    print(log)
    if "OK (1 test)" not in log or "FAILURES!!!" in log:
        raise RuntimeError("Native journey did not pass; inspect instrumentation.txt")
    base = "files/ui-screenshots/"
    manifest = json.loads(adb("exec-out", "run-as", "org.vitalink.app.debug", "cat",
                              base + args.prefix + "-capture-manifest.json"))
    (output / "capture-manifest.json").write_text(json.dumps(manifest, indent=2))
    for capture in manifest["captures"]:
        name = capture["file"]
        if not re.fullmatch(re.escape(args.prefix) + r"-[A-Za-z0-9_-]+\.png", name):
            raise RuntimeError("Unexpected screenshot filename")
        image = adb("exec-out", "run-as", "org.vitalink.app.debug", "cat", base + name)
        if not image.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Screenshot is not a PNG")
        (output / name).write_bytes(image)
    print(f"Exported {len(manifest['captures'])} actual native screenshots to {output}")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        print(error, file=sys.stderr)
        sys.exit(1)
