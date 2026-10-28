# Screenshot and Recording Checklist

Capture actual running software only. Use `SYN-*` synthetic identifiers and exclude credentials, tokens, host usernames, and unrelated windows.

## Verified Live Browser Captures (`apps/web/evidence/`)

- [x] **Doctor Workspace Sign-In (`1440x960` desktop):** [`../apps/web/evidence/sign-in-desktop.png`](../apps/web/evidence/sign-in-desktop.png) (`0` axe-core WCAG 2.1 AA violations in `accessibility-sign-in.json`)
- [x] **Doctor Workspace Overview with Live Queue & Operational Counts (`1440x960` desktop):** [`../apps/web/evidence/overview-desktop.png`](../apps/web/evidence/overview-desktop.png) (`0` axe-core WCAG 2.1 AA violations in `accessibility-overview.json`)
- [x] **Referral Detail View with Timestamp, Severe BP (`168/112 mmHg`), Rule Triggers & Explicit Clinician Acknowledgment (`1440x960` desktop):** [`../apps/web/evidence/referral-desktop.png`](../apps/web/evidence/referral-desktop.png) (`0` axe-core WCAG 2.1 AA violations in `accessibility-referral.json`)
- [x] **Responsive Mobile Doctor Workspace (`390x844` viewport, zero horizontal overflow):** [`../apps/web/evidence/referral-mobile.png`](../apps/web/evidence/referral-mobile.png) (`0` axe-core WCAG 2.1 AA violations in `accessibility-mobile-referral.json`)

## Physical Android Handset Captures (Pending Physical ADB Device Connection)

The Android Debug APK (`apps/android/app/build/outputs/apk/debug/app-debug.apk`) and AndroidTest APK (`apps/android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`) are built and signed. Capture the following on a physical Android handset before external submission:

- [ ] Android assessment screen showing research/rules-only label (`v1.0.0-ng133-research`).
- [ ] Urgent severe-BP alert (`168/112 mmHg`) and immediate local escalation instructions.
- [ ] Normal BP (`124/82 mmHg`) with concerning symptom (`SEVERE_HEADACHE` / `VISUAL_DISTURBANCE`) still triggering `URGENT_REFERRAL`.
- [ ] Offline `PENDING_SYNC` referral and handoff summary before network sync.
- [ ] Same committed referral record after application restart.
- [ ] Updated Android `SERVER_RECEIVED` and `ACKNOWLEDGED` states after synchronization.
