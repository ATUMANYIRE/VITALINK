# Clinical AI research

The distributed application uses deterministic rules only. No clinically validated predictive model, clinical risk probability, SHAP plot or ONNX artifact is included.

From the repository root in PowerShell:

```powershell
$env:PYTHONPATH = "$PWD/ml"
python -m unittest discover -s ml/tests -v
python -m vitalink_research.pipeline --manifest ml/data/dataset_assessment.json
```

The admission command intentionally returns exit code **2** with BLOCKED. This is an expected safety decision, not a successful model training run. Algorithm unit tests use small mathematical fixtures; these establish software behavior only.

For a future authorized cohort, supply an approved provenance manifest and CSV with patient_id, record_id, outcome (0/1), measured_at, latest_feature_at, outcome_window_end, systolic, diastolic, gestational_weeks and optional subgroup. The manifest must provide an exact CSV SHA-256 and the assurances checked in data.py. Admission is a necessary check, not proof of data quality; signed clinical/ethics review must verify assertions independently.

With approved data and separately installed research requirements:

```powershell
python -m vitalink_research.pipeline --manifest approved.json --csv approved.csv --output private/research.json
```

The pipeline splits **patients**, not rows, into train (55%), calibration (15%), conformal (15%) and untouched test (15%). Imputation and scaling fit only on training data. Logistic regression and histogram gradient boosting are trained separately; Platt calibration uses its own partition; split-conformal evaluation uses another. Both candidates are evaluated without selecting a winner on the test set. AUROC, average precision, sensitivity, specificity, Brier score, reliability bins, abstention, empirical coverage and sufficiently sized subgroup metrics are reported. Rules-only baseline is included. Random patient splitting does not establish temporal or geographic generalization. Forty patients is a mechanical partition minimum, not a clinical sample-size recommendation.

No export occurs automatically. ONNX conversion, parity testing, external validation, operating-point review and real-device benchmarking remain gated. Do not set manifest assurances to true merely to run training. Do not place patient records or research outputs in this repository.
