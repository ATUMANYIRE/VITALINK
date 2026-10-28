# Executive summary

VITALINK is an offline maternal screening and referral coordination prototype for community health workers and receiving clinicians. It addresses a practical handoff problem: recording a concerning measurement does not establish that the receiving team knows about it.

The system combines native Kotlin/Compose assessment, local deterministic safety rules, Keystore-protected encrypted clinical payloads, a durable referral outbox, a Java 21/Spring Boot API with PostgreSQL, and an accessible Next.js doctor workspace. UUID-based idempotency and explicit received/acknowledged states make referral progress visible. Facility-scoped access and auditable clinician actions support accountable coordination.

The scientific boundary is deliberate. No suitable outcome-validation cohort was available, so the release uses rules only and reports predictive abstention. The project makes no diagnostic, sensitivity, mortality-reduction or savings claim. Exact engineering evidence and remaining blockers are listed in the final release audit.

The next phase requires local clinician review of rules and escalation pathways, prospective usability assessment, privacy/ethics approval and an authorized outcome cohort before predictive research deployment can be considered.
