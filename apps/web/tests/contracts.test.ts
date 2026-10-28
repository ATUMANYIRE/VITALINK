import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import {
  parseReferral,
  parsePage,
  parseMetrics,
  parseHistory,
  parseUser,
  triggerLabel,
} from "../src/lib/contracts.ts";
function referral() {
  return {
    id: "611147a5-4073-491d-9227-3731f1f1a101",
    facilityId: "demo-facility",
    assessment: {
      id: "611147a5-4073-491d-9227-3731f1f1a102",
      patientId: "SYN-001",
      measuredAt: "2026-10-28T05:00:00Z",
      systolic: 160,
      diastolic: 90,
      gestationalWeeks: 32,
      symptoms: [],
      source: "MANUAL",
      ruleVersion: "vitalink-rules-1.0.0",
    },
    status: "SERVER_RECEIVED",
    urgency: "URGENT",
    ruleTriggers: ["SEVERE_SYSTOLIC", "ELEVATED_DIASTOLIC"],
    receivedAt: "2026-10-28T05:01:00Z",
    acknowledgedAt: null,
    acknowledgedBy: null,
  };
}
test("server receipt is accepted without inventing a clinical acknowledgment", () => {
  const result = parseReferral(referral());
  assert.equal(result.status, "SERVER_RECEIVED");
  assert.equal(result.acknowledgedAt, null);
});
test("acknowledgment requires explicit actor and timestamp", () => {
  assert.throws(() => parseReferral({ ...referral(), status: "ACKNOWLEDGED" }));
  const value = {
    ...referral(),
    status: "ACKNOWLEDGED",
    acknowledgedAt: "2026-10-28T05:02:00Z",
    acknowledgedBy: "doctor",
  };
  assert.equal(parseReferral(value).acknowledgedBy, "doctor");
});
test("server received must not carry contradictory acknowledgment metadata", () => {
  assert.throws(() =>
    parseReferral({ ...referral(), acknowledgedBy: "doctor" }),
  );
});
test("missing measurements remain null and are not rendered as zero", () => {
  const r = referral();
  const result = parseReferral({
    ...r,
    assessment: { ...r.assessment, systolic: null, diastolic: null },
    urgency: "REVIEW_REQUIRED",
    ruleTriggers: ["MISSING_SYSTOLIC", "MISSING_DIASTOLIC"],
  });
  assert.equal(result.assessment.systolic, null);
  assert.equal(result.assessment.diastolic, null);
});
test("malformed response shapes and delivery states fail closed", () => {
  for (const value of [
    null,
    [],
    {},
    { ...referral(), status: "SENT" },
    { ...referral(), urgency: "LOW_RISK" },
    { ...referral(), receivedAt: "yesterday" },
  ])
    assert.throws(() => parseReferral(value));
});
test("referral identifiers and synthetic patient identifiers are validated", () => {
  assert.throws(() => parseReferral({ ...referral(), id: "untrusted" }));
  assert.throws(() =>
    parseReferral({
      ...referral(),
      assessment: { ...referral().assessment, patientId: "Real patient name" },
    }),
  );
});
test("pagination validates every row and numeric counts", () => {
  assert.equal(
    parsePage({
      items: [referral()],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    }).items.length,
    1,
  );
  assert.throws(() =>
    parsePage({
      items: [null],
      page: 0,
      size: 10,
      totalElements: 1,
      totalPages: 1,
    }),
  );
  assert.throws(() =>
    parsePage({
      items: [],
      page: -1,
      size: 10,
      totalElements: 0,
      totalPages: 0,
    }),
  );
});
test("metrics cannot silently substitute fabricated or invalid counts", () => {
  assert.equal(
    parseMetrics({
      total: 3,
      urgent: 1,
      awaitingAcknowledgment: 2,
      acknowledged: 1,
      closed: 0,
    }).total,
    3,
  );
  assert.throws(() => parseMetrics({ total: 3 }));
  assert.throws(() =>
    parseMetrics({
      total: -1,
      urgent: 1,
      awaitingAcknowledgment: 2,
      acknowledged: 1,
      closed: 0,
    }),
  );
});
test("history rejects client-only states and malformed timestamps", () => {
  assert.equal(
    parseHistory([
      {
        status: "SERVER_RECEIVED",
        at: "2026-10-28T05:01:00Z",
        actor: "worker",
      },
    ]).length,
    1,
  );
  assert.throws(() =>
    parseHistory([
      { status: "DRAFT", at: "2026-10-28T05:01:00Z", actor: "worker" },
    ]),
  );
});
test("unknown roles cannot become an authenticated profile", () => {
  assert.throws(() =>
    parseUser({
      username: "test",
      role: "SUPERUSER",
      facilityId: "demo-facility",
    }),
  );
});
test("explanations identify actual severe clinical rule thresholds", () => {
  assert.match(triggerLabel("SEVERE_SYSTOLIC"), /160 mmHg/);
  assert.match(triggerLabel("SEVERE_DIASTOLIC"), /110 mmHg/);
});
test("malformed upstream low priority cannot suppress severe BP or symptoms", () => {
  assert.throws(() => parseReferral({ ...referral(), urgency: "NO_TRIGGER" }));
  assert.throws(() => parseReferral({ ...referral(), urgency: "REVIEW_REQUIRED" }));
  const r = referral();
  assert.throws(() => parseReferral({ ...r, urgency: "NO_TRIGGER", assessment: { ...r.assessment, systolic: 120, diastolic: 80, symptoms: ["VISUAL_DISTURBANCE"] } }));
});
test("missing measurements cannot be presented as no rule trigger", () => {
  const r = referral();
  assert.throws(() => parseReferral({ ...r, urgency: "NO_TRIGGER", assessment: { ...r.assessment, systolic: null, diastolic: 80 } }));
});
test("unsupported versions, unknown symptoms and implausible values fail closed", () => {
  for (const patch of [{ ruleVersion: "unknown" }, { symptoms: ["UNRECOGNIZED"] }, { systolic: 160.5 }, { systolic: 400 }]) {
    assert.throws(() => parseReferral({ ...referral(), assessment: { ...referral().assessment, ...patch } }));
  }
});

