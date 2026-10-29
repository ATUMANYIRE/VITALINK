# VITALINK

Offline maternal assessment and auditable referral handoff for Health Hackathon 3.0 at VIT Bhopal University.

**Research prototype. Synthetic data only. Not a validated medical device.** No diagnosis, medication recommendation or clinical-accuracy claim. Severe systolic >=160 OR diastolic >=110 mmHg and concerning symptoms prompt immediate clinical assessment, independently of connectivity. The current release is rules-only; predictive AI is blocked pending suitable outcome data and review.

## Components

```text
apps/android/       Kotlin/Compose application and Android-independent domain
services/api/       Java 21 / Spring Boot 3.5.16 / PostgreSQL / Flyway
apps/web/           Next.js / strict TypeScript clinical workspace
contracts/          Shared API, safety version and regression cases
ml/                 Rules-only inference and gated research tooling
scripts/            Real API lifecycle and research verification
evidence/           Executed checks and measured results
docs/               Architecture, safety, verification and release audit
submission/         Judge briefing, demo script and ten-slide content
```

The repository initially contained only AGENTS.md and the pitch PDF. Implementation was created during this run; no earlier milestone pass is assumed. The original files remain unchanged. See [the final audit](docs/FINAL_RELEASE_AUDIT.md) for actual verification status, counts and blockers. Authored tests alone do not establish a pass.

## Local demonstration

Prerequisites: JDK21, Maven3.9.x, PostgreSQL18, Node24, Android SDK35/Gradle8.11.1 for mobile. Use separate terminals and keep credentials private. Run heavy builds sequentially on constrained machines.

1. Follow [services/api/README.md](services/api/README.md) to initialize the project-local database, package the API and start it on **127.0.0.1:8087**. Runtime credentials are generated in ignored `services/api/.runtime/demo-credentials.json`; there are no fixed production credentials.
2. Follow [apps/web/README.md](apps/web/README.md) to install dependencies, configure a random session key and start the real workspace at **http://localhost:3017** with API_BASE_URL=http://127.0.0.1:8087/api/v1.
3. Build/run [Android](apps/android/README.md). Emulator debug API URL is http://10.0.2.2:8087/api/v1. The [native UI rebuild report](docs/ANDROID_UI_REBUILD.md) contains the current APK path, Android test evidence and actual device screenshots.
4. Use a SYN-prefixed record and the runtime worker/doctor accounts. Follow [the three-minute demo](submission/DEMO_SCRIPT_3_MIN.md).

Ports8080,3000,3007 belong to unrelated applications in this workspace; do not stop them.

## Verification

From the root:

```powershell
python scripts/research_checks.py
python scripts/verify_lifecycle.py
```

The lifecycle script requires the real API/database and generated local credentials. It does not substitute for Android or browser testing. Component commands and exact evidence are in docs/TEST_EVIDENCE.md and each component README.

## Docker alternative

```powershell
python scripts/create_demo_env.py
docker compose config --quiet
docker compose up --build -d
```

The generator preserves any existing .env and writes random demo credentials to ignored files. Workspace URL: http://localhost:3017; API: http://127.0.0.1:8087. This environment initially denied Docker engine access, so a valid Compose configuration is not a claim of container startup. Consult the final audit before presenting it as tested.

## Clinical and release boundaries

Saved locally, sent, server-received and clinician-acknowledged are distinct states. Never wait for synchronization to escalate an urgent case. No-trigger does not establish health. [Clinical safety](docs/CLINICAL_SAFETY.md), [AI validation](docs/CLINICAL_AI_VALIDATION.md), [deployment](docs/DEPLOYMENT.md) and [known limitations](docs/KNOWN_LIMITATIONS.md) describe remaining review requirements. No cloud service was purchased, public deployment performed, code pushed or competition entry submitted.
