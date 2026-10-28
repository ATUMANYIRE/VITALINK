# Repository Guidelines

## Project Status & Scope

VITALINK is an offline maternal screening and referral research prototype for Health Hackathon 3.0. Currently, only `VITALINK_Hackathon_Pitch.pdf` exists; there is no Android scaffold or Git history. Milestone 1 covers Android foundations, deterministic clinical rules, and automated tests. Backend services, doctor dashboards, and machine-learning models are outside scope.

## Project Structure & Module Organization

Use this target structure when creating the Android scaffold:

- `app/src/main/`: Kotlin/Jetpack Compose UI, ViewModels, and Android integration.
- `app/src/main/res/`: Android resources and assets.
- `domain/src/main/kotlin/`: Android-independent models, validation, data contracts, and clinical rules.
- `domain/src/test/kotlin/`: rule-engine and validation unit tests.
- `app/src/test/` and `app/src/androidTest/`: local and device tests.
- `docs/`: architecture decisions, versioned clinical-rule specifications, and review notes.

Use unidirectional state management with ViewModel and StateFlow. Keep Android APIs out of the domain module.

## Build, Test, and Development Commands

After scaffolding, use these Windows commands from the repository root:

- `.\gradlew.bat :app:assembleDebug`: build a debug APK.
- `.\gradlew.bat test`: run local unit tests.
- `.\gradlew.bat :app:lint`: run Android Lint.
- `.\gradlew.bat :app:connectedDebugAndroidTest`: run device tests on an emulator or connected device.

Verify JDK 21 and compatible installed Android, Kotlin, and Compose tooling before selecting versions. Report builds as successful only after executing them.

## Coding Style & Naming Conventions

Use Kotlin and Gradle Kotlin DSL with four-space indentation. Use `PascalCase` for types and composables, `camelCase` for functions and properties, and `UPPER_SNAKE_CASE` for constants. Prefer immutable state and explicit units in vital-sign contracts. No formatter is configured yet.

## Testing & Clinical Safety

Use JUnit for domain tests and Compose UI tests for screen behavior. Name tests after observable behavior, such as `severeSystolicTriggersUrgentAssessment`. Cover thresholds, invalid inputs, concerning symptoms with normal BP, and offline operation; no numerical coverage target is established.

Systolic BP >=160 OR diastolic BP >=110 mmHg must trigger urgent clinical assessment regardless of future AI scores. Normal BP must never suppress concerning symptoms. Isolate, version, and document rules for clinician review. Use synthetic patient data, local escalation messaging, and no diagnosis, accuracy claims, or autonomous treatment recommendations.

## Commit & Pull Request Guidelines

There is no existing commit convention. Use short, imperative subjects, optionally prefixed with `feat:`, `fix:`, or `test:`. Pull requests should explain behavior changes, link relevant issues, list executed checks, and include screenshots for UI changes. Identify clinical-rule changes and their review status explicitly. Never commit patient data, credentials, or signing keys.
