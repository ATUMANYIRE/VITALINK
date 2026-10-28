import json
import math
import unittest
from pathlib import Path

from vitalink_research.data import DataBlocked, assess_manifest, partition, validate_rows
from vitalink_research.inference import DataError, RulesOnlyInference, clinical_rules
from vitalink_research.metrics import conformal_quantile, evaluate, evaluate_abstention, prediction_set


class MetricTests(unittest.TestCase):
    def test_perfect_predictions(self):
        result = evaluate([0, 1, 0, 1], [0., 1., 0., 1.])
        for name in ("auroc", "auprc_average_precision", "sensitivity", "specificity"):
            self.assertEqual(1., result[name])
        self.assertEqual(0., result["brier"])

    def test_tied_predictions(self):
        result = evaluate([0, 1, 0, 1], [.5] * 4)
        self.assertEqual(.5, result["auroc"])
        self.assertEqual(.5, result["auprc_average_precision"])
        self.assertEqual(.25, result["brier"])

    def test_known_ranking(self):
        result = evaluate([0, 0, 1, 1], [.1, .4, .35, .8])
        self.assertEqual(.75, result["auroc"])
        self.assertAlmostEqual(5 / 6, result["auprc_average_precision"])

    def test_undefined_denominators_remain_null(self):
        result = evaluate([0, 0], [.1, .2])
        self.assertIsNone(result["sensitivity"])
        self.assertIsNone(result["auroc"])
        self.assertIsNone(result["auprc_average_precision"])

    def test_reject_invalid_probabilities(self):
        for probabilities in ([math.nan], [math.inf], [-.1], [1.1], [True]):
            with self.subTest(probabilities=probabilities), self.assertRaises(ValueError):
                evaluate([1], probabilities)

    def test_reject_invalid_labels_and_shapes(self):
        for labels, p in (([], []), ([1], []), ([2], [.5]), ([True], [.5])):
            with self.assertRaises(ValueError):
                evaluate(labels, p)

    def test_finite_sample_conformal_small_cohort(self):
        self.assertEqual(1., conformal_quantile([0, 1], [.1, .9], alpha=.1))
        self.assertEqual([0, 1], prediction_set(.5, 1))

    def test_conformal_abstention(self):
        result = evaluate_abstention([0, 1], [.1, .9], .2)
        self.assertEqual(0., result["abstention_fraction"])
        self.assertEqual(1., result["empirical_coverage"])

    def test_empty_sets_abstain(self):
        self.assertEqual([], prediction_set(.5, .1))
        self.assertIsNone(evaluate_abstention([0, 1], [.5, .5], .1)["singleton_accuracy"])


class GateTests(unittest.TestCase):
    def test_uci_assessment_is_blocked(self):
        manifest = json.loads((Path(__file__).parents[1] / "data/dataset_assessment.json").read_text())
        self.assertEqual("BLOCKED", assess_manifest(manifest)["status"])
        self.assertFalse(assess_manifest(manifest)["clinical_deployment_authorized"])

    def test_empty_manifest_is_blocked(self):
        self.assertEqual("BLOCKED", assess_manifest({})["status"])

    def row(self, patient="p", record="r"):
        return {"patient_id": patient, "record_id": record, "outcome": "1",
                "measured_at": "2025-01-01T00:00:00Z", "latest_feature_at": "2025-01-01T00:00:00Z",
                "outcome_window_end": "2025-02-01T00:00:00Z", "systolic": "120", "diastolic": "80",
                "gestational_weeks": "30"}

    def test_post_index_features_rejected(self):
        row = self.row()
        row["latest_feature_at"] = "2025-01-02T00:00:00Z"
        with self.assertRaises(DataBlocked):
            validate_rows([row])

    def test_duplicate_records_rejected(self):
        with self.assertRaises(DataBlocked):
            validate_rows([self.row(), self.row()])

    def test_missing_patient_linkage_rejected(self):
        row = self.row()
        del row["patient_id"]
        with self.assertRaises(DataBlocked):
            validate_rows([row])

    def test_naive_time_rejected(self):
        row = self.row()
        row["measured_at"] = "2025-01-01T00:00:00"
        with self.assertRaises(DataBlocked):
            validate_rows([row])

    def test_nonfinite_features_rejected(self):
        row = self.row()
        row["systolic"] = "nan"
        with self.assertRaises(DataBlocked):
            validate_rows([row])

    def test_patient_partitions_are_disjoint_and_reproducible(self):
        # Algorithm fixture only, never presented as clinical outcome evidence.
        rows = []
        for i in range(100):
            for j in range(2):
                row = self.row(f"p{i}", f"r{i}-{j}")
                row["outcome"] = str(i % 2)
                rows.append(row)
        result = partition(rows)
        self.assertEqual(result, partition(rows))
        groups = [{r['patient_id'] for r in items} for items in result.values()]
        self.assertEqual(100, len(set.union(*groups)))
        for i, group in enumerate(groups):
            for other in groups[i + 1:]:
                self.assertFalse(group & other)

    def test_small_cohort_rejected(self):
        with self.assertRaises(DataBlocked):
            partition([self.row()])


class InferenceTests(unittest.TestCase):
    def test_severe_systolic_with_missing_diastolic(self):
        result = RulesOnlyInference().predict(systolic=160)
        self.assertEqual("URGENT", result.clinical_urgency)
        self.assertTrue(result.abstained)
        self.assertIsNone(result.probability)

    def test_severe_diastolic(self):
        self.assertEqual("URGENT", clinical_rules(150, 110)[0])

    def test_symptoms_override_normal_bp(self):
        self.assertEqual("URGENT", clinical_rules(120, 80, ["SEVERE_HEADACHE"])[0])

    def test_missing_is_not_normal(self):
        self.assertEqual("REVIEW_REQUIRED", clinical_rules()[0])

    def test_no_trigger_is_not_healthy(self):
        result = RulesOnlyInference().predict(systolic=120, diastolic=80)
        self.assertEqual("NO_TRIGGER", result.clinical_urgency)
        self.assertIn("Clinical assessment", result.reason)

    def test_model_loading_is_blocked(self):
        with self.assertRaises(DataError):
            RulesOnlyInference().load_model("unreviewed.onnx")

    def test_invalid_input_cannot_reassure(self):
        for value in (math.nan, math.inf, "120", True, 10):
            with self.subTest(value=value):
                result = RulesOnlyInference().predict(systolic=value, diastolic=80)
                self.assertEqual("REVIEW_REQUIRED", result.clinical_urgency)
                self.assertIn("systolic", result.invalid_features)

    def test_invalid_other_input_cannot_suppress_urgent(self):
        result = RulesOnlyInference().predict(systolic=160, diastolic=math.nan)
        self.assertEqual("URGENT", result.clinical_urgency)

    def test_boundaries(self):
        for systolic, diastolic, expected in ((159, 109, "REVIEW_REQUIRED"), (160, 109, "URGENT"),
                                             (159, 110, "URGENT"), (160, 110, "URGENT"),
                                             (139, 89, "NO_TRIGGER"), (140, 80, "REVIEW_REQUIRED"),
                                             (120, 90, "REVIEW_REQUIRED")):
            with self.subTest(systolic=systolic, diastolic=diastolic):
                self.assertEqual(expected, clinical_rules(systolic, diastolic)[0])


if __name__ == "__main__":
    unittest.main()
