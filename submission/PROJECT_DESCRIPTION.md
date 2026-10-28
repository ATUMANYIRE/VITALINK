# VITALINK

## Project overview

VITALINK connects a maternal vital-sign assessment recorded offline with a clinician's explicit referral acknowledgment. A native Android application runs deterministic safety rules locally, stores immutable encrypted assessment snapshots, and queues referrals durably. When connectivity returns, the client submits the same referral identifier to a facility-scoped Java API. A responsive clinical workspace displays the stored referral, measurement history and rule triggers, then records who acknowledged it and when.

## Innovation

The central engineering contribution is making the handoff observable: local saving, attempted transmission, server receipt and clinician acknowledgment are separate states. A stable UUID and transactional outbox support retries after ambiguous network failures without silently creating another referral. Clinical urgency remains independent of transport and any future optimization/model.

## Clinical safety

Severe-range pregnancy BP (systolic >=160 OR diastolic >=110 mmHg) and concerning symptoms prompt urgent clinical assessment on device. Missing data never become normal findings. An unsent referral never implies that a hospital has been notified. The prototype supplies local escalation/handoff messaging and no autonomous diagnosis or medication advice.

## What AI does in this release

Predictive AI is intentionally blocked. The reviewed public risk-category dataset does not establish outcome validity for this task. A rules-only inference interface explicitly abstains from prediction. Research admission, leakage checks and evaluation tools are available for future authorized outcome cohorts. No accuracy or feature-contribution chart is invented.

## Evidence and limits

Consult the final audit for exact builds, test counts, device evidence, benchmarks and environmental blockers. Synthetic demonstrations establish software behavior only. Patient benefit, clinical accuracy, operational savings and deployment readiness require separate clinical review and field studies.
