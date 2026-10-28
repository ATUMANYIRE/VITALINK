# Clinical AI validation

## Release decision

**BLOCKED for predictive deployment; rules-only workflow retained.** No suitable outcome-labeled cohort was present in the repository. No predictive model was trained or exported. There are no clinical accuracy, sensitivity, specificity, calibration, conformal-coverage or patient-outcome results to report. Software tests on mathematical fixtures do not establish clinical validity.

## Dataset review

The [UCI Maternal Health Risk schema](https://archive.ics.uci.edu/dataset/863/maternal+health+risk) describes RiskLevel based on vital/metabolic attributes and rural Bangladesh data. It does not provide patient linkage, feature/outcome timing or an independently observed adverse maternal outcome/horizon. Its CC BY 4.0 license does not resolve those scientific limitations. No records were downloaded. Other restricted clinical datasets were not accessed. See ml/data_card.md and ml/data/dataset_assessment.json.

## Implemented research tooling

Manifest admission checks provenance, authorization, labels, patient linkage, timing, clinician/ethics review assertions and a CSV checksum. CSV validation rejects duplicate records, missing grouping, nonbinary labels, nonfinite/out-of-range inputs and post-index features. Patient-disjoint fixed-seed partitions separate training, probability calibration, conformal calibration and untouched test data. Training-only imputation avoids fitting preprocessing to held-out data.

The optional pipeline implements logistic regression and histogram gradient boosting, separate Platt calibration, fixed-threshold sensitivity/specificity, tied-rank AUROC, stepwise average precision, Brier score, reliability bins, conformal prediction sets and abstention, subgroup metrics with explicit small-sample suppression, and a deterministic-rule baseline. It is unexecuted on a real cohort. Dependencies must be installed and locked in a permitted research environment. The current installed default Python lacks scikit-learn; rules-only tools need only the standard library.

Conformal coverage assumes exchangeability. It does not guarantee coverage under geographic, temporal, measurement or population shift. A random patient split does not establish temporal/external validation. Threshold 0.5 is an algorithm comparison only. Forty patients is a mechanical split floor, not a clinical sample-size calculation. Manifest assertions need independent review; setting booleans does not confer authorization.

## Edge behavior

The rules-only contract reports null risk/confidence, explicit abstention, missing/invalid features, model-unavailable reason, rule version, actual rule triggers and measured software latency. Predictive loading is disabled. Android must retain the same safety hierarchy. No invented SHAP values, decorative feature contributions or predictive confidence are permitted. ONNX export/parity, supported operator checks and device inference timing remain blocked until a reviewed artifact exists.

## Clinical review still required

Outcome definition and horizon; cohort sampling and completeness; measurement quality; external population evaluation; subgroup harm; prospective workflow usability; operating-point and abstention policy; local escalation pathways; privacy/ethics authorization; and applicable regulatory review are unresolved. Hackathon verification cannot authorize clinical deployment.
