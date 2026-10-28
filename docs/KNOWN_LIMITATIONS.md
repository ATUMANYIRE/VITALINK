# Known Limitations & Honest Disclosure

This document records all clinical, scientific, architectural, and environmental limitations of the VITALINK Health Hackathon 3.0 prototype.

---

## 1. Clinical & Regulatory Limitations
- **Research Prototype Only:** VITALINK is an engineering and research prototype developed for Health Hackathon 3.0 at VIT Bhopal University. It is **not** an approved medical device, diagnostic instrument, or autonomous treatment selector.
- **No Diagnostic or Negative-Assurance Claims:** Deterministic rule triggers (`v1.0.0-ng133-research`) are based on NICE NG133 blood-pressure thresholds (`systolic >= 160 mmHg OR diastolic >= 110 mmHg`) and maternal red-flag symptoms. A `NO_SAFETY_TRIGGER` result means only that no configured rule fired on the entered values; it never rules out preeclampsia, eclampsia, or maternal/fetal compromise.
- **Local Escalation Precedence:** Urgent local action must never wait for network synchronization, server receipt, or clinician dashboard acknowledgment.
- **Synthetic Data Only:** All identifiers must begin with `SYN-`. Never enter real patient names, phone numbers, government IDs, or protected health information.

---

## 2. Predictive AI & Dataset Limitations (Milestone 4)
- **No Validated Clinical Cohort Available:** Publicly inspected maternal risk tables (such as UCI Maternal Health Risk, dataset ID 863) provide only coarse `RiskLevel` categories (`low` / `mid` / `high`) without verified clinical outcome definitions, gestational timing at measurement, measurement-to-outcome intervals, or patient-level longitudinal identifiers.
- **Pipeline Refusal by Design:** The admission gate (`ml/vitalink_research/pipeline.py --manifest ml/data/dataset_assessment.json`) intentionally refuses training with exit code `2` (`BLOCKED`) when patient-level split provenance and clinical outcome definitions are absent.
- **Rules-Only Runtime:** Both the Android client (`RulesOnlyModelProvider`) and Python engine (`RulesOnlyInference`) return `MODEL_UNAVAILABLE` and abstain safely rather than fabricatingsynthetic risk probabilities or unvalidated ONNX models.

---

## 3. Technical & Environmental Limitations
- **Physical Android Device / Bootable Emulator Execution (`BLOCKED`):**
  - Host JVM unit tests (`64/64 PASS`), Android Lint (`0` errors), Debug APK (`apps/android/app/build/outputs/apk/debug/app-debug.apk`), and AndroidTest APK (`apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`) are all built and verified.
  - However, no physical Android device is connected via ADB, and the installed `android-37.1` AVD image remained `adb offline` while reserving 4 GB RAM on this 16 GB host. Therefore, on-device instrumentation tests (`connectedDebugAndroidTest`) and on-device cold-start/SQLCipher hardware timings have not been executed on physical Android hardware.
- **Android Local Database Encryption Caveat:**
  - Sensitive assessment and referral payloads are encrypted at the application layer with `AES/GCM/NoPadding` (`256-bit` key backed by `AndroidKeyStore`, `96-bit` random IV, `128-bit` authentication tag, bound to `recordId|schemaVersion` AAD) before storage in Room SQLite, while non-PII operational indexing columns remain queryable for outbox recovery. Full-database SQLCipher encryption is a future hardening item.
- **Offline Dependency Vulnerability Audit:**
  - Builds use read-only local dependency caches (`C:\Users\HP\.m2\repository`, `C:\Users\HP\.gradle\caches`, local `node_modules`). Online CVE/advisory scanning (`npm audit`, OWASP Dependency-Check) was not run in offline mode; cached Spring Security `6.5.0` pins should be reviewed before any non-hackathon deployment.
