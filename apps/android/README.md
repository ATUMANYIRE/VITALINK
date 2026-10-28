# VITALINK Android research prototype

Native Kotlin/Compose application with a pure Kotlin clinical domain module. Synthetic patient identifiers only (`SYN-...`). Clinical rules are research specifications awaiting clinician review; this is not a validated medical device.

## Build and test

Use JDK 21 and Android SDK 35. The verified cached versions selected for this environment are AGP 8.7.3, Gradle 8.11.1, Kotlin/Compose plugin 2.0.21, Room 2.6.1 and Compose BOM 2024.10.01. Dependency versions are pinned in Gradle Kotlin DSL.

From `apps/android`:

```powershell
.\scripts\build.ps1 -Offline
.\scripts\build.ps1 -Offline -Tasks ':domain:test',':app:assembleDebugAndroidTest',':app:lintDebug'
.\gradlew.bat :app:connectedDebugAndroidTest
```

The helper selects an installed JDK21 when JAVA_HOME is unset, isolates writable Gradle cache under `.cache`, and uses the existing read-only dependency cache in offline mode. Online environments can omit `-Offline`. The Gradle wrapper uses Gradle 8.11.1 once generated. APK target: `app/build/outputs/apk/debug/app-debug.apk`. Evidence: `evidence/` and Gradle XML reports under module `build/test-results`.

## Layout

- `domain/`: rules, validation, transitions, receipt verification, retry policy, handoff and rules-only inference interface. No Android dependencies.
- `app/`: Compose UI, ViewModel/StateFlow, encrypted Room persistence, Keystore, API client and WorkManager.
- `app/src/androidTest/`: real Keystore/Room/migration/UI tests and staged real-service referral test.

## Offline and clinical behavior

Assess and save without authentication/network. Severe systolic >=160 OR diastolic >=110, and any configured concerning symptom, produce immediate escalation independently of other invalid/missing fields. Invalid data cannot be committed; missing measurements remain missing and require review. Normal-range BP never cancels concerning symptoms. The system gives no diagnosis or treatment recommendation. No predictive model is deployed.

Saved referral snapshots are immutable. Room commits assessment, referral and outbox together. State: DRAFT → PENDING_SYNC → SENT → SERVER_RECEIVED → ACKNOWLEDGED → CLOSED. DRAFT describes the unsaved form. SENT means in flight, not delivered. Only a validated matching server response confirms receipt; acknowledgment requires clinician actor/time. Timeout retries preserve UUID and immutable body. Two-minute attempt leases recover process death; attempt fencing prevents stale failures overwriting newer attempts. Equal-jitter exponential retry delays range from15 seconds toone hour; connectivity constraints and periodic work resume delivery. Invalid/auth/conflict responses require visible user action.

## Storage and privacy

Supported encrypted-storage alternative: Room stores AEAD AES-256-GCM blobs for every clinical assessment, referral snapshot, server receipt and token. Keys are nonexportable Android Keystore keys. Fresh nonce per operation and record-specific associated data prevent ciphertext reuse/substitution. Only opaque IDs, delivery states/times and safe error codes are plaintext. Database pages, counts and access patterns are not concealed; this is explicitly not whole-file SQLCipher encryption. Device backup is disabled. Key loss fails closed without destructive database reset. No patient fields or auth tokens are logged.

Closed referrals and unlinked assessments expire locally after30 days. Unsent referrals are never automatically purged. Local deletion is explicit, warns about undelivered records, cascades outbox deletion, and does not delete server records. Secure disposal of device flash and server retention require a deployment-specific policy.

## Local API networking

Debug uses `http://10.0.2.2:8087/api/v1` from an Android emulator. Physical device: use `adb reverse tcp:8087 tcp:8087` and build with `-PapiBaseUrl=http://127.0.0.1:8087/api/v1`, or supply a trusted LAN endpoint through the same Gradle property. For example, `.\scripts\build.ps1 -Offline -Tasks ':app:assembleDebug','-PapiBaseUrl=http://127.0.0.1:8087/api/v1'`. Release disables cleartext and must be configured with a real HTTPS endpoint before deployment; the supplied localhost release URL is not a deployed service. Authentication credentials are runtime fixtures provided by the API service. Offline unsigned referrals use the explicitly shown `demo-facility` scope; sign in to the intended facility before creation. Port 8087 avoids unrelated local services; do not stop another application's processes to reclaim a default port.

## Staged device lifecycle test

`ReferralLifecycleDeviceTest#executeLifecycleStage` takes runner argument `lifecycleStage`. Host sequence: airplane mode on → `offline-save` → force-stop → `restart-verify` → `interrupted-sync` → force-stop → `recovery-verify` → airplane mode off → `submit` with runtime workerUsername/workerPassword → real dashboard doctor acknowledgment → `poll-ack`. Stages log only opaque referral ID and stage. Run this separately from normal instrumentation; it skips unless arguments are supplied. Do not count authored tests as executed evidence.
