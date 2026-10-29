# Native Android presentation rebuild

Verified on 29 October 2026. This report covers the Android presentation change; historical milestone reports retain their original scope.

## Inspection and preservation

The working tree was clean at baseline `bd27be456c48d5b1d0c06928244f36acfebdf7a8`. Before replacing presentation code, a safety branch was created: `safety/android-presentation-before-rebuild-20261028`. AGENTS.md and the original pitch remain unchanged.

Actual modules: `apps/android/app` and Android-independent `apps/android/domain`. The original UI occupied one 864-line MainActivity. Existing ViewModel/StateFlow, encrypted Room repository, Keystore, WorkManager, API client and receipt validation were inspected before editing. Baseline unit verification passed with 64 tests.

`python scripts/verify_android_presentation.py` reports no Git changes to domain/rules, repositories, cryptography, workers, database schemas, backend, web, shared clinical contracts or research code. The ViewModel only gains loaded/save-navigation flags and session-draft reset; persistence navigation follows the existing committed save operation.

## Design and implemented screens

All three exact Dribbble references were downloaded, saved under `design-references/`, and visually inspected. See its README for attribution, original CDN URLs and the JPEG-to-PNG conversion. Reference photographs, illustrations, proprietary icons and consumer pregnancy features are not bundled.

The native Compose UI uses white canvases, forest #164C3F, pink #E9A4B7, ink #252B28 and fine borders #E5E7EB. The approved Protective Embrace geometry is a native vector. Licensed DM Sans serves functional text, including clinical values and warnings; Fraunces is confined to welcome/home editorial text. Material Symbols Rounded vectors and complete font/icon licenses work offline.

Implemented: first-run welcome; operational home; patient intake; BP observations; warning signs; rule review; saved assessments; searchable records; referral list/detail/timeline; offline handoff summary; account/sign-in; privacy/deletion; offline licenses. Existing users bypass welcome. Forms remain session drafts until saved. Navigation saves only routes and opaque IDs, never clinical form values or passwords.

Urgent findings appear immediately above the scrolling input workflow. Local saving, attempted transmission, server receipt and clinician acknowledgment remain distinct. Missing acknowledgment evidence is explicit. No diagnosis, treatment recommendation, model probability or invented dashboard count is introduced.

## Executed verification

| Check | Result | Evidence |
|---|---|---|
| Final debug and AndroidTest APK builds | PASS | `apps/android/evidence/gradle-20261029-173405.txt` |
| JVM tests, forced rerun | PASS: 70, zero failures/errors/skips | `evidence/android-ui/jvm-summary.json`, copied XML in `jvm/` |
| Native clinical, storage and presentation suites | PASS: 20 | `evidence/android-ui/device-tests-final.txt` |
| Separate process-boundary stages | PASS: offline save, restart, interrupted sync, recovery | `evidence/android-ui/offline-process-stages-initial.txt` |
| APK update plus emulator reboot retains referral/outbox | PASS | `evidence/android-ui/update-and-reboot-persistence.txt` |
| Native capture journeys | PASS: standard, smaller, larger with increased text | `evidence/android-ui/screenshots/*-final/` |
| Android lint | PASS: zero errors, three warnings | `evidence/android-ui/lint-summary.json` |
| Bundled fonts/licenses, exact logo, six text contrast pairs, preserved paths | PASS | `evidence/android-ui/resources-and-preservation.json` |
| Existing debug signing certificate | Matches | `evidence/android-ui/signing-identity.json` |

Native suites comprise two clinical UI tests, 16 encryption/persistence/migration tests and two referral presentation tests. Process stages and repeated screenshot journeys are additional executions, not extra unique tests included in the 70/20 totals.

