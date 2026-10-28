# Judge questions

**Does VITALINK diagnose preeclampsia?** No. It documents measurements and applies a limited, versioned escalation-rule set. Clinical assessment remains necessary.

**What happens without internet?** The Android workflow runs locally and queues an immutable referral. Local saving and attempted delivery are distinct from confirmed server receipt. Use the established local emergency pathway immediately for urgent cases.

**Can AI suppress a severe reading?** No. Severe systolic >=160 OR diastolic >=110 and concerning symptoms take precedence. This release has no predictive model and explicitly abstains.

**Why no AI accuracy number?** The reviewed data do not support the required outcome-validation design. Reproducing risk labels or testing synthetic records would not establish clinical accuracy.

**What prevents duplicates after a timeout?** The same UUID/idempotency key identifies the immutable submission. Server transactions/constraints resolve identical retries and reject conflicting snapshots. Exact tested scenarios are in the final audit.

**How is data protected?** Android clinical payloads use Keystore AES-GCM; backend credentials/sessions are hashed; API queries enforce facility scope; the browser uses HttpOnly same-origin sessions. This is not a completed production security certification.

**Has a clinician seen a referral just because HTTP returned 201?** No. 201 establishes server receipt. Explicit clinician acknowledgment records an actor/time and separate history event.

**What outcomes have improved?** No patient-outcome improvement is established. The pilot proposal measures operational handoff reliability, acknowledgment times and staff burden.

**Can this be deployed clinically today?** No. Clinical rule/local escalation review, prospective usability, privacy/ethics review, deployment security and appropriate regulatory review are unresolved.

**How much does it cost?** The INR7.78 example is an equipment/consumables scenario with explicit assumptions and exclusions, not a measured full cost or savings estimate.
