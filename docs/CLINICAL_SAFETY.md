# Clinical safety specification — vitalink-rules-1.0.0

## Intended use and boundary

VITALINK is a research/hackathon prototype using synthetic records. It is not a validated medical device. It supports measurement documentation and referral handoff; it does not diagnose preeclampsia, establish health, or recommend medication/treatment. Clinical review, local escalation approval, measurement-device validation and privacy/ethics review are required before any patient use.

## Deterministic hierarchy

1. Systolic **>=160 OR diastolic >=110 mmHg** triggers **URGENT clinical assessment**. One qualifying available reading is sufficient. Invalid or missing other data cannot suppress this alert.
2. Any concerning symptom triggers URGENT even with normal BP: severe headache, visual disturbance, upper abdominal pain, breathlessness, seizure, heavy bleeding, reduced fetal movement. This broader prototype symptom set requires local clinician review.
3. Otherwise systolic >=140 OR diastolic >=90, or missing BP, requires clinical review. Invalid values cannot be interpreted as reassurance and cannot be submitted as valid measurements.
4. Otherwise NO_TRIGGER means only that these limited rules did not trigger. It does not establish that the patient is healthy or exclude other conditions.

The [NICE NG133 recommendations](https://www.nice.org.uk/guidance/ng133/chapter/Recommendations) inform severe BP and symptom escalation. This prototype is not a complete guideline implementation. Rule thresholds are versioned in contracts/API_CONTRACT.md and implemented independently in Android domain and server; shared regression cases live in contracts/clinical-cases.json. Rules may change only through documented clinical review and regression testing, never to make tests green.

## Escalation and handoff

Urgent findings display immediate on-device escalation: contact the responsible clinician or local emergency pathway now and arrange assessment under local protocol. Do not wait for connectivity or app synchronization. No unreviewed emergency phone number or autonomous treatment instruction is embedded. In an outage use the offline handoff summary and established telephone/in-person route. A locally saved record has **not notified** the receiving facility.

Local DRAFT/PENDING_SYNC/SENT states differ from confirmed SERVER_RECEIVED and explicit clinician ACKNOWLEDGED. HTTP acceptance is not clinical acknowledgment. Transport retries never substitute for escalation. Acknowledgment is a coordination event, not proof that care or clinical review occurred. CLOSED records a coordination action only; it does not imply recovery.

## Hazard controls

| Hazard | Control | Residual limitation |
|---|---|---|
| Low model score masks severe BP or symptoms | Rules authoritative; rules-only release; no predictive risk output | Clinical review still needed for completeness |
| Missing data appear normal | Explicit missing/review state; validation | Measurements may still be inaccurate |
| Outage delays urgent assessment | On-device alert and fallback handoff | Transport/clinical response outside application control |
| Duplicate/conflicting referral after timeout | Stable UUID, immutable snapshot, transactional outbox, server constraints | Must verify actual device and server recovery |
| Delivery confused with care | Separate local, received, acknowledged states and history | No guarantee of clinical action |
| Unauthorized facility access | Scoped server queries and negative tests | Deployment/IAM configuration still requires review |
| Lost/unencrypted local records | Room transaction, encrypted payload, Keystore, backup disabled | Device compromise/key loss remain risks |

## Release-review requirements

Review severe/missing/symptom fixtures; invalid measurements; maternity/postpartum scope; symptom wording and translations; BP technique; local escalation/contact availability; accessibility and usability; record-retention policy; failure recovery; incident reporting; and changes in guideline evidence. No clinical-performance percentage, diagnosis, lives-saved estimate or device certification is claimed by software tests.
