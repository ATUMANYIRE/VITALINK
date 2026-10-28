"""Cohort admission gate and deterministic, patient-disjoint partitions."""

import csv
import hashlib
import json
import random
from datetime import datetime
from pathlib import Path

FEATURES = ["systolic", "diastolic", "gestational_weeks"]
PARTITIONS = ("train", "calibration", "conformal", "test")


class DataBlocked(ValueError):
    pass


def assess_manifest(manifest):
    reasons = []
    for field in ("source", "license", "population", "outcome_definition", "outcome_horizon",
                  "patient_id_definition", "label_ascertainment", "ethics_review_reference"):
        if not isinstance(manifest.get(field), str) or not manifest[field].strip():
            reasons.append(f"Missing provenance: {field}")
    for flag in ("authorized_for_research", "independently_observed_outcome", "patient_linkage_available",
                 "feature_timing_available", "clinician_reviewed_outcome"):
        if manifest.get(flag) is not True:
            reasons.append(f"Required assurance absent: {flag}")
    if manifest.get("synthetic") is not False:
        reasons.append("Synthetic or unclassified data cannot establish predictive validity")
    return {"status": "ELIGIBLE_FOR_RESEARCH_REVIEW" if not reasons else "BLOCKED", "reasons": reasons,
            "clinical_deployment_authorized": False}


def _instant(value):
    result = datetime.fromisoformat(value.replace("Z", "+00:00"))
    if result.tzinfo is None:
        raise DataBlocked("Timestamp must include timezone")
    return result


def validate_rows(rows):
    if not rows:
        raise DataBlocked("Empty cohort")
    seen = set()
    for row in rows:
        if not row.get("patient_id") or not row.get("record_id"):
            raise DataBlocked("Patient and record identifiers required")
        if row["record_id"] in seen:
            raise DataBlocked("Duplicate record identifier")
        seen.add(row["record_id"])
        if str(row.get("outcome")) not in ("0", "1"):
            raise DataBlocked("Independently ascertained binary outcome required")
        try:
            if _instant(row["measured_at"]) >= _instant(row["outcome_window_end"]):
                raise DataBlocked("Feature measurement at/after outcome-window end")
            if _instant(row["latest_feature_at"]) > _instant(row["measured_at"]):
                raise DataBlocked("Post-index feature leakage")
        except (KeyError, ValueError) as exc:
            raise DataBlocked(f"Invalid temporal metadata: {type(exc).__name__}") from exc
        for field, low, high in (("systolic", 50, 300), ("diastolic", 30, 200), ("gestational_weeks", 0, 45)):
            value = row.get(field)
            if value not in (None, ""):
                try:
                    if not low <= float(value) <= high:
                        raise ValueError("outside range")
                except (TypeError, ValueError) as exc:
                    raise DataBlocked(f"Invalid feature: {field}") from exc
    return rows


def partition(rows, seed=2026):
    validate_rows(rows)
    patients = sorted({row["patient_id"] for row in rows})
    if len(patients) < 40:
        raise DataBlocked("At least 40 patients needed for four research partitions; not a clinical sample-size calculation")
    random.Random(seed).shuffle(patients)
    n = len(patients)
    first, second, third = int(n * .55), int(n * .7), int(n * .85)
    groups = dict(zip(PARTITIONS, (patients[:first], patients[first:second], patients[second:third], patients[third:])))
    assignment = {patient: name for name, group in groups.items() for patient in group}
    result = {name: [row for row in rows if assignment[row["patient_id"]] == name] for name in PARTITIONS}
    for name, items in result.items():
        if {int(row["outcome"]) for row in items} != {0, 1}:
            raise DataBlocked(f"Partition {name} must contain both outcomes; collect more data, do not resample test sets")
    return result


def load_cohort(csv_path, manifest_path):
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    decision = assess_manifest(manifest)
    if decision["status"] == "BLOCKED":
        raise DataBlocked("; ".join(decision["reasons"]))
    path = Path(csv_path)
    expected = manifest.get("sha256")
    if not expected or hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise DataBlocked("Dataset checksum does not match approved manifest")
    with path.open(encoding="utf-8", newline="") as handle:
        return validate_rows(list(csv.DictReader(handle))), manifest
