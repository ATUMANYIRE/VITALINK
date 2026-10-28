# Overnight execution plan

## Verified baseline — 28 October 2026

The complete initial repository consisted of AGENTS.md and VITALINK_Hackathon_Pitch.pdf. No Android application, README, milestone implementation, build scripts, tests, or Git history existed. `git status` initially reported no repository; Git has now been initialized. Existing files are preserved. Milestone 1 is unimplemented, not previously passed. No baseline tests can run without source/build scripts.

Windows toolchain: Microsoft JDK 21.0.10 at C:/Users/HP/.jdks/ms-21.0.10; default Java is 24.0.2. Node 24.12.0, npm 11.19.0, Python 3.11.9, PostgreSQL 18, Gradle 8.11.1 distribution and Android SDK platform 35 are installed. Cached AGP 8.7.3, Kotlin/Compose plugin 2.0.21, Room 2.6.1, Compose BOM 2024.10.01 are available. AVD DataWise_Pixel exists but no device is connected. Docker CLI 29.8.2 cannot access its engine (permission denied). npm registry and Maven network probes fail through the configured proxy; do not bypass this control. Use cached dependencies, bounded attempts, and report blockers honestly.

## Contracts before implementation

All workstreams follow contracts/API_CONTRACT.md. Referral UUID is stable across mobile, server and web; facility scope derives from authenticated users. Deterministic safety is authoritative. Patient identifiers are synthetic pseudonyms. No model is deployed without defensible validation.

## Ownership and dependencies

- Android agent: apps/android only; Kotlin domain, Compose, encrypted Room payloads, Keystore, transactional outbox, WorkManager, device/JVM tests. Repairs Milestone 1 foundations while implementing Milestone 2.
- Backend agent: services/api only; Java 21/Spring Boot 3.x, PostgreSQL/Flyway, opaque bearer sessions, facility authorization, idempotent referrals, tests and API documentation.
- Web agent: apps/web only; Next.js, strict TypeScript, accessible warm-white clinical interface, server-side token proxy, browser tests.
- Lead: contracts, root configuration, ml, documentation, Compose, submission artifacts, integration and release evidence.

Separate Git worktrees/branches isolate changes. Three independent agents may run concurrently; the lead performs AI/data assessment. Integrate commits sequentially. Agents must not change shared contracts without coordination.

## Integration order

1. Capture baseline and commit shared contracts.
2. Implement Android foundation/offline workflow, API and web in parallel.
3. Build and test each real component with available cached tooling.
4. Start a project-specific PostgreSQL cluster, migrate and run API; connect web and mobile.
5. Verify authentication, facility isolation, duplicate/conflicting submission, severe BP and symptom overrides, acknowledgment lifecycle and failures.
6. Attempt emulator/device tests and Docker Compose, recording environmental limits.
7. Re-run relevant regressions after fixes; record counts/commands and prepare submission artifacts.

## Validation strategy

No mock may stand in for real integration evidence. Unit tests may isolate dependencies. Record commands, exit codes, test counts and raw evidence. Device persistence, encryption and process-death tests require actual Android runtime. Clinical AI stays rules-only if outcome labels, patient identities, provenance or authorization are missing. Never substitute synthetic predictive accuracy for validation. Every milestone requirement receives PASS, PARTIAL, BLOCKED or FAIL in the release audit. Local commits only; no public deployment, push or competition submission.
