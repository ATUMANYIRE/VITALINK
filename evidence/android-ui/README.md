# Native Android UI evidence

Final rebuild: 29 October 2026. [Implementation and verification report](../../docs/ANDROID_UI_REBUILD.md).

- 70 JVM tests: [summary](jvm-summary.json), original copied XML in jvm/.
- 20 native tests: [complete output](device-tests-final.txt).
- Four process-boundary stages: [output](offline-process-stages-initial.txt).
- Final APK update and emulator reboot retain referral/outbox: [output](update-and-reboot-persistence.txt).
- [Resource/preservation checks](resources-and-preservation.json), [lint](lint-summary.json), [complete summary](verification-summary.json).

## Selected actual native screenshots

| State | Image |
|---|---|
| First-run welcome | [Normal launch](screenshots/native-live/welcome.png) |
| Home with an actual pending synthetic referral | [Normal launch](screenshots/native-live/standard-home.png) |
| Smaller home, 320x640dp | [Normal launch](screenshots/native-live/small-home.png) |
| Larger home, 480x960dp, text1.5 | [Normal launch](screenshots/native-live/large-text-home.png) |
| Severe BP with keyboard open | [Phone](screenshots/phone-final/phone-final-04-observations-urgent.png) |
| Saved referral, delivery unconfirmed | [Details](screenshots/phone-final/phone-final-07-referral-details.png) |
| Offline handoff | [Summary](screenshots/phone-final/phone-final-13-offline-handoff-summary.png) |
| Sign-in | [Screen](screenshots/phone-final/phone-final-15-sign-in.png) |
| Bundled licenses | [Screen](screenshots/phone-final/phone-final-16-licenses.png) |

The phone-final, small-final and large-text-final folders contain the three passing capture journeys and actual configuration manifests. Native-live contains normal app launches, accessibility hierarchies and unmodified ADB PNGs. First-frame instrumentation images can retain a previous loading frame; use native-live for landing-screen review. Raw originals remain intact. Screenshots are actual app pixels, not generated mockups or image edits.

The test emulator used API30, x86_64, WHPX, two cores and1536MiB RAM. All records are synthetic. Live backend sign-in/delivery/acknowledgment were not rerun because the local API was unavailable. Physical-device, other-API runtime and complete accessibility testing remain manual. No clinical-deployment readiness is asserted.

Text transcripts use normalized transport line endings and trailing whitespace. Test messages, results and order are unchanged. Upstream OFL license text is kept verbatim, including its original whitespace.
