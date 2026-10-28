# VITALINK — Executed Test & Build Evidence

This document records all executed verification commands, exit codes, exact test counts, and artifact paths across every component of VITALINK.

---

## 1. Summary Table

| Layer | Suite / Check | Executed Command | Exit Code | Total | Passed | Failed | Skipped | Evidence Artifact |
|---|---|---|---|---:|---:|---:|---:|---|
| **Android (`:domain` + `:app`)** | Kotlin JVM Unit Tests & Host Rule Benchmark | `.\apps\android\scripts\build.ps1 -Offline -Tasks ':domain:test',':app:testDebugUnitTest'` | `0` | 64 | 64 | 0 | 0 | `apps/android/evidence/verification-summary.json` |
| **Android (`:app`)** | Debug APK & AndroidTest APK Build + Lint | `.\apps\android\scripts\build.ps1 -Offline -Tasks ':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug'` | `0` | 3 tasks | 3 | 0 | 0 | `apps/android/app/build/outputs/apk/debug/app-debug.apk`, `apps/android/app/build/reports/lint-results-debug.html` |
| **Backend (`services/api`)** | Maven Package + Unit + PostgreSQL + Testcontainers | `. .\services\api\scripts\environment.ps1; Push-Location services\api; .\scripts\maven.ps1 -o package` | `0` | 57 | 57 | 0 | 0 | `services/api/evidence/verification-summary.json`, `services/api/evidence/sanitized-TEST-*.xml` |
| **Web (`apps/web`)** | TypeScript Strict Check | `npm run typecheck` | `0` | 1 check | 1 | 0 | 0 | `apps/web/evidence/typecheck.txt` |
| **Web (`apps/web`)** | Node Unit & Contract Parity Tests | `npm test` | `0` | 31 | 31 | 0 | 0 | `apps/web/evidence/unit-tests.txt` |
| **Web (`apps/web`)** | Next.js 16.4.2 Production Build | `npm run build` | `0` | 5 routes | 5 | 0 | 0 | `apps/web/evidence/production-build.txt` |
| **Web + API + DB E2E** | Playwright Chromium E2E + axe-core WCAG 2.1 AA | `node node_modules/@playwright/test/cli.js test` | `0` | 6 | 6 | 0 | 0 | `apps/web/evidence/playwright-e2e.txt`, `apps/web/evidence/playwright-results.json` |
| **Python ML & Shared Rules** | Mathematical Evaluation, Abstention & 16-Case Parity | `python scripts/research_checks.py` | `0` | 27 | 27 | 0 | 0 | `evidence/ml/summary.json`, `evidence/ml/unit-tests.txt` |
| **Python Dataset Gate** | Provenance & Outcome Gate Refusal Check | `PYTHONPATH=ml python -m vitalink_research.pipeline --manifest ml/data/dataset_assessment.json` | `2` (expected) | 1 gate | 1 | 0 | 0 | `evidence/ml/dataset-gate.txt` |
| **Full-Stack HTTP Lifecycle** | Real HTTP API + PostgreSQL 18.1 Black-Box Harness | `python scripts/verify_lifecycle.py` | `0` | 36 | 36 | 0 | 0 | `evidence/integration/lifecycle-summary.json`, `evidence/integration/lifecycle-tests.txt` |
| **Docker Compose** | Environment Generation & Compose Interpolation | `python scripts/create_demo_env.py; docker compose config --quiet` | `0` | 1 check | 1 | 0 | 0 | `docker-compose.yml` |
| **TOTAL AUTOMATED TESTS** | **All Executed Test Suites** | — | — | **221** | **221** | **0** | **0** | — |

---

## 2. Clinical Blood-Pressure Boundary Verification (`OR` Semantics)

All four language implementations (`Kotlin`, `Java`, `TypeScript`, and `Python`) explicitly verify the severe blood-pressure boundary quadrant around `160 mmHg` systolic and `110 mmHg` diastolic using `OR` (never `AND`) semantics:

- **`159/109 mmHg`**: Below severe threshold on both axes (`ELEVATED_SYSTOLIC_BP`, `ELEVATED_DIASTOLIC_BP`) -> `REVIEW_REQUIRED` (never `URGENT` unless concerning symptoms are present).
- **`160/109 mmHg`**: Severe systolic (`>= 160 mmHg`) with diastolic below `110 mmHg` -> **`URGENT`** (`SEVERE_SYSTOLIC_BP` + `ELEVATED_DIASTOLIC_BP`).
- **`159/110 mmHg`**: Severe diastolic (`>= 110 mmHg`) with systolic below `160 mmHg` -> **`URGENT`** (`ELEVATED_SYSTOLIC_BP` + `SEVERE_DIASTOLIC_BP`).
- **`160/110 mmHg`**: Severe on both axes -> **`URGENT`** (`SEVERE_SYSTOLIC_BP` + `SEVERE_DIASTOLIC_BP`).

Verified in:
- Kotlin: `apps/android/domain/src/test/kotlin/org/vitalink/domain/ClinicalRulesTest.kt` (`exactSevereOrBoundaries159And160By109And110`)
- Java: `services/api/src/test/java/org/vitalink/api/ClinicalRulesTest.java` (`boundariesAreExact`)
- Python: `ml/tests/test_research.py` (`test_boundaries`)
- TypeScript: `apps/web/tests/contracts.test.ts` (`clinical BP boundary quadrant (159/109, 160/109, 159/110, 160/110) enforces OR semantics`)

---

## 3. Browser E2E Screenshots & WCAG 2.1 AA Accessibility Evidence

Captured from the live Next.js 16.4.2 production server (`http://localhost:3017`) backed by the live Spring Boot 3.5.16 API (`http://127.0.0.1:8087`) and PostgreSQL 18.1 (`127.0.0.1:55432`):

- `apps/web/evidence/sign-in-desktop.png` + `apps/web/evidence/accessibility-sign-in.json` (`0` axe-core violations)
- `apps/web/evidence/overview-desktop.png` + `apps/web/evidence/accessibility-overview.json` (`0` axe-core violations)
- `apps/web/evidence/referral-desktop.png` + `apps/web/evidence/accessibility-referral.json` (`0` axe-core violations)
- `apps/web/evidence/referral-mobile.png` (`390x844` viewport, zero horizontal overflow) + `apps/web/evidence/accessibility-mobile-referral.json` (`0` axe-core violations)

---

## 4. Measured Software Benchmarks (Non-Device)

- **Android Domain Host-JVM Rules Benchmark (`apps/android/domain/build/reports/rule-benchmark.json`):**
  - Environment: Microsoft OpenJDK `21.0.10`, Windows 11 `amd64`, 12 logical processors, `deviceBenchmark: false`.
  - Protocol: `10,000` warmup iterations, `5,000` measured samples.
  - Result: **`median = 1,000 ns` (`1.0 µs`), `p95 = 1,200 ns` (`1.2 µs`)**.
- **Python Reference Rules Benchmark (`evidence/ml/software-benchmark.json`):**
  - Environment: Python `3.11.9`, Windows 11 `amd64`, `device_claim: false`.
  - Protocol: `200` warmup iterations, `2,000` measured samples.
  - Result: **`p50 = 0.0049 ms` (`4.9 µs`), `p95 = 0.0106 ms` (`10.6 µs`), `max = 0.0972 ms`**.
