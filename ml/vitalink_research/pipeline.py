"""Gated candidate research pipeline. Does not authorize deployment or auto-select on test data."""

import argparse
import importlib.metadata
import json
import math
from pathlib import Path

from .data import DataBlocked, FEATURES, assess_manifest, load_cohort, partition
from .metrics import conformal_quantile, evaluate, evaluate_abstention


def run(csv_path, manifest_path, output_path):
    rows, manifest = load_cohort(csv_path, manifest_path)
    groups = partition(rows)
    # Optional dependencies are loaded only after admission. No downloads or hidden data acquisition.
    import numpy as np
    from sklearn.ensemble import HistGradientBoostingClassifier
    from sklearn.impute import SimpleImputer
    from sklearn.linear_model import LogisticRegression
    from sklearn.pipeline import make_pipeline
    from sklearn.preprocessing import StandardScaler

    def xy(name):
        selected = groups[name]
        return (np.array([[float(row[f]) if row.get(f) not in (None, "") else np.nan
                           for f in FEATURES] for row in selected]), np.array([int(row["outcome"]) for row in selected]))

    train_x, train_y = xy("train")
    if np.isnan(train_x).all(axis=0).any():
        raise DataBlocked("Training feature is entirely missing")
    calibration_x, calibration_y = xy("calibration")
    conformal_x, conformal_y = xy("conformal")
    test_x, test_y = xy("test")
    candidates = {
        "logistic": make_pipeline(SimpleImputer(add_indicator=True), StandardScaler(),
                                   LogisticRegression(max_iter=2000, random_state=2026)),
        "histogram_gradient_boosting": make_pipeline(SimpleImputer(add_indicator=True),
                                                      HistGradientBoostingClassifier(max_iter=100, max_leaf_nodes=7,
                                                                                    random_state=2026))}
    report = {"status": "RESEARCH_ONLY", "clinical_deployment_authorized": False,
              "dataset_sha256": manifest["sha256"], "seed": 2026, "features": FEATURES,
              "versions": {name: importlib.metadata.version(name) for name in ("numpy", "scikit-learn")},
              "partition_counts": {name: {"rows": len(items), "patients": len({r['patient_id'] for r in items})}
                                   for name, items in groups.items()}, "models": {}}
    for name, candidate in candidates.items():
        candidate.fit(train_x, train_y)
        def logits(x):
            p = np.clip(candidate.predict_proba(x)[:, 1], 1e-7, 1 - 1e-7)
            return np.log(p / (1 - p)).reshape(-1, 1)
        calibrator = LogisticRegression(random_state=2026).fit(logits(calibration_x), calibration_y)
        conf_p = calibrator.predict_proba(logits(conformal_x))[:, 1].tolist()
        test_p = calibrator.predict_proba(logits(test_x))[:, 1].tolist()
        quantile = conformal_quantile(conformal_y.tolist(), conf_p)
        subgroup_report = {}
        for subgroup in sorted({r.get("subgroup", "UNSPECIFIED") for r in groups["test"]}):
            indices = [i for i, row in enumerate(groups["test"]) if row.get("subgroup", "UNSPECIFIED") == subgroup]
            subgroup_report[subgroup] = (evaluate([int(test_y[i]) for i in indices], [test_p[i] for i in indices])
                                         if len(indices) >= 30 else {"status": "INSUFFICIENT_SAMPLE", "n": len(indices)})
        report["models"][name] = {"raw": evaluate(test_y.tolist(), candidate.predict_proba(test_x)[:, 1].tolist()),
                                   "calibrated": evaluate(test_y.tolist(), test_p),
                                   "conformal": evaluate_abstention(test_y.tolist(), test_p, quantile),
                                   "subgroups": subgroup_report}
    from .inference import clinical_rules
    baseline = []
    for row in groups["test"]:
        urgency, _ = clinical_rules(*(float(row[f]) if row.get(f) not in (None, "") else None for f in FEATURES[:2]))
        baseline.append(1.0 if urgency == "URGENT" else 0.0)
    report["rules_only_baseline"] = evaluate(test_y.tolist(), baseline)
    report["limitations"] = ["Random patient split does not establish transportability or temporal generalization.",
                              "Conformal marginal coverage assumes exchangeability, not subgroup or shifted-population coverage.",
                              "No deployment artifact is exported; external validation, clinical review and compatible ONNX parity tests required.",
                              "Fixed threshold 0.5 is a research comparison, not an approved clinical operating point."]
    destination = Path(output_path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, allow_nan=False), encoding="utf-8")
    return report


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--csv")
    parser.add_argument("--output", default="ml/reports/research_evaluation.json")
    arguments = parser.parse_args()
    if not arguments.csv:
        decision = assess_manifest(json.loads(Path(arguments.manifest).read_text(encoding="utf-8")))
        print(json.dumps(decision, indent=2))
        return 2 if decision["status"] == "BLOCKED" else 0
    try:
        run(arguments.csv, arguments.manifest, arguments.output)
    except (DataBlocked, ImportError) as exc:
        print(json.dumps({"status": "BLOCKED", "reason": str(exc), "clinical_deployment_authorized": False}))
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
