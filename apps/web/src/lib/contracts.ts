export const URGENCIES = ["URGENT", "REVIEW_REQUIRED", "NO_TRIGGER"] as const;
export type Urgency = (typeof URGENCIES)[number];
export type Status = "SERVER_RECEIVED" | "ACKNOWLEDGED" | "CLOSED";
export interface User {
  username: string;
  role: "HEALTH_WORKER" | "DOCTOR" | "ADMIN";
  facilityId: string;
}
export interface Assessment {
  id: string;
  patientId: string;
  measuredAt: string;
  systolic: number | null;
  diastolic: number | null;
  gestationalWeeks: number | null;
  symptoms: string[];
  source: "MANUAL" | "DEVICE";
  ruleVersion: string;
}
export interface Referral {
  id: string;
  facilityId: string;
  assessment: Assessment;
  status: Status;
  urgency: Urgency;
  ruleTriggers: string[];
  receivedAt: string;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
}
export interface ReferralPage {
  items: Referral[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
export interface Metrics {
  total: number;
  urgent: number;
  awaitingAcknowledgment: number;
  acknowledged: number;
  closed: number;
}
export interface HistoryEntry {
  status: Status;
  at: string;
  actor: string;
}
export class ContractError extends Error {
  constructor() {
    super(
      "The server returned an unexpected response. Refresh or contact the service administrator.",
    );
  }
}
const object = (v: unknown): v is Record<string, unknown> =>
  typeof v === "object" && v !== null && !Array.isArray(v);
const instant = (v: unknown): v is string =>
  typeof v === "string" && Number.isFinite(Date.parse(v));
const uuid = (v: unknown): v is string =>
  typeof v === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    v,
  );
const strings = (v: unknown): v is string[] =>
  Array.isArray(v) && v.every((x) => typeof x === "string");
const SYMPTOMS = ["SEVERE_HEADACHE", "VISUAL_DISTURBANCE", "UPPER_ABDOMINAL_PAIN", "BREATHLESSNESS", "SEIZURE", "HEAVY_BLEEDING", "REDUCED_FETAL_MOVEMENT"];
const nullableIntegerInRange = (v: unknown, min: number, max: number) => v === null || (typeof v === "number" && Number.isInteger(v) && v >= min && v <= max);
export function parseUser(v: unknown): User {
  if (
    !object(v) ||
    typeof v.username !== "string" ||
    !/^[A-Za-z0-9_.-]{1,64}$/.test(v.username) ||
    typeof v.facilityId !== "string" ||
    !/^[A-Za-z0-9-]{1,80}$/.test(v.facilityId) ||
    !["HEALTH_WORKER", "DOCTOR", "ADMIN"].includes(String(v.role))
  )
    throw new ContractError();
  return v as unknown as User;
}
export function parseReferral(v: unknown): Referral {
  if (!object(v) || !object(v.assessment)) throw new ContractError();
  const a = v.assessment;
  if (
    !uuid(v.id) ||
    typeof v.facilityId !== "string" ||
    !/^[A-Za-z0-9-]{1,80}$/.test(v.facilityId) ||
    !["SERVER_RECEIVED", "ACKNOWLEDGED", "CLOSED"].includes(String(v.status)) ||
    !URGENCIES.includes(v.urgency as Urgency) ||
    !strings(v.ruleTriggers) ||
    !instant(v.receivedAt) ||
    !uuid(a.id) ||
    typeof a.patientId !== "string" ||
    !/^SYN-[A-Za-z0-9-]{1,40}$/.test(a.patientId) ||
    !instant(a.measuredAt) ||
    !nullableIntegerInRange(a.systolic, 50, 300) ||
    !nullableIntegerInRange(a.diastolic, 30, 200) ||
    !nullableIntegerInRange(a.gestationalWeeks, 0, 45) ||
    !strings(a.symptoms) ||
    !a.symptoms.every(s => SYMPTOMS.includes(s)) ||
    new Set(a.symptoms).size !== a.symptoms.length ||
    !["MANUAL", "DEVICE"].includes(String(a.source)) ||
    a.ruleVersion !== "vitalink-rules-1.0.0" ||
    !(v.acknowledgedAt === null || instant(v.acknowledgedAt)) ||
    !(v.acknowledgedBy === null || typeof v.acknowledgedBy === "string")
  )
    throw new ContractError();
  if (typeof a.systolic === "number" && typeof a.diastolic === "number" && a.systolic <= a.diastolic) throw new ContractError();
  // Verify the complete upstream safety contract. All observed flags remain
  // visible even when another flag makes the referral urgent.
  const expectedTriggers: string[] = [];
  for (const [label, value, elevated, severe] of [
    ["SYSTOLIC", a.systolic, 140, 160],
    ["DIASTOLIC", a.diastolic, 90, 110],
  ] as const) {
    if (value === null) expectedTriggers.push(`MISSING_${label}`);
    else if (typeof value === "number" && value >= severe) expectedTriggers.push(`SEVERE_${label}`);
    else if (typeof value === "number" && value >= elevated) expectedTriggers.push(`ELEVATED_${label}`);
  }
  expectedTriggers.push(...(a.symptoms as string[]).map(s => `SYMPTOM_${s}`));
  const expectedUrgency = expectedTriggers.some(t => t.startsWith("SEVERE_") || t.startsWith("SYMPTOM_"))
    ? "URGENT" : expectedTriggers.length ? "REVIEW_REQUIRED" : "NO_TRIGGER";
  if (v.urgency !== expectedUrgency || v.ruleTriggers.length !== expectedTriggers.length
    || new Set(v.ruleTriggers).size !== v.ruleTriggers.length
    || !expectedTriggers.every(t => (v.ruleTriggers as string[]).includes(t))) throw new ContractError();
  if (
    v.status !== "SERVER_RECEIVED" &&
    (v.acknowledgedAt === null || typeof v.acknowledgedBy !== "string" || !v.acknowledgedBy.trim()
      || Date.parse(v.acknowledgedAt as string) < Date.parse(v.receivedAt as string))
  )
    throw new ContractError();
  if (
    v.status === "SERVER_RECEIVED" &&
    (v.acknowledgedAt !== null || v.acknowledgedBy !== null)
  )
    throw new ContractError();
  return v as unknown as Referral;
}
export function parsePage(v: unknown): ReferralPage {
  if (
    !object(v) ||
    !Array.isArray(v.items) ||
    ![v.page, v.size, v.totalElements, v.totalPages].every(
      (n) => Number.isInteger(n) && Number(n) >= 0,
    ) ||
    Number(v.size) < 1
  )
    throw new ContractError();
  return { ...v, items: v.items.map(parseReferral) } as unknown as ReferralPage;
}
export function parseMetrics(v: unknown): Metrics {
  if (
    !object(v) ||
    ![
      "total",
      "urgent",
      "awaitingAcknowledgment",
      "acknowledged",
      "closed",
    ].every((k) => Number.isInteger(v[k]) && Number(v[k]) >= 0)
  )
    throw new ContractError();
  return v as unknown as Metrics;
}
export function parseHistory(v: unknown): HistoryEntry[] {
  if (
    !Array.isArray(v) ||
    !v.every(
      (x) =>
        object(x) &&
        ["SERVER_RECEIVED", "ACKNOWLEDGED", "CLOSED"].includes(
          String(x.status),
        ) &&
        instant(x.at) &&
        typeof x.actor === "string",
    )
  )
    throw new ContractError();
  return v as HistoryEntry[];
}
export const urgencyLabel = (u: Urgency) =>
  ({
    URGENT: "Urgent assessment",
    REVIEW_REQUIRED: "Clinical review",
    NO_TRIGGER: "No rule trigger",
  })[u];
export const statusLabel = (s: Status) =>
  ({
    SERVER_RECEIVED: "Received",
    ACKNOWLEDGED: "Acknowledged",
    CLOSED: "Closed",
  })[s];
export function triggerLabel(code: string): string {
  const labels: Record<string, string> = {
    SEVERE_SYSTOLIC: "Systolic blood pressure at or above 160 mmHg",
    SEVERE_DIASTOLIC: "Diastolic blood pressure at or above 110 mmHg",
    ELEVATED_SYSTOLIC: "Systolic blood pressure at or above 140 mmHg",
    ELEVATED_DIASTOLIC: "Diastolic blood pressure at or above 90 mmHg",
    MISSING_SYSTOLIC: "Systolic blood pressure is missing",
    MISSING_DIASTOLIC: "Diastolic blood pressure is missing",
  };
  if (labels[code]) return labels[code];
  return code
    .replace(/^SYMPTOM_/, "Concerning symptom: ")
    .replaceAll("_", " ")
    .toLowerCase();
}
