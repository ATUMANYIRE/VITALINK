# Data provenance

Initial local files: contributor guide and pitch PDF only. No patient dataset was present. All software fixtures and demonstration records use synthetic SYN-prefixed identifiers. No names, addresses, phone numbers or national identifiers are required by the API contract.

Public documentation reviewed: [UCI Maternal Health Risk](https://archive.ics.uci.edu/dataset/863/maternal+health+risk), DOI 10.24432/C5DP5D, accessed 28 October 2026; rural Bangladesh clinic/hospital population; CC BY 4.0 stated license. Published target is RiskLevel, not a documented independently ascertained adverse outcome with timing. Records were not downloaded. Patient-group and temporal leakage cannot be evaluated from the published schema; predictive use is blocked.

Clinical rule reference: [NICE NG133 recommendations](https://www.nice.org.uk/guidance/ng133/chapter/Recommendations), accessed 28 October 2026. This informs severe BP and symptom escalation; the prototype's full symptom set and local pathway require clinician review. It is not a diagnostic implementation of the entire guideline.

No synthetic cohort is used to claim predictive validity. Unit-test probabilities and labels are arithmetic fixtures only. No restricted dataset, cloud account or real patient record was accessed.
