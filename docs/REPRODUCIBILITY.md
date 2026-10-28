# Reproducibility & Verification Guide

## 1. Verified Host Toolchain

- **Host OS & CPU:** Windows 11 (`amd64`), 12 logical processors, 16,016 MiB RAM.
- **Java:** Microsoft OpenJDK `21.0.10` (`C:\Users\HP\.jdks\ms-21.0.10`); default system `PATH` Java is `24.0.2`, so all build scripts explicitly select JDK 21.
- **Android (`apps/android`):** Android SDK 35 (`C:\Users\HP\AppData\Local\Android\Sdk`), Build Tools `36.0.0`, Gradle `8.11.1`, Android Gradle Plugin `8.7.3`, Kotlin & Compose Compiler `2.0.21`, KSP `2.0.21-1.0.28`, Room `2.6.1`, Compose BOM `2024.10.01`.
- **Backend (`services/api`):** Spring Boot `3.5.16`, Spring Security `6.5.0`, Flyway Core & PostgreSQL `11.7.2`, PostgreSQL `18.1` (`127.0.0.1:55432`) + Docker Testcontainers `postgres:17-alpine`, Apache Maven `3.9.11`.
- **Web (`apps/web`):** Node.js `24.12.0`, Next.js `16.4.2`, React `19.3.0`, TypeScript `5.9.3`, Playwright `1.64.1`, `@axe-core/playwright` `4.11.2`.
- **Python (`ml` & `scripts`):** Python `3.11.9` (standard library for `ml` and `scripts`; workspace `.tools/python` includes `PyMuPDF 1.28.2` for slide rendering).
- **Docker:** Docker Engine `29.8.2` & Docker Compose `v5.1.3`.

---

## 2. Reproducible Verification Commands (From Repository Root)

Run heavy builds sequentially (Backend -> Web -> Android) to avoid exhausting host memory:

### Step 1: Python ML, Shared Clinical Rules & Dataset Gate
```powershell
python scripts/research_checks.py
```
- Runs `27` unit tests (`evidence/ml/unit-tests.txt`), verifies `16/16` shared clinical rule cases (`contracts/clinical-cases.json`), confirms the dataset admission gate exits with `2` (`BLOCKED`, `evidence/ml/dataset-gate.txt`), and records the Python software benchmark (`evidence/ml/software-benchmark.json`).

### Step 2: Backend Package, Unit, PostgreSQL & Testcontainers Integration Tests
```powershell
& .\services\api\scripts\start-database.ps1
. .\services\api\scripts\environment.ps1
Push-Location services\api
.\scripts\maven.ps1 -o package
Pop-Location
```
- Runs `57/57` JUnit 5 tests (`AuthServiceTest`: 5, `ClinicalRulesTest`: 26, `PostgresContainerTest`: 1 [Docker `postgres:17-alpine`], `ReferralIntegrationTest`: 25 [local PostgreSQL 18.1]) and packages `services/api/target/vitalink-api-0.1.0.jar`.

### Step 3: Start Backend & Run Black-Box HTTP Lifecycle Suite
```powershell
& .\services\api\scripts\start-api.ps1
python scripts/verify_lifecycle.py
```
- Runs `36/36` black-box HTTP + PostgreSQL lifecycle tests (`evidence/integration/lifecycle-summary.json`).

### Step 4: Web TypeScript Check, Unit Tests, Production Build & Playwright E2E
```powershell
Push-Location apps\web
npm run typecheck
npm test
npm run build
Pop-Location
```
- Runs `typecheck` (`0` errors), `31/31` unit & contract parity tests, and builds the Next.js 16.4.2 standalone production bundle.
- To run Playwright E2E + axe-core WCAG 2.1 AA against the live API (`127.0.0.1:8087`) and Next.js production server (`http://localhost:3017`), start Next.js on port `3017` with `VITALINK_API_ORIGIN=http://127.0.0.1:8087` and run `node node_modules/@playwright/test/cli.js test` (`6/6` E2E tests pass, generating screenshots and `accessibility-*.json` in `apps/web/evidence/`).

### Step 5: Android Unit Tests, Lint, Debug APK & AndroidTest APK
```powershell
& .\apps\android\scripts\build.ps1 -Offline -Tasks ':domain:test',':app:testDebugUnitTest',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug'
```
- Or from the repository root: `.\gradlew.bat :domain:test :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`.
- Executes `64/64` Kotlin unit tests, generates host-JVM rule latency benchmark (`apps/android/domain/build/reports/rule-benchmark.json`), runs Android Lint (`0` errors), and builds `apps/android/app/build/outputs/apk/debug/app-debug.apk` and `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`.

### Step 6: Docker Compose Configuration Check
```powershell
python scripts/create_demo_env.py
docker compose config --quiet
```