Tests used a dedicated Android30 x86_64 AOSP emulator, WHPX, two cores and 1536 MiB guest RAM. Standard: 1080x2340 pixels at440 dpi (approximately393x851 dp). Smaller: 640x1280 at320 dpi (320x640 dp), font1.0. Larger: 960x1920 at320 dpi (480x960 dp), font1.5. Airplane mode was verified on for the journeys. Tests include normal BP with concerning symptoms, severe-range BP, immutable snapshots, encryption tampering, migration, duplicate outbox prevention and unconfirmed delivery states.

The final APK is `apps/android/app/build/outputs/apk/debug/app-debug.apk` (10,402,369 bytes). SHA256: `3129d428ee22e97975b5ed1992c10ff2f859abff323cd67866b9d0a95e392e6f`. It uses the existing user debug signing identity; no key is committed.

## Actual screenshots and visual review

Use `evidence/android-ui/screenshots/native-live/` for normal-launch welcome and home images, with captured accessibility hierarchies and display metadata. Standard workflow images are in `phone-final/`; BP entry and rule review at alternative sizes are in `small-final/` and `large-text-final/`. The welcome action is visible without scrolling on the target phone; Home now resets its scroll position after welcome. Medical fields stack when space or text scaling requires it. Inputs, warning text and handoff controls remain reachable in the tested configurations.

Raw instrumentation first-frame captures can show the previous loading frame even when semantics assertions pass. Those originals are retained; authoritative landing images were captured separately with ADB screencap after a normal app launch. No screenshot was mocked or image-edited. The normal-launch images and representative assessment, referral, account and supporting-screen images were visually inspected against the supplied design direction.

## Toolchain repairs and remaining limits

The inherited global android-35 folder contained API37 metadata. A genuine Android35r02 platform was verified and extracted into the ignored project cache, selected through ignored local.properties; the global SDK was not changed. This enabled lint to detect an API27-only navigation-bar attribute. It was moved into version-qualified resources, preserving minSdk26 without a lint suppression or baseline. Source/checksum evidence is in `sdk-platform.json` and `emulator-image.json`.

The three lint warnings are an existing test-runner update advisory, the inherited data-extraction-rules advisory with backups disabled, and one long official visibility-off vector path. Complete TalkBack/accessibility review and physical-device/API26/API35 runtime testing remain manual. Calculated text contrast does not establish full accessibility compliance.

The local API health probe returned connection refused at127.0.0.1:8087. Real sign-in, server delivery and clinician acknowledgment were not rerun in this presentation change. Start the existing services using their README before that integration rehearsal. This is a research prototype using synthetic records, with no clinical-validation or predictive-accuracy claim.

## Source map and reproduction

- `ui/VitalinkApp.kt`: routing, back behavior, shared shell and dialogs.
- `ui/theme/`: typography, colors and shapes; `ui/components/`: shared native controls.
- `ui/screens/`: welcome/home, assessment, records/referrals, account/privacy/licenses.
- `ui/Presentation.kt`: display labels and acknowledgment evidence; never changes referral state.
- `res/font`, `res/drawable`, `assets/licenses`: bundled resources and licenses.

From `apps/android`:

```powershell
.\scripts\build.ps1 -Offline -Tasks ':domain:test','--rerun',':app:testDebugUnitTest','--rerun',':app:assembleDebug',':app:assembleDebugAndroidTest',':app:lintDebug'
```

From the repository root, after installing both APKs on a dedicated test emulator:

```powershell
python scripts/verify_android_presentation.py
python scripts/capture_android_ui.py --serial emulator-5556 --prefix phone --mode journey
python scripts/capture_android_ui.py --serial emulator-5556 --prefix layout --mode layout
python scripts/capture_android_landing.py --serial emulator-5556 --prefix home --expect-text 'Start assessment'
```

Configure test display size, font scale and airplane mode explicitly before capture. The landing helper restarts the emulator app but never clears its records. First-run welcome requires a fresh synthetic test workspace. Heavy builds and emulator startup were serialized on this host. Machine-readable final results: `evidence/android-ui/verification-summary.json`.
