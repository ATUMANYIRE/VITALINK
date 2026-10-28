# Technical architecture

```mermaid
flowchart LR
  W[Health worker] --> A[Native Android Compose]
  A --> R[Versioned Kotlin safety rules]
  R --> E[Immediate local escalation]
  A --> D[(Room encrypted payloads)]
  D --> O[Transactional outbox]
  O -->|Stable UUID / bearer auth| API[Java 21 Spring Boot API]
  API --> PG[(PostgreSQL / Flyway)]
  C[Clinician] --> WEB[Next.js clinical workspace]
  WEB --> BFF[Same-origin server proxy]
  BFF -->|Facility-scoped auth| API
  API --> H[Explicit acknowledgment audit]
  H --> PG
  PG -->|Status polling| A
```

Assessment snapshots are immutable. Retry metadata is separate from clinical acknowledgment. Server rules recompute urgency; no client or model priority is trusted. Clinical payload encryption uses Android Keystore AES-GCM; opaque local IDs/status metadata remain queryable. Browser tokens are carried in encrypted HttpOnly session cookies and forwarded by the same-origin proxy, never localStorage.

No predictive model or ONNX artifact is deployed. The rules-only fallback remains functional when inference is unavailable. Real integration and environmental limits are documented in ../docs/FINAL_RELEASE_AUDIT.md.
