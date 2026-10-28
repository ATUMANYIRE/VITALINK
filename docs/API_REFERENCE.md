# API reference

The normative wire contract is [contracts/API_CONTRACT.md](../contracts/API_CONTRACT.md); machine-readable OpenAPI is supplied by services/api and `/api/v1/openapi.yaml`. Local host base: http://127.0.0.1:8087/api/v1. Compose internal API uses port8080.

| Method | Path | Role and effect |
|---|---|---|
| POST | /auth/login | Username/password; returns expiring opaque bearer session |
| POST | /auth/logout | Revoke authenticated session |
| POST | /sync/referrals | HEALTH_WORKER/ADMIN in own facility; stable UUID/key and immutable snapshot |
| GET | /referrals | Own-facility queue; bounded pagination, urgency/status and supported patient filtering |
| GET | /referrals/{id} | Own-facility referral; wrong-facility object is404 |
| GET | /referrals/{id}/history | Ordered receipt/acknowledgment/closure events |
| POST | /referrals/{id}/acknowledge | DOCTOR/ADMIN; explicit actor/time, idempotent |
| POST | /referrals/{id}/close | DOCTOR/ADMIN; only after acknowledgment |
| GET | /metrics | Scoped actual stored-record aggregates |
| GET | /health | Minimal unauthenticated health response |
| GET | /actuator/health/readiness | Database-inclusive readiness (outside /api/v1 base) |

Use `Authorization: Bearer <runtime-token>`. Never put tokens in URLs, source or logs. Body and header Idempotency-Key must contain the same referral UUID. First creation201; identical replay200 with current status; conflicting snapshot409. Server receipt never implies clinician acknowledgment.

Assessment measurements use mmHg; systolic/diastolic and gestational weeks may be null. Missing is never zero/normal. Source and measuredAt are explicit. Patient IDs must match SYN-[A-Za-z0-9-]{1,40}. Symptoms/rule versions are restricted. The server rejects unknown properties, invalid types/ranges/order, future measurement times and unauthorized facility scope.

Errors use Problem Details with stable code and safe detail.400 invalid,401 session absent/expired,403 role/scope prohibited,404 unavailable object,409 conflict/transition,413 body limit,429 throttled,503 unavailable. No stack trace, SQL or patient payload is exposed. Clinical/auth responses are no-store. Backend bearer authentication does not use browser cookies; browser mutation CSRF protection is supplied by the same-origin Next proxy.
