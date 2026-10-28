# VITALINK — ten-slide presentation content

Each numbered section is one slide. Keep headings large, use warm-white/navy/teal, and prefer actual software screenshots. This is complete editable slide-ready text; no PPTX generation is implied.

## 1. VITALINK

**Offline maternal assessment and referral handoff**

Health Hackathon 3.0 · VIT Bhopal University

Research prototype · Synthetic data · Not for clinical use

Speaker notes: Introduce the handoff problem. Do not claim a diagnosis or patient-outcome improvement.

## 2. The referral handoff problem

A recorded measurement does not prove that a receiving clinician knows about it.

- Connectivity can interrupt transmission.
- Retries can create duplicates or uncertainty.
- Server receipt and clinical acknowledgment are different events.

Speaker notes: Target users are community health workers and receiving clinicians. These are workflow risks the prototype addresses, not quantified epidemiological findings.

## 3. The field-to-clinician workflow

**Measure → Check → Escalate → Save → Synchronize → Acknowledge**

Local assessment continues offline. Urgent escalation never waits for internet. A stable referral UUID connects the handoff.

Speaker notes: Show the actual Android assessment and pending referral if device evidence exists. Otherwise identify build/JVM evidence accurately.

## 4. Clinical safety rules

**Systolic >=160 OR diastolic >=110 mmHg → urgent clinical assessment**

Concerning symptoms remain urgent with normal BP. Missing inputs require review. A no-trigger result does not prove health.

Speaker notes: Source: NICE NG133 recommendations, https://www.nice.org.uk/guidance/ng133/chapter/Recommendations. This limited prototype rule set requires clinician/local pathway review. No diagnosis or medication advice is supplied.

## 5. Reliable offline referrals

**Saved locally ≠ received by server ≠ acknowledged by clinician**

Immutable assessment snapshots; encrypted Room payloads and Android Keystore; transactional outbox; bounded retry/backoff; stable idempotency key.

Speaker notes: Explain DRAFT/PENDING_SYNC/SENT/SERVER_RECEIVED/ACKNOWLEDGED. SENT is an attempt, never confirmed delivery. Explain restart and duplicate tests with exact final evidence.

## 6. Regional clinical workspace

Java 21 / Spring Boot / PostgreSQL / Flyway

Next.js / strict TypeScript / accessible responsive workflow

Facility-scoped referral queue, actual operational counts, measurement timeline, deterministic triggers and explicit acknowledgment history.

Speaker notes: Use an actual captured workspace screenshot. Explain negative authorization tests. Acknowledgment does not establish treatment or recovery.

## 7. Scientific integrity and AI

**Current release: rules-only, predictive abstention**

The reviewed risk-label dataset lacks the outcome/linkage/timing evidence needed for this task. No clinical accuracy number or invented SHAP chart is shown.

Future research tools: patient-disjoint splits, baseline/candidate comparison, calibration, uncertainty, subgroup review and untouched test evaluation.

Speaker notes: UCI schema: https://archive.ics.uci.edu/dataset/863/maternal+health+risk. Documentation reviewed; records not downloaded. No trained model or device ONNX benchmark exists.

## 8. Demonstration and evidence

Synthetic severe reading → offline referral → server receipt → doctor acknowledgment → mobile status

Executed builds, unit tests, integration tests, browser checks and device limitations are recorded in the final release audit.

Speaker notes: Rehearse DEMO_SCRIPT_3_MIN.md. Quote exact final results from ../docs/FINAL_RELEASE_AUDIT.md; do not repeat original pitch targets as achievements. Keep device-only evidence distinct from API/JVM evidence.

## 9. Economics and intended impact

**Illustrative allocation: INR7.78/screening**

INR10,000 equipment / (24 months × 150 screenings/month) + INR5 consumables

Scenario only. Excludes staff, transport, training, maintenance, hosting and follow-up.

Measure handoff reliability, receipt/acknowledgment time and staff burden in an approved pilot.

Speaker notes: No measured savings, cost-effectiveness, mortality reduction or lives-saved claim. See ECONOMIC_MODEL.md for volume sensitivity and exclusions.

## 10. Next steps and review boundary

**Clinical review → usability study → privacy/ethics approval → authorized outcome cohort → external validation**

VITALINK makes referral progress visible while preserving immediate local escalation.

Research/hackathon prototype. Clinical deployment is not authorized by hackathon testing.

Speaker notes: State remaining technical/environmental blockers from the final audit. Request appropriate collaborators for clinical and field evaluation, not permission to deploy clinically without review.
