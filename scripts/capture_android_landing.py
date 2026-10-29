"""Capture a normal app launch outside the Compose instrumentation clock.

Dedicated emulators only: restarts the project app, but never clears its data.
The caller must prepare a fresh test workspace to capture first-run welcome.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--prefix", required=True)
    parser.add_argument("--expect-text", required=True)
    parser.add_argument("--adb", default=str(Path(os.environ.get("LOCALAPPDATA", "")) /
                                            "Android/Sdk/platform-tools/adb.exe"))
    args = parser.parse_args()
    if not re.fullmatch(r"emulator-[0-9]+", args.serial):
        parser.error("use a dedicated emulator; this command restarts the app")
    if not re.fullmatch(r"[A-Za-z0-9_-]{1,40}", args.prefix):
        parser.error("invalid output prefix")

    def adb(*command, timeout=30):
        result = subprocess.run([args.adb, "-s", args.serial, *command],
                                capture_output=True, timeout=timeout, check=True)
        return result.stdout

    output = ROOT / "evidence/android-ui/screenshots/native-live"
    output.mkdir(parents=True, exist_ok=True)
    adb("shell", "am", "force-stop", "org.vitalink.app.debug")
    adb("shell", "am", "start", "-W", "-n", "org.vitalink.app.debug/org.vitalink.app.MainActivity")
    remote = "/data/local/tmp/vitalink-ui-window.xml"
    for attempt in range(6):
        adb("shell", "uiautomator", "dump", remote)
        hierarchy = adb("exec-out", "cat", remote)
        tree = ET.fromstring(hierarchy)
        if any(args.expect_text in node.get("text", "") or
               args.expect_text in node.get("content-desc", "") for node in tree.iter()):
            break
        time.sleep(1)
    else:
        raise RuntimeError("Expected native landing screen was not observed")
    time.sleep(.3)
    payload = adb("exec-out", "screencap", "-p")
    if not payload.startswith(b"\x89PNG\r\n\x1a\n"):
        raise RuntimeError("Native capture is not a PNG")
    width, height = struct.unpack(">II", payload[16:24])
    (output / f"{args.prefix}.png").write_bytes(payload)
    (output / f"{args.prefix}-hierarchy.xml").write_bytes(hierarchy)
    metadata = {"source": "ADB exec-out screencap -p after normal app launch; unmodified pixels",
        "serial": args.serial, "expectedTextObserved": args.expect_text,
        "widthPixels": width, "heightPixels": height,
        "pngSha256": hashlib.sha256(payload).hexdigest(),
        "density": adb("shell", "wm", "density").decode().strip(),
        "fontScale": adb("shell", "settings", "get", "system", "font_scale").decode().strip(),
        "airplaneMode": adb("shell", "settings", "get", "global", "airplane_mode_on").decode().strip()}
    (output / f"{args.prefix}.json").write_text(json.dumps(metadata, indent=2))
    print(json.dumps(metadata, indent=2))


if __name__ == "__main__":
    main()
