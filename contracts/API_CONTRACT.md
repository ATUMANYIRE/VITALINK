# VITALINK API contract v1

Research prototype; synthetic records only. JSON UTF-8; UTC ISO-8601 instants. Base path `/api/v1`. Development API host port **8087**, web host port **3017**, PostgreSQL project port **55432**. Ports 8080, 3000 and 3007 are occupied by unrelated applications; do not stop them. Compose maps host8087 to API container8080 and host3017 to web container3000. UUIDs are canonical strings. Rule version `vitalink-rules-1.0.0`.

## Authentication

`POST /auth/login` body `{ "username": "...", "password": "..." }` returns `{ "token": "opaque-random-value", "expiresAt": "...", "user": { "username": "...", "role": "HEALTH_WORKER|DOCTOR|ADMIN", "facilityId": "demo-facility" } }`.

Authenticated requests use `Authorization: Bearer <token>`. `POST /auth/logout` revokes the session. Tokens expire after eight hours or less. Password hashes and demo credentials are supplied at runtime, never fixed production defaults. No roles or facility scopes are accepted from login requests. All roles are restricted to their configured facility; ADMIN does not imply cross-facility access. Web proxies requests server-side using HttpOnly SameSite session cookies; never store tokens in localStorage. Android encrypts tokens using Keystore. Health workers submit; DOCTOR/ADMIN acknowledge and close; all roles can read their own facility. Wrong-facility object reads respond 404.

## Submission

`POST /sync/referrals`, HEALTH_WORKER/ADMIN. Header `Idempotency-Key` equals body `id`. Body:

```json
{
  "id": "611147a5-4073-491d-9227-3731f1f1a101",
  "facilityId": "demo-facility",
  "assessment": {
    "id": "611147a5-4073-491d-9227-3731f1f1a102",
    "patientId": "SYN-001",
    "measuredAt": "2026-10-28T05:00:00Z",
    "systolic": 160,
    "diastolic": 90,
    "gestationalWeeks": 32,
    "symptoms": [],
    "source": "MANUAL",
    "ruleVersion": "vitalink-rules-1.0.0"
  }
}
```

Nullable BP values mean missing, never zero/normal. Gestational weeks nullable 0–45. Systolic integer 50–300; diastolic 30–200; when both present systolic must exceed diastolic. Patient ID `SYN-[A-Za-z0-9-]{1,40}`. Source MANUAL or DEVICE. Symptoms are unique enum strings: SEVERE_HEADACHE, VISUAL_DISTURBANCE, UPPER_ABDOMINAL_PAIN, BREATHLESSNESS, SEIZURE, HEAVY_BLEEDING, REDUCED_FETAL_MOVEMENT. Unknown symptoms/rule versions are rejected. measuredAt must be a parseable instant, no more than five minutes in the future. Clinical safety evaluates available severe values/symptoms even if another input is invalid; invalid records cannot be submitted until corrected.

First submission returns 201; identical replay returns 200 with current server state. Same id with different snapshot/facility returns 409; wrong facility 403/404. Snapshot is immutable. Database uniqueness plus transactions enforce concurrent idempotency. Do not use a new UUID on retry after timeout.

## Referral response and state

Response object: `{id,facilityId,assessment,status,urgency,ruleTriggers,receivedAt,acknowledgedAt,acknowledgedBy}`. Optional acknowledgment fields are null until explicitly acknowledged. Server `status`: SERVER_RECEIVED, ACKNOWLEDGED, CLOSED. `urgency`: URGENT, REVIEW_REQUIRED, NO_TRIGGER. `ruleTriggers`: stable string codes.

Client states: DRAFT → PENDING_SYNC → SENT → SERVER_RECEIVED → ACKNOWLEDGED → CLOSED. SENT means an in-flight attempt only, never delivery. Failure metadata (attemptCount, nextAttemptAt, lastErrorCode) is separate; after an expired attempt lease, SENT returns to PENDING_SYNC. Delivery advances only after validated server response matching the same referral UUID and facility. Retry uses exponential delay with bounded jitter, and distinguishes terminal validation/auth/conflict errors. Poll received referrals to obtain clinician acknowledgment. Local drafts may be discarded; queued snapshots cannot be edited.

## Read and clinical actions

- `GET /referrals?page=0&size=20&urgency=URGENT&status=SERVER_RECEIVED`: `{items:[Referral],page,size,totalElements,totalPages}`. Filters optional; bounded size 1–100. Sort urgent first, then oldest received first. Metrics aggregate all authorized rows, not just a page.
- `GET /referrals/{id}`: Referral.
- `GET /referrals/{id}/history`: `[{status,at,actor}]`, ordered chronologically.
- `GET /metrics`: `{total,urgent,awaitingAcknowledgment,acknowledged,closed}` based on scoped stored records.
- `POST /referrals/{id}/acknowledge`: empty body; DOCTOR/ADMIN; idempotent, returns Referral. Records authenticated actor and server timestamp. No automatic acknowledgment on ingestion.
- `POST /referrals/{id}/close`: empty body; DOCTOR/ADMIN; only after acknowledgment; idempotent.
- `GET /health`: unauthenticated minimal status, no infrastructure secrets. Database readiness via Actuator readiness endpoint.

## Rules and errors

Severe BP (systolic >=160 OR diastolic >=110) and any listed concerning symptom → URGENT. Else systolic >=140 OR diastolic >=90 OR missing BP → REVIEW_REQUIRED. Else NO_TRIGGER, explicitly not assurance of health. Rule codes: SEVERE_SYSTOLIC, SEVERE_DIASTOLIC, SYMPTOM_<enum>, ELEVATED_SYSTOLIC, ELEVATED_DIASTOLIC, MISSING_SYSTOLIC, MISSING_DIASTOLIC. Server recomputes findings; it never trusts client priority. No treatment or diagnostic advice.

`ruleTriggers` includes **all observed flags**, including missing/elevated flags when another measurement or symptom is urgent. Example 160/90 → SEVERE_SYSTOLIC + ELEVATED_DIASTOLIC; 160/null → SEVERE_SYSTOLIC + MISSING_DIASTOLIC. Trigger order is not significant; duplicates are prohibited. Urgency precedence is independent of trigger-list completeness.

Errors follow Problem Details: `{type,title,status,detail,code,instance}` with stable code. Never echo passwords, tokens, SQL, stack traces or patient payloads. Typical statuses: 400 invalid JSON/input, 401 unauthenticated/expired, 403 unauthorized role/scope, 404 absent/out-of-scope object, 409 conflicting snapshot/transition, 429 throttled, 503 service unavailable. All clinical and auth responses use Cache-Control: no-store.
