# Model card — rules-only release

Status: **PREDICTIVE MODEL BLOCKED**. No model was trained, calibrated, exported or validated on patient outcomes. No accuracy, AUROC, sensitivity, specificity or outcome improvement is claimed.

Intended use: synthetic-data hackathon demonstrations of offline assessment and referral coordination. Excluded use: autonomous diagnosis, medication or treatment decisions, replacement of clinical assessment, and clinical deployment.

The rules-only inference contract reports mode RULES_ONLY, no model version, null probability and confidence, explicit abstention, missing features, measured software latency, rule version and deterministic triggers. Statistical feature contributions are unavailable. Severe BP and concerning symptoms trigger urgent on-device escalation irrespective of future models.

Known limitations: rules are an unvalidated prototype subset requiring clinician and local escalation review; a no-trigger result is not assurance of health; reliable BP technique and calibrated measurement equipment remain external requirements. Clinical, demographic and geographic generalization are unmeasured. No device ONNX latency is available.
