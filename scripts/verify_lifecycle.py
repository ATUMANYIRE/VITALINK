"""Black-box integration tests against the real API and PostgreSQL, never mocks."""

import concurrent.futures
import copy
import json
import os
import platform
import statistics
import sys
import time
import unittest
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = os.environ.get("VITALINK_API_URL", "http://127.0.0.1:8087/api/v1").rstrip("/")
EVIDENCE = ROOT / "evidence/integration"


def call(method, path, payload=None, token=None, headers=None):
    values = {"Accept": "application/json"}
    if payload is not None:
        values["Content-Type"] = "application/json"
    if token:
        values["Authorization"] = "Bearer " + token
    values.update(headers or {})
    request = urllib.request.Request(BASE + path, data=json.dumps(payload).encode() if payload is not None else None,
                                     headers=values, method=method)
    started = time.perf_counter_ns()
    try:
        response = urllib.request.urlopen(request, timeout=15)
    except urllib.error.HTTPError as exc:
        response = exc
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None, dict(response.headers), (time.perf_counter_ns() - started) / 1e6


def snapshot(case=None):
    result = {"id": str(uuid.uuid4()), "facilityId": "demo-facility", "assessment": {
        "id": str(uuid.uuid4()), "patientId": "SYN-INTEGRATION", "measuredAt": datetime.now(timezone.utc).isoformat(),
        "systolic": 160, "diastolic": 90, "gestationalWeeks": 32, "symptoms": [], "source": "MANUAL",
        "ruleVersion": "vitalink-rules-1.0.0"}}
    if case:
        for name in ("systolic", "diastolic", "symptoms"):
            result["assessment"][name] = case[name]
    return result


class LifecycleTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        path = Path(os.environ.get("VITALINK_CREDENTIALS", ROOT / "services/api/.runtime/demo-credentials.json"))
        cls.credentials = json.loads(path.read_text(encoding="utf-8-sig"))
        cls.tokens = {}
        for user in cls.credentials["users"]:
            status, body, _, _ = call("POST", "/auth/login", {"username": user["username"], "password": user["password"]})
            if status != 200:
                raise RuntimeError(f"Prerequisite login for role {user['role']} failed with HTTP {status}")
            cls.tokens[user["username"]] = body["token"]

    def create(self, case=None):
        payload = snapshot(case)
        status, result, headers, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"],
                                         {"Idempotency-Key": payload["id"]})
        self.assertEqual(201, status, result)
        self.assertEqual(payload["id"], result["id"])
        self.assertEqual("SERVER_RECEIVED", result["status"])
        self.assertIsNone(result.get("acknowledgedAt"))
        return payload, result

    def test_01_health_endpoint(self):
        status, result, _, _ = call("GET", "/health")
        self.assertEqual(200, status)
        self.assertNotIn("password", json.dumps(result).lower())

    def test_02_authentication_required(self):
        status, result, _, _ = call("GET", "/referrals")
        self.assertEqual(401, status)
        self.assertEqual(status, result["status"])

    def test_03_unknown_user_rejected(self):
        status, result, _, _ = call("POST", "/auth/login", {"username": "no-such-user", "password": "invalid-not-a-real-password"})
        self.assertEqual(401, status)
        self.assertNotIn("no-such-user", json.dumps(result))

    def test_04_delivery_is_not_acknowledgment(self):
        _, referral = self.create()
        self.assertIsNone(referral.get("acknowledgedBy"))

    def test_05_identical_retry_after_lost_response(self):
        payload, _ = self.create()
        status, replay, _, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"], {"Idempotency-Key": payload["id"]})
        self.assertEqual(200, status)
        self.assertEqual(payload["id"], replay["id"])
        self.assertEqual("SERVER_RECEIVED", replay["status"])

    def test_06_conflicting_retry_rejected(self):
        payload, _ = self.create()
        payload["assessment"]["systolic"] = 170
        status, _, _, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"], {"Idempotency-Key": payload["id"]})
        self.assertEqual(409, status)

    def test_07_wrong_idempotency_key(self):
        payload = snapshot()
        status, _, _, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"], {"Idempotency-Key": str(uuid.uuid4())})
        self.assertEqual(400, status)

    def test_08_health_worker_cannot_acknowledge(self):
        payload, _ = self.create()
        status, _, _, _ = call("POST", f"/referrals/{payload['id']}/acknowledge", token=self.tokens["worker"])
        self.assertEqual(403, status)

    def test_09_doctor_cannot_submit(self):
        payload = snapshot()
        status, _, _, _ = call("POST", "/sync/referrals", payload, self.tokens["doctor"], {"Idempotency-Key": payload["id"]})
        self.assertEqual(403, status)

    def test_10_facility_object_authorization(self):
        payload, _ = self.create()
        for user in ("outsider", "outsider-admin"):
            with self.subTest(user=user):
                for suffix in ("", "/history"):
                    status, _, _, _ = call("GET", f"/referrals/{payload['id']}{suffix}", token=self.tokens[user])
                    self.assertEqual(404, status)
                status, _, _, _ = call("POST", f"/referrals/{payload['id']}/acknowledge", token=self.tokens[user])
                self.assertEqual(404, status)

    def test_11_facility_submission_authorization(self):
        payload = snapshot()
        payload["facilityId"] = "other-facility"
        status, _, _, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"], {"Idempotency-Key": payload["id"]})
        self.assertIn(status, (403, 404))

    def test_12_explicit_clinician_acknowledgment_and_history(self):
        payload, _ = self.create()
        path = f"/referrals/{payload['id']}"
        status, acknowledged, _, _ = call("POST", path + "/acknowledge", token=self.tokens["doctor"])
        self.assertEqual(200, status)
        self.assertEqual("ACKNOWLEDGED", acknowledged["status"])
        self.assertEqual("doctor", acknowledged["acknowledgedBy"])
        self.assertIsNotNone(acknowledged["acknowledgedAt"])
        status, replay, _, _ = call("POST", path + "/acknowledge", token=self.tokens["doctor"])
        self.assertEqual(200, status)
        self.assertEqual(acknowledged["acknowledgedAt"], replay["acknowledgedAt"])
        status, history, _, _ = call("GET", path + "/history", token=self.tokens["worker"])
        self.assertEqual(200, status)
        self.assertEqual(["SERVER_RECEIVED", "ACKNOWLEDGED"], [entry["status"] for entry in history])
        status, mobile_poll, _, _ = call("GET", path, token=self.tokens["worker"])
        self.assertEqual("ACKNOWLEDGED", mobile_poll["status"])
        self.assertEqual(payload["id"], mobile_poll["id"])
        status, retried, _, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"], {"Idempotency-Key": payload["id"]})
        self.assertEqual(200, status)
        self.assertEqual("ACKNOWLEDGED", retried["status"])

    def test_13_invalid_transition(self):
        payload, _ = self.create()
        status, _, _, _ = call("POST", f"/referrals/{payload['id']}/close", token=self.tokens["doctor"])
        self.assertEqual(409, status)

    def test_14_close_after_acknowledgment(self):
        payload, _ = self.create()
        path = f"/referrals/{payload['id']}"
        self.assertEqual(200, call("POST", path + "/acknowledge", token=self.tokens["doctor"])[0])
        self.assertEqual("CLOSED", call("POST", path + "/close", token=self.tokens["doctor"])[1]["status"])
        self.assertEqual("CLOSED", call("POST", path + "/close", token=self.tokens["doctor"])[1]["status"])

    def test_15_concurrent_duplicate_submissions(self):
        payload = snapshot()
        with concurrent.futures.ThreadPoolExecutor(max_workers=6) as pool:
            results = list(pool.map(lambda _: call("POST", "/sync/referrals", payload, self.tokens["worker"],
                                                   {"Idempotency-Key": payload["id"]}), range(6)))
        self.assertEqual(1, sum(status == 201 for status, _, _, _ in results))
        self.assertEqual(5, sum(status == 200 for status, _, _, _ in results))
        status, history, _, _ = call("GET", f"/referrals/{payload['id']}/history", token=self.tokens["doctor"])
        self.assertEqual(1, len(history))

    def test_16_malformed_and_unknown_input(self):
        for field, value in (("ruleVersion", "unreviewed"), ("symptoms", ["UNKNOWN"]),
                             ("systolic", 301), ("patientId", "real-name-is-disallowed")):
            with self.subTest(field=field):
                payload = snapshot()
                payload["assessment"][field] = value
                status, error, _, _ = call("POST", "/sync/referrals", payload, self.tokens["worker"],
                                           {"Idempotency-Key": payload["id"]})
                self.assertEqual(400, status)
                self.assertNotIn("stackTrace", error)

    def test_17_no_sensitive_cache(self):
        _, referral = self.create()
        _, _, headers, _ = call("GET", f"/referrals/{referral['id']}", token=self.tokens["doctor"])
        self.assertIn("no-store", headers.get("Cache-Control", ""))

    def test_18_pagination_and_real_metrics(self):
        self.create()
        status, page, _, _ = call("GET", "/referrals?page=0&size=1", token=self.tokens["doctor"])
        self.assertEqual(200, status)
        self.assertEqual(1, len(page["items"]))
        self.assertGreaterEqual(page["totalElements"], 1)
        status, metrics, _, _ = call("GET", "/metrics", token=self.tokens["doctor"])
        self.assertEqual(200, status)
        self.assertEqual(page["totalElements"], metrics["total"])
        self.assertGreaterEqual(metrics["urgent"], 1)

    def test_19_filtering_scoped_queue(self):
        self.create()
        status, page, _, _ = call("GET", "/referrals?urgency=URGENT&status=SERVER_RECEIVED", token=self.tokens["doctor"])
        self.assertEqual(200, status)
        for row in page["items"]:
            self.assertEqual("demo-facility", row["facilityId"])
            self.assertEqual("URGENT", row["urgency"])
            self.assertEqual("SERVER_RECEIVED", row["status"])

    def test_20_logout_revokes_token(self):
        user = next(u for u in self.credentials["users"] if u["username"] == "doctor")
        status, body, _, _ = call("POST", "/auth/login", {"username": user["username"], "password": user["password"]})
        token = body["token"]
        self.assertIn(call("POST", "/auth/logout", token=token)[0], (200, 204))
        self.assertEqual(401, call("GET", "/referrals", token=token)[0])


def add_case(case):
    def test(self):
        _, referral = self.create(case)
        self.assertEqual(case["urgency"], referral["urgency"])
        self.assertEqual(set(case["ruleTriggers"]), set(referral["ruleTriggers"]))
    return test


for index, case in enumerate(json.loads((ROOT / "contracts/clinical-cases.json").read_text())):
    setattr(LifecycleTests, f"test_rule_{index:02}_{case['name'].replace('-', '_')}", add_case(case))


def main():
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    started = time.time()
    with (EVIDENCE / "lifecycle-tests.txt").open("w", encoding="utf-8") as report:
        result = unittest.TextTestRunner(stream=report, verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(LifecycleTests))
    summary = {"component": "Real HTTP API + PostgreSQL", "api": BASE, "tests": result.testsRun,
               "failures": len(result.failures), "errors": len(result.errors), "skipped": len(result.skipped),
               "duration_seconds": time.time() - started, "success": result.wasSuccessful(),
               "not_proven": "This harness is not Android process-death, Android network recovery or browser evidence."}
    (EVIDENCE / "lifecycle-summary.json").write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))
    return 0 if result.wasSuccessful() else 1


if __name__ == "__main__":
    raise SystemExit(main())
