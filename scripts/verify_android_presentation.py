"""Inspect the actual APK/resources and prove preserved module contents against the UI checkpoint."""

import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ANDROID = ROOT / "apps/android"
# Exact commit behind the local safety branch; remains usable in another clone.
CHECKPOINT = "bd27be456c48d5b1d0c06928244f36acfebdf7a8"


def contrast(foreground, background):
    def luminance(color):
        values = [int(color[i:i + 2], 16) / 255 for i in (0, 2, 4)]
        linear = [x / 12.92 if x <= .04045 else ((x + .055) / 1.055) ** 2.4 for x in values]
        return sum(x * w for x, w in zip(linear, (.2126, .7152, .0722)))
    values = sorted([luminance(foreground), luminance(background)])
    return (values[1] + .05) / (values[0] + .05)


def main():
    artifact = ANDROID / "app/build/outputs/apk/debug/app-debug.apk"
    evidence = ROOT / "evidence/android-ui"
    evidence.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(artifact) as apk:
        names = apk.namelist()
        fonts = {}
        for name in ("dm_sans", "fraunces"):
            member = f"res/font/{name}.ttf"
            payload = apk.read(member)
            assert payload[:4] in (b"\x00\x01\x00\x00", b"OTTO"), member
            assert payload == (ANDROID / f"app/src/main/res/font/{name}.ttf").read_bytes()
            fonts[name] = {"apk_member": member, "bytes": len(payload), "sha256": hashlib.sha256(payload).hexdigest()}
        licenses = [f"assets/licenses/{name}" for name in ("dm-sans-OFL.txt", "fraunces-OFL.txt", "material-symbols-APACHE.txt")]
        for member in licenses:
            assert len(apk.read(member)) > 1000
        assert not any("design-references" in name for name in names)
    vector = ET.parse(ANDROID / "app/src/main/res/drawable/protective_embrace.xml").getroot()
    ns = "{http://schemas.android.com/apk/res/android}"
    assert vector.attrib[ns + "viewportWidth"] == "100"
    paths = vector.findall("path")
    assert paths[0].attrib[ns + "pathData"] == "M28,72C18,58 20,36 38,24C54,13 74,19 80,36C83,48 76,64 62,76"
    assert paths[1].attrib[ns + "pathData"] == "M42,60C36,52 38,42 46,37C55,32 63,34 66,44C67,51 61,58 54,63"
    assert paths[0].attrib[ns + "strokeColor"] == "#164C3F"
    assert paths[1].attrib[ns + "strokeColor"] == "#E9A4B7"
    assert paths[2].attrib[ns + "pathData"] == "M56.75,50A3.75,3.75 0,1 1,49.25 50A3.75,3.75 0,1 1,56.75 50"
    assert paths[2].attrib[ns + "fillColor"] == "#E9A4B7"
    assert all(path.attrib[ns + "strokeLineCap"] == "round" for path in paths[:2])
    pairs = [("functional text", "252B28", "FFFFFF"), ("supporting text", "626B66", "FFFFFF"),
             ("primary button", "FFFFFF", "164C3F"), ("urgent alert", "A12D24", "FFF1EF"),
             ("review alert", "815700", "FFF5E3"), ("pink-accent text", "8A4056", "FDF2F6")]
    contrasts = [{"purpose": label, "foreground": "#" + fg, "background": "#" + bg,
                  "ratio": round(contrast(fg, bg), 3)} for label, fg, bg in pairs]
    assert all(pair["ratio"] >= 4.5 for pair in contrasts), contrasts
    preserved = ["apps/android/domain", "apps/android/app/src/main/java/org/vitalink/app/data",
                 "apps/android/app/src/main/java/org/vitalink/app/SyncWorker.kt",
                 "apps/android/app/src/main/java/org/vitalink/app/VitalinkApplication.kt",
                 "apps/android/app/schemas", "services/api", "apps/web", "contracts/API_CONTRACT.md",
                 "contracts/clinical-cases.json", "ml"]
    changed = subprocess.run(["git", "diff", "--name-only", CHECKPOINT, "--", *preserved], cwd=ROOT,
                             capture_output=True, text=True, check=True).stdout.splitlines()
    assert not changed, "Preserved implementation unexpectedly changed: " + ", ".join(changed)
    checkpoint = subprocess.run(["git", "rev-parse", CHECKPOINT], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
    result = {"status": "PASS", "apk": str(artifact.relative_to(ROOT)), "apk_bytes": artifact.stat().st_size,
              "apk_sha256": hashlib.sha256(artifact.read_bytes()).hexdigest(), "fonts": fonts,
              "license_members": licenses, "approved_logo_geometry": "PASS", "text_contrast_pairs": contrasts,
              "preservation_checkpoint": checkpoint, "preserved_paths": preserved, "changed_preserved_files": changed,
              "limitations": ["APK resource inspection is not visual or device execution evidence.",
                              "Contrast calculations do not establish complete accessibility compliance."]}
    (evidence / "resources-and-preservation.json").write_text(json.dumps(result, indent=2), encoding="utf-8")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    main()
