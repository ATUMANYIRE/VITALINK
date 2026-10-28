"""Small, dependency-free binary metrics; undefined values remain explicit."""

import math


def _validate(labels, probabilities):
    if not labels or len(labels) != len(probabilities):
        raise ValueError("Nonempty, paired labels and probabilities required")
    if any(type(y) is not int or y not in (0, 1) for y in labels):
        raise ValueError("Binary integer outcomes required")
    if any(isinstance(p, bool) or not math.isfinite(p) or not 0 <= p <= 1 for p in probabilities):
        raise ValueError("Finite probabilities in [0,1] required")


def evaluate(labels, probabilities, threshold=0.5):
    """AUROC uses tied ranks; AUPRC is stepwise average precision (not trapezoids)."""
    _validate(labels, probabilities)
    if not 0 <= threshold <= 1:
        raise ValueError("Threshold outside [0,1]")
    tp = sum(y == 1 and p >= threshold for y, p in zip(labels, probabilities))
    tn = sum(y == 0 and p < threshold for y, p in zip(labels, probabilities))
    positives = sum(labels)
    negatives = len(labels) - positives
    ordered = sorted(zip(probabilities, labels))
    rank_sum = 0.0
    i = 0
    while i < len(ordered):
        j = i + 1
        while j < len(ordered) and ordered[j][0] == ordered[i][0]:
            j += 1
        rank_sum += ((i + 1 + j) / 2) * sum(y for _, y in ordered[i:j])
        i = j
    auroc = ((rank_sum - positives * (positives + 1) / 2) / (positives * negatives)
             if positives and negatives else None)
    ap = 0.0
    found = total = 0
    for score in sorted(set(probabilities), reverse=True):
        group = [y for y, p in zip(labels, probabilities) if p == score]
        new_positives = sum(group)
        found += new_positives
        total += len(group)
        if positives:
            ap += new_positives / positives * found / total
    bins = []
    for index in range(10):
        pairs = [(y, p) for y, p in zip(labels, probabilities)
                 if min(int(p * 10), 9) == index]
        if pairs:
            bins.append({"lower": index / 10, "n": len(pairs),
                         "mean_probability": sum(p for _, p in pairs) / len(pairs),
                         "observed_fraction": sum(y for y, _ in pairs) / len(pairs)})
    return {"n": len(labels), "threshold": threshold, "tp": tp, "tn": tn,
            "fp": negatives - tn, "fn": positives - tp,
            "sensitivity": tp / positives if positives else None,
            "specificity": tn / negatives if negatives else None,
            "auroc": auroc, "auprc_average_precision": ap if positives else None,
            "brier": sum((p - y) ** 2 for y, p in zip(labels, probabilities)) / len(labels),
            "calibration_bins": bins}


def conformal_quantile(labels, probabilities, alpha=0.1):
    _validate(labels, probabilities)
    if not 0 < alpha < 1:
        raise ValueError("alpha must be strictly between 0 and 1")
    scores = sorted(1 - (p if y else 1 - p) for y, p in zip(labels, probabilities))
    k = math.ceil((len(scores) + 1) * (1 - alpha))
    return scores[k - 1] if k <= len(scores) else 1.0


def prediction_set(probability, quantile):
    if not math.isfinite(probability) or not 0 <= probability <= 1 or not 0 <= quantile <= 1:
        raise ValueError("Invalid probability or quantile")
    return [label for label, score in [(0, probability), (1, 1 - probability)] if score <= quantile]


def evaluate_abstention(labels, probabilities, quantile):
    _validate(labels, probabilities)
    sets = [prediction_set(p, quantile) for p in probabilities]
    selected = [(y, values[0]) for y, values in zip(labels, sets) if len(values) == 1]
    return {"n": len(labels),
            "empirical_coverage": sum(y in values for y, values in zip(labels, sets)) / len(labels),
            "abstention_fraction": 1 - len(selected) / len(labels),
            "singleton_accuracy": sum(y == v for y, v in selected) / len(selected) if selected else None,
            "coverage_warning": "Exchangeability required; no guarantee under distribution shift."}
