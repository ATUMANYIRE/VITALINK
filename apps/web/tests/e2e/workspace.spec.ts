import { test, expect, type BrowserContext, type Page } from "@playwright/test";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";

type Credentials = { users: { username: string; password: string; role: string; facilityId: string }[] };
const credentialFile = process.env.VITALINK_DEMO_CREDENTIALS || path.resolve(import.meta.dirname, "../../../../services/api/.runtime/demo-credentials.json");
const api = process.env.VITALINK_TEST_API || "http://127.0.0.1:8087/api/v1";
test.describe.configure({ mode: "serial" });
let context: BrowserContext;
let page: Page;
let referralId: string;
let patientId: string;
let credentials: Credentials;

async function auditAccessibility(target: Page, name: string) {
    await target.addScriptTag({ path: path.resolve("node_modules/axe-core/axe.min.js") });
    const result = await target.evaluate(async () => {
        const axe = (window as unknown as { axe: { run: (context: Element, options: unknown) => Promise<{ violations: unknown[] }> } }).axe;
        return await axe.run(document.documentElement, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21aa"] } });
    });
    fs.mkdirSync("evidence", { recursive: true });
    fs.writeFileSync(`evidence/accessibility-${name}.json`, JSON.stringify(result, null, 2));
    expect(result.violations).toEqual([]);
}

test.beforeAll(async ({ browser, request }) => {
    if (!fs.existsSync(credentialFile)) throw new Error("Generate local synthetic demo credentials before running real-service browser E2E.");
    credentials = JSON.parse(fs.readFileSync(credentialFile, "utf8")) as Credentials;
    const worker = credentials.users.find(u => u.role === "HEALTH_WORKER" && u.facilityId === "demo-facility");
    const doctor = credentials.users.find(u => u.role === "DOCTOR" && u.facilityId === "demo-facility");
    if (!worker || !doctor) throw new Error("A health worker and doctor fixture are required.");
    const login = await request.post(`${api}/auth/login`, { data: { username: worker.username, password: worker.password } });
    expect(login.ok()).toBeTruthy();
    const { token } = await login.json() as { token: string };
    referralId = crypto.randomUUID(); patientId = `SYN-WEB-${Date.now()}`;
    const seeded = await request.post(`${api}/sync/referrals`, { headers: { Authorization: `Bearer ${token}`, "Idempotency-Key": referralId }, data: {
        id: referralId, facilityId: "demo-facility", assessment: { id: crypto.randomUUID(), patientId, measuredAt: new Date().toISOString(), systolic: 160, diastolic: 90, gestationalWeeks: 32, symptoms: [], source: "MANUAL", ruleVersion: "vitalink-rules-1.0.0" }
    } });
    expect(seeded.status()).toBe(201);
    await request.post(`${api}/auth/logout`, { headers: { Authorization: `Bearer ${token}` } });
    context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, baseURL: process.env.WEB_BASE_URL || "http://localhost:3017" });
    page = await context.newPage();
    await page.goto("/");
    await expect(page.getByRole("heading", { name: "Welcome back." })).toBeVisible();
    await page.screenshot({ path: "evidence/sign-in-desktop.png", fullPage: true });
    await auditAccessibility(page, "sign-in");
    await page.getByLabel("Username", { exact: true }).fill(doctor.username);
    await page.getByLabel("Password", { exact: true }).fill(doctor.password);
    await page.getByRole("button", { name: "Sign in to workspace" }).click();
    await expect(page.getByRole("heading", { name: "Clinical overview", exact: true })).toBeVisible();
});
test.afterAll(async () => { await context?.close(); });

test("authenticated overview uses real records and protects session credentials", async () => {
    await expect(page.getByRole("region", { name: "Facility operational metrics" })).toBeVisible();
    await expect(page.getByText("Service connected", { exact: true })).toBeVisible();
    const cookies = await context.cookies();
    expect(cookies.find(c => c.name === "vitalink_session")?.httpOnly).toBe(true);
    const session = await context.request.get("/api/session");
    expect((await session.json()).token).toBeUndefined();
    expect(await page.evaluate(() => Object.keys(localStorage))).toEqual([]);
    await page.screenshot({ path: "evidence/overview-desktop.png", fullPage: true });
    await auditAccessibility(page, "overview");
});

test("urgent referral detail preserves clinical escalation and server receipt distinction", async () => {
    await page.goto(`/referrals/${referralId}`);
    await expect(page.getByRole("heading", { name: patientId, exact: true })).toBeVisible();
    await expect(page.getByText("Urgent clinical assessment required", { exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Awaiting acknowledgment", exact: true })).toBeVisible();
    await expect(page.getByText("Systolic blood pressure at or above 160 mmHg", { exact: true })).toBeVisible();
    await expect(page.getByText("AI research status: unavailable", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Acknowledge referral", exact: true })).toBeEnabled();
    await page.screenshot({ path: "evidence/referral-desktop.png", fullPage: true });
    await auditAccessibility(page, "referral");
});

test("acknowledgment requires confirmation and updates only after the real server confirms", async () => {
    await page.getByRole("button", { name: "Acknowledge referral", exact: true }).click();
    await expect(page.getByRole("group", { name: "Confirm clinical action" })).toBeVisible();
    await page.getByRole("button", { name: "Cancel", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Awaiting acknowledgment", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "Acknowledge referral", exact: true }).click();
    await page.getByRole("button", { name: "Confirm acknowledgment", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Acknowledgment recorded", exact: true })).toBeVisible();
    await expect(page.getByText("Clinical acknowledgment recorded by the server.", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Close referral", exact: true })).toBeEnabled();
});

test("mobile layout and keyboard navigation remain accessible", async () => {
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.getByRole("heading", { name: patientId, exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.screenshot({ path: "evidence/referral-mobile.png", fullPage: true });
    await auditAccessibility(page, "mobile-referral");
    await page.keyboard.press("Control+Home");
    await page.keyboard.press("Tab");
    await expect(page.getByRole("link", { name: "Skip to main content" })).toBeFocused();
    await page.setViewportSize({ width: 1440, height: 1000 });
});

test("cross-origin mutations and unauthenticated proxy reads are rejected", async ({ request }) => {
    const forbidden = await context.request.post(`/api/backend/referrals/${referralId}/close`, { headers: { Origin: "https://untrusted.example" } });
    expect(forbidden.status()).toBe(403);
    const noOrigin = await context.request.post(`/api/backend/referrals/${referralId}/close`);
    expect(noOrigin.status()).toBe(403);
    const unauthenticated = await request.get(`/api/backend/referrals/${referralId}`);
    expect(unauthenticated.status()).toBe(401);
    const arbitrary = await context.request.get("/api/backend/actuator/env");
    expect(arbitrary.status()).toBe(404);
});

test("offline actions are disabled and reconnection refreshes stored state", async () => {
    await context.setOffline(true);
    await expect(page.getByText(/This browser is offline/)).toBeVisible();
    await expect(page.getByRole("button", { name: "Close referral", exact: true })).toBeDisabled();
    await context.setOffline(false);
    await expect(page.getByRole("button", { name: "Close referral", exact: true })).toBeEnabled();
    await page.getByRole("button", { name: "Sign out", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Welcome back." })).toBeVisible();
});
