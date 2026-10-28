# Android Build & Verification Record — 28 October 2026

## Final Verification Status: PASS (Host JVM Unit Tests, Lint, Debug APK & AndroidTest APK)

### Root Causes Resolved During Antigravity Recovery
1. **JDK 21 Auto-Selection (`apps/android/scripts/build.ps1`):** When the host `JAVA_HOME` pointed to JDK 24, `build.ps1` previously threw before checking `%USERPROFILE%\.jdks\ms-21.0.10`. Updated `build.ps1` to auto-select Microsoft OpenJDK `21.0.10` and prefer the locally installed Gradle `8.11.1` binary in `-Offline` mode.
2. **Android SDK Build Tools Version (`apps/android/app/build.gradle.kts`):** `C:\Users\HP\AppData\Local\Android\Sdk\build-tools\34.0.0` contained only an incomplete `.installer` folder, whereas `36.0.0` had the complete build-tools binaries (`aapt2.exe`, `d8.bat`, `apksigner.bat`, `zipalign.exe`). Updated `buildToolsVersion = "36.0.0"`.
3. **Offline Read-Only Dependency Cache Alignment (`apps/android/app/build.gradle.kts`):** Added `configurations.all.resolutionStrategy.force(...)` entries aligning transitive versions with artifacts present in `C:\Users\HP\.gradle\caches`:
   - `org.jetbrains.kotlin:kotlin-stdlib:2.0.21`
   - `org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.20`
   - `org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.20`
   - `com.google.devtools.ksp:symbol-processing-api:2.0.21-1.0.28`
   - `com.squareup:javapoet:1.13.0`
   - `androidx.test.espresso:espresso-idling-resource:3.6.1`
   - `com.google.code.findbugs:jsr305:3.0.2`
   - `androidx.test:core:1.6.1`

### Executed Tasks & Results
- **Command:** `.\apps\android\scripts\build.ps1 -Offline -Tasks ':domain:test',':app:testDebugUnitTest',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug','wrapper'`
- **Unit Tests (`64/64 PASS`, `0` failures, `0` skipped):**
  - `org.vitalink.domain.ClinicalRulesTest`: `35/35 PASS` (includes explicit `159/109`, `160/109`, `159/110`, `160/110` OR boundary quadrant check)
  - `org.vitalink.domain.ContractParityTest`: `1/1 PASS` (16/16 canonical `contracts/clinical-cases.json` parity cases)
  - `org.vitalink.domain.ReferralTest`: `24/24 PASS`
  - `org.vitalink.domain.RuleBenchmarkTest`: `1/1 PASS` (`apps/android/domain/build/reports/rule-benchmark.json`: median `1,000 ns` [`1.0 µs`], p95 `1,200 ns` [`1.2 µs`] across 5,000 samples after 10,000 warmup iterations on JDK `21.0.10`, `deviceBenchmark: false`)
  - `org.vitalink.app.DraftValidationTest`: `3/3 PASS`
- **Android Lint (`:app:lintDebug`):** `PASS` (`0` errors, `3` warnings; report at `apps/android/app/build/reports/lint-results-debug.html` and `lint-results-debug.xml`)
- **Generated APKs:**
  - Debug APK: `apps/android/app/build/outputs/apk/debug/app-debug.apk` (`9,801,357` bytes, SHA-256 `2fcea676272bc6b55515ef32d4d97c551571cf3d64e86bcc79123313cc6edd69`)
  - AndroidTest APK: `apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` (`1,173,285` bytes, SHA-256 `68e5da0120c58fc1e35e517f3a0964bd872cd47d593f96605db534cf558cafa6`)

### Device / Emulator Limitation
- No physical Android device is attached over ADB, and the installed `android-37.1` emulator image previously remained `adb offline` under host memory pressure while enforcing a 4 GB guest RAM reservation.
- Therefore, `app-debug-androidTest.apk` (`VitalinkInstrumentedTest` and `RealBackendLifecycleTest`) is compiled, packaged, and signed, but on-device instrumentation execution (`connectedDebugAndroidTest`) and on-device cold-start/SQLCipher timings are honestly reported as `BLOCKED` by physical/bootable ADB device availability.
