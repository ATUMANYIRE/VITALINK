## Inspiration

Maternal care depends on recognizing warning signs and completing a timely handoff. We wanted to help health workers record concerns when connectivity is unreliable and follow what happens to a referral afterward.

That led to VITALINK: **Maternal care, connected.**

## What it does

VITALINK supports an offline maternal assessment and referral workflow. A health worker records a synthetic patient identifier, blood pressure, gestational age and reported warning signs in a native Android app.

Deterministic rules run on the device. Configured severe-range blood pressure—systolic ≥160 **or** diastolic ≥110 mmHg—prompts urgent clinical assessment. Concerning symptoms remain urgent with normal BP, and missing measurements remain explicitly missing. Escalation instructions are available without waiting for a network connection.

The app saves encrypted assessment snapshots and queues referrals locally. Synchronization uses a stable referral identifier and idempotent requests. The doctor workspace provides facility-scoped referral review, assessment details, status history and explicit clinician acknowledgment.

The interface distinguishes **saved locally**, **transmission attempted**, **server received** and **clinician acknowledged**, helping users understand the current handoff state.

## How we built it

The Android client uses Kotlin, Jetpack Compose, ViewModel/StateFlow, Room, Android Keystore-backed payload encryption and WorkManager. The rule engine is independent of Android APIs and carries an explicit version for later clinical review.

A Java 21/Spring Boot API uses Spring Security, PostgreSQL, Flyway migrations and transactional referral processing. The doctor workspace uses Next.js, React and strict TypeScript. Python research tooling supports dataset assessment and evaluation preparation.

The mobile design combines a white canvas, forest green, restrained pink accents, the Protective Embrace mark, bundled typography and readable clinical values.

## Challenges we faced

We had to handle process interruption, repeated submissions and uncertain network outcomes while keeping referral states accurate. We also worked to keep urgent instructions visible during data entry and preserve usability with smaller screens and increased text sizes.

Another challenge was scientific integrity: the available dataset assessment did not justify predictive deployment for the intended outcome. The current release therefore uses rules-only decision support, with predictive AI explicitly unavailable.

## Accomplishments we're proud of

The latest Android rebuild passed **70 JVM tests and 20 Android instrumentation tests on an Android 30 emulator**. Additional checks verified offline referral persistence, recovery across process stops, retained records after an APK update and emulator reboot, and native journeys at smaller and larger emulated screen sizes, including increased text scaling.

We also built the API and doctor workspace around immutable snapshots, scoped access and auditable acknowledgment.

## What we learned

Reliable coordination needs explicit states, durable records and clear communication about uncertainty. We learned to test clinical rule boundaries and recovery behavior, and to distinguish software verification from evidence of clinical benefit.

## What's next

Our next steps are clinician review of rules and local escalation pathways, health-worker usability studies, broader physical-device testing and further integration rehearsal. Predictive research requires an appropriate authorized outcome-labeled cohort and a defensible validation process.

**VITALINK is a locally run research/hackathon prototype using synthetic records. It is not a validated medical device and provides no diagnosis, medication advice or validated predictive risk score. Clinical benefit and real-world impact have not been established.**
