"""Rules-only edge contract, independent of any statistical model."""

from dataclasses import asdict, dataclass
import math
from time import perf_counter_ns

from . import RULE_VERSION

SYMPTOMS = {"SEVERE_HEADACHE", "VISUAL_DISTURBANCE", "UPPER_ABDOMINAL_PAIN", "BREATHLESSNESS",
            "SEIZURE", "HEAVY_BLEEDING", "REDUCED_FETAL_MOVEMENT"}


def clinical_rules(systolic=None, diastolic=None, symptoms=()):
    def numeric(value):
        return isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value)
    invalid = (systolic is not None and (not numeric(systolic) or not 50 <= systolic <= 300)
               or diastolic is not None and (not numeric(diastolic) or not 30 <= diastolic <= 200)
               or numeric(systolic) and numeric(diastolic) and systolic <= diastolic
               or bool(set(symptoms) - SYMPTOMS))
    systolic = systolic if numeric(systolic) else None
    diastolic = diastolic if numeric(diastolic) else None
    reasons = []
    # Available severe measurements are evaluated before any missing/invalid-data gate.
    if systolic is not None and systolic >= 160:
        reasons.append("SEVERE_SYSTOLIC")
    if diastolic is not None and diastolic >= 110:
        reasons.append("SEVERE_DIASTOLIC")
    reasons.extend("SYMPTOM_" + symptom for symptom in sorted(set(symptoms) & SYMPTOMS))
    urgent = bool(reasons)
    for label, value, threshold, severe in (("SYSTOLIC", systolic, 140, 160), ("DIASTOLIC", diastolic, 90, 110)):
        if value is None:
            reasons.append("MISSING_" + label)
        elif threshold <= value < severe:
            reasons.append("ELEVATED_" + label)
    if invalid:
        reasons.append("INVALID_INPUT_REQUIRES_REVIEW")
    return ("URGENT" if urgent else "REVIEW_REQUIRED" if reasons else "NO_TRIGGER"), reasons


@dataclass(frozen=True)
class InferenceResult:
    mode: str
    model_version: str | None
    probability: float | None
    confidence: float | None
    calibration: str
    abstained: bool
    reason: str
    missing_features: tuple[str, ...]
    invalid_features: tuple[str, ...]
    clinical_urgency: str
    rule_triggers: tuple[str, ...]
    rule_version: str
    latency_ms: float


class RulesOnlyInference:
    """Model loader is deliberately unavailable until a reviewed artifact exists."""

    def predict(self, *, systolic=None, diastolic=None, gestational_weeks=None, symptoms=()):
        start = perf_counter_ns()
        urgency, triggers = clinical_rules(systolic, diastolic, symptoms)
        missing = tuple(name for name, value in (("systolic", systolic), ("diastolic", diastolic),
                                                ("gestational_weeks", gestational_weeks)) if value is None)
        invalid = tuple(name for name, value, low, high in (("systolic", systolic, 50, 300),
                                                           ("diastolic", diastolic, 30, 200),
                                                           ("gestational_weeks", gestational_weeks, 0, 45))
                        if value is not None and (isinstance(value, bool) or not isinstance(value, (int, float))
                                                  or not math.isfinite(value) or not low <= value <= high))
        if invalid and urgency == "NO_TRIGGER":
            urgency = "REVIEW_REQUIRED"
        return InferenceResult("RULES_ONLY", None, None, None, "NOT_AVAILABLE", True,
                               "No validated predictive model is installed. Clinical assessment is still required.",
                               missing, invalid, urgency, tuple(triggers), RULE_VERSION,
                               (perf_counter_ns() - start) / 1_000_000)

    def load_model(self, _path):
        raise DataError("Predictive loading is blocked pending cohort validation and clinical review")


class DataError(ValueError):
    pass
