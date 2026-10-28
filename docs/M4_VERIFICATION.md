# Milestone 4 verification

Overall: **BLOCKED** for predictive validation, with functional rules-only fallback and research admission/evaluation tooling implemented.

| Requirement | Status | Evidence |
|---|---|---|
| Local data audit and public provenance assessment | PASS | Initial inventory; ml/data_card.md; dataset_assessment.json |
| No fabricated outcome/accuracy claims | PASS | No predictive model or performance report distributed |
| Reproducible mathematical metrics/admission tests | PASS | `PYTHONPATH=ml python -m unittest discover -s ml/tests -v`; evidence/ml/unit-tests.txt |
| Safe model-unavailable/abstention interface | PASS | RulesOnlyInference; inference tests |
| Candidate training/calibration/evaluation pipeline | PARTIAL | Implemented in ml/vitalink_research/pipeline.py; not run on an authorized cohort |
| Clinically meaningful labeled cohort and patient-level validation | BLOCKED | No suitable authorized outcome data |
| Empirical calibration, uncertainty, subgroup validity | BLOCKED | No eligible cohort |
| Validated ONNX artifact and Android ONNX parity | BLOCKED | No validated artifact exists |
| Actual device inference benchmark | BLOCKED | No predictive artifact; rules timing is a separate software benchmark |
| Clinical deployment authorization | BLOCKED | Independent clinical/ethics/regulatory review absent |

Dataset gate command: `PYTHONPATH=ml python -m vitalink_research.pipeline --manifest ml/data/dataset_assessment.json`. Expected exit **2**, status BLOCKED. This refusal is recorded separately from unit-test success. No model training success is implied.
