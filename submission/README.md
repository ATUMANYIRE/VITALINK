# VITALINK Submission Package

Health Hackathon 3.0 · VIT Bhopal University  
Offline maternal assessment and auditable referral coordination using synthetic records (`SYN-*`).

## Package Contents

- [`PROJECT_DESCRIPTION.md`](PROJECT_DESCRIPTION.md) — Complete system overview, problem statement, and scope.
- [`EXECUTIVE_SUMMARY.md`](EXECUTIVE_SUMMARY.md) — Executive summary of verified engineering and clinical safety properties.
- [`TECHNICAL_ARCHITECTURE.md`](TECHNICAL_ARCHITECTURE.md) — Android, Spring Boot, PostgreSQL, Next.js, and Python research architecture.
- [`CLINICAL_SAFETY.md`](CLINICAL_SAFETY.md) — Deterministic safety rules (`v1.0.0-ng133-research`), OR boundary semantics, and abstention guarantees.
- [`ECONOMIC_MODEL.md`](ECONOMIC_MODEL.md) — Illustrative `INR 7.78/screening` unit allocation model with volume sensitivity and explicit exclusions.
- [`DEMO_SCRIPT_3_MIN.md`](DEMO_SCRIPT_3_MIN.md) — Reproducible 3-minute live demonstration sequence.
- [`JUDGE_QA.md`](JUDGE_QA.md) — Evidence-backed responses to clinical, technical, AI, and operational judge questions.
- [`SLIDES_10.md`](SLIDES_10.md) — Complete 10-slide presentation text and speaker notes.
- [`VITALINK_Updated_Pitch_Deck.pdf`](VITALINK_Updated_Pitch_Deck.pdf) — Generated 10-slide PDF presentation incorporating real application screenshots and audited verification counts (original [`../VITALINK_Hackathon_Pitch.pdf`](../VITALINK_Hackathon_Pitch.pdf) is preserved untouched at the repository root).
- [`SCREENSHOT_CHECKLIST.md`](SCREENSHOT_CHECKLIST.md) — Inventory of real captured screenshots (`../apps/web/evidence/*.png`) and remaining physical-device captures.
- [`SUBMISSION_CHECKLIST.md`](SUBMISSION_CHECKLIST.md) — Pre-submission verification checklist.

## Engineering Evidence

Engineering verification is maintained in [`../docs/FINAL_RELEASE_AUDIT.md`](../docs/FINAL_RELEASE_AUDIT.md), [`../docs/TEST_EVIDENCE.md`](../docs/TEST_EVIDENCE.md), and [`../evidence/`](../evidence/). Actual executed checks (`221/221` automated tests passing, `app-debug.apk` built, `vitalink-api-0.1.0.jar` built, Next.js production bundle built) take precedence over original pitch targets. Rules-only research prototype; no clinical diagnosis or predictive accuracy claim is made. No public deployment, remote push, or external submission is performed by this package.
