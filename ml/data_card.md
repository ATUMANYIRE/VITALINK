# Data card

Local repository audit found no clinical dataset. Demo fixtures are synthetic identifiers and measurements; never an outcome-validation cohort.

The [UCI Maternal Health Risk documentation](https://archive.ics.uci.edu/dataset/863/maternal+health+risk) was reviewed on 28 October 2026. It describes rural Bangladesh clinic/hospital data, six vital/metabolic features, a RiskLevel target, and CC BY 4.0 licensing. The published schema does not supply patient linkage, feature/outcome timestamps or an independently observed maternal outcome with a prediction horizon. No records were downloaded. Do not interpret reproduction of RiskLevel as prediction of clinical outcomes. UCI's current documentation reports 1,013 instances; this was not independently recounted.

Admission decision: BLOCKED for VITALINK outcome prediction. See data/dataset_assessment.json for machine-readable reasons. Restricted clinical datasets were not accessed. A future cohort needs appropriate authorization, independently ascertained outcomes, patient grouping, index times, horizon, outcome completeness, measurement provenance and clinical/ethics review. External validation in the intended population remains necessary.
