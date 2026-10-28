"""Run reproducible software-only research checks and preserve clean evidence."""

import json
import os
import platform
import statistics
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "ml"))
from vitalink_research.inference import RulesOnlyInference, clinical_rules


def main():
    destination = ROOT / "evidence/ml"
    destination.mkdir(parents=True, exist_ok=True)
    environment = dict(os.environ, PYTHONPATH=str(ROOT / "ml"))
    checks = [([sys.executable, "-m", "unittest", "discover", "-s", "ml/tests", "-v"], 0, "unit-tests"),
              ([sys.executable, "-m", "vitalink_research.pipeline", "--manifest", "ml/data/dataset_assessment.json"],
               2, "dataset-gate")]
    failed = False
    summary = []
    for command, expected, name in checks:
        result = subprocess.run(command, cwd=ROOT, env=environment, capture_output=True, text=True, timeout=60)
        (destination / f"{name}.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
        summary.append({"name": name, "command": command, "exit_code": result.returncode,
                        "expected_exit_code": expected, "passed": result.returncode == expected})
        failed |= result.returncode != expected
    cases = json.loads((ROOT / "contracts/clinical-cases.json").read_text())
    for case in cases:
        assert clinical_rules(case["systolic"], case["diastolic"], case["symptoms"])[0] == case["urgency"], case["name"]
        assert set(clinical_rules(case["systolic"], case["diastolic"], case["symptoms"])[1]) == set(case["ruleTriggers"]), case["name"]
    engine = RulesOnlyInference()
    for _ in range(1000):
        engine.predict(systolic=160, diastolic=90, gestational_weeks=32)
    samples = []
    for _ in range(10000):
        start = time.perf_counter_ns()
        engine.predict(systolic=160, diastolic=90, gestational_weeks=32)
        samples.append((time.perf_counter_ns() - start) / 1_000_000)
    ordered = sorted(samples)
    benchmark = {"component": "Python reference rules-only inference contract",
                 "not_measured": ["Android cold-start", "Android clinical engine latency", "ONNX inference"],
                 "hardware": os.environ.get("PROCESSOR_IDENTIFIER", platform.processor()),
                 "logical_cpus": os.cpu_count(), "os": platform.platform(), "python": platform.python_version(),
                 "sample_size": len(samples), "warmup_iterations": 1000, "clock": "perf_counter_ns",
                 "input": "Synthetic systolic160 diastolic90 gestationalWeeks32 no symptoms",
                 "unit": "milliseconds", "p50": statistics.median(samples),
                 "p95": ordered[9499], "p99": ordered[9899], "max": max(samples),
                 "limitations": "Microbenchmark in this shared build environment; not a device or clinical benchmark."}
    (destination / "software-benchmark.json").write_text(json.dumps(benchmark, indent=2), encoding="utf-8")
    (destination / "summary.json").write_text(json.dumps({"checks": summary, "shared_rule_cases": len(cases),
                                                        "software_benchmark": benchmark}, indent=2), encoding="utf-8")
    print(json.dumps({"checks": summary, "shared_rule_cases": len(cases), "python_rules_p95_ms": benchmark["p95"]}, indent=2))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