test("missing, invented and duplicate rule explanations fail closed", () => {
  for (const ruleTriggers of [["SEVERE_SYSTOLIC"], ["SEVERE_SYSTOLIC", "UNKNOWN"], ["SEVERE_SYSTOLIC", "SEVERE_SYSTOLIC"]]) {
    assert.throws(() => parseReferral({ ...referral(), ruleTriggers }));
  }
});

test("acknowledgment timestamp cannot precede receipt or omit its actor", () => {
  for (const values of [
    { acknowledgedAt: "2026-10-28T04:00:00Z", acknowledgedBy: "doctor" },
    { acknowledgedAt: "2026-10-28T05:02:00Z", acknowledgedBy: "" },
  ]) assert.throws(() => parseReferral({ ...referral(), status: "ACKNOWLEDGED", ...values }));
});

test("all shared cross-platform rule fixtures match the full response contract", () => {
  const cases = JSON.parse(fs.readFileSync(new URL("../../../contracts/clinical-cases.json", import.meta.url), "utf8")) as {
    name: string; systolic: number | null; diastolic: number | null; symptoms: string[]; urgency: string; ruleTriggers: string[];
  }[];
  for (const value of cases) {
    const base = referral();
    const actual = parseReferral({ ...base, urgency: value.urgency, ruleTriggers: value.ruleTriggers,
      assessment: { ...base.assessment, systolic: value.systolic, diastolic: value.diastolic, symptoms: value.symptoms } });
    assert.equal(actual.urgency, value.urgency, value.name);
    assert.deepEqual(new Set(actual.ruleTriggers), new Set(value.ruleTriggers), value.name);
  }
});

test("clinical BP boundary quadrant (159/109, 160/109, 159/110, 160/110) enforces OR semantics", () => {
  const base = referral();
  const c159_109 = parseReferral({
    ...base,
    urgency: "REVIEW_REQUIRED",
    ruleTriggers: ["ELEVATED_SYSTOLIC", "ELEVATED_DIASTOLIC"],
    assessment: { ...base.assessment, systolic: 159, diastolic: 109, symptoms: [] },
  });
  assert.equal(c159_109.urgency, "REVIEW_REQUIRED");

  const c160_109 = parseReferral({
    ...base,
    urgency: "URGENT",
    ruleTriggers: ["SEVERE_SYSTOLIC", "ELEVATED_DIASTOLIC"],
    assessment: { ...base.assessment, systolic: 160, diastolic: 109, symptoms: [] },
  });
  assert.equal(c160_109.urgency, "URGENT");

  const c159_110 = parseReferral({
    ...base,
    urgency: "URGENT",
    ruleTriggers: ["ELEVATED_SYSTOLIC", "SEVERE_DIASTOLIC"],
    assessment: { ...base.assessment, systolic: 159, diastolic: 110, symptoms: [] },
  });
  assert.equal(c159_110.urgency, "URGENT");

  const c160_110 = parseReferral({
    ...base,
    urgency: "URGENT",
    ruleTriggers: ["SEVERE_SYSTOLIC", "SEVERE_DIASTOLIC"],
    assessment: { ...base.assessment, systolic: 160, diastolic: 110, symptoms: [] },
  });
  assert.equal(c160_110.urgency, "URGENT");
});
