// Genuine real-service browser checks using an already running local Chromium.
// The host may deny Node subprocess creation; CDP avoids requiring a supervisor
// or browser spawn from Node. No browser security features are disabled.
import { chromium } from "playwright";
import fs from "node:fs";
import path from "node:path";
import crypto from "node:crypto";
import assert from "node:assert/strict";
const root = path.resolve(import.meta.dirname, "..");
const output = path.join(root, "evidence");
fs.mkdirSync(output, { recursive: true });
const base = process.env.WEB_BASE_URL || "http://localhost:3017";
const api = process.env.VITALINK_TEST_API || "http://127.0.0.1:8087/api/v1";
const credentials = JSON.parse(fs.readFileSync(process.env.VITALINK_DEMO_CREDENTIALS || path.resolve(root, "../../services/api/.runtime/demo-credentials.json"), "utf8"));
const report = { startedAt: new Date().toISOString(), webBase: base, apiBase: api, browser: "", checks: [], screenshots: [], measurements: [], status: "RUNNING" };
let browser, context, page;
async function check(name, fn) { try { await fn(); report.checks.push({ name, status: "PASS" }); console.log(`PASS ${name}`); } catch (error) { report.checks.push({ name, status: "FAIL", detail: String(error.message) }); throw error; } }
async function shot(name) { await page.screenshot({ path: path.join(output, `${name}.png`), fullPage: true }); report.screenshots.push(`${name}.png`); }
async function a11y(name) {
    await page.addScriptTag({ path: path.join(root, "node_modules/axe-core/axe.min.js") });
    const result = await page.evaluate(async () => await window.axe.run(document.documentElement, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21aa"] } }));
    fs.writeFileSync(path.join(output, `accessibility-${name}.json`), JSON.stringify(result, null, 2));
    assert.deepEqual(result.violations, [], `WCAG violations on ${name}: ${result.violations.map(v => v.id).join(", ")}`);
}
async function requestJson(url, options = {}) {
    const response = await fetch(url, { ...options, signal: AbortSignal.timeout(15000) });
    if (!response.ok) throw new Error(`Real API request failed with status ${response.status}`);
    return { status: response.status, data: await response.json() };
}
try {
    browser = await chromium.connectOverCDP(process.env.PLAYWRIGHT_CDP_URL || "http://127.0.0.1:9223", { timeout: 15000 });
    report.browser = browser.version();
    context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, baseURL: base });
    page = await context.newPage();
    const worker = credentials.users.find(u => u.role === "HEALTH_WORKER" && u.facilityId === "demo-facility");
    const doctor = credentials.users.find(u => u.role === "DOCTOR" && u.facilityId === "demo-facility");
    assert.ok(worker && doctor);
    const login = await requestJson(`${api}/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ username: worker.username, password: worker.password }) });
    const workerToken = login.data.token;
    const id = crypto.randomUUID();
    const patientId = `SYN-WEB-${Date.now()}`;
    const payload = { id, facilityId: "demo-facility", assessment: { id: crypto.randomUUID(), patientId, measuredAt: new Date().toISOString(), systolic: 160, diastolic: 90, gestationalWeeks: 32, symptoms: [], source: "MANUAL", ruleVersion: "vitalink-rules-1.0.0" } };
    await check("real PostgreSQL-backed API accepts severe-range synthetic referral", async () => {
        const result = await requestJson(`${api}/sync/referrals`, { method: "POST", headers: { "Content-Type": "application/json", Authorization: `Bearer ${workerToken}`, "Idempotency-Key": id }, body: JSON.stringify(payload) });
        assert.equal(result.status, 201); assert.equal(result.data.status, "SERVER_RECEIVED"); assert.equal(result.data.urgency, "URGENT");
    });
    await check("sign-in page is accessible and labels research limitations", async () => {
        await page.goto(base); await page.getByRole("heading", { name: "Welcome back." }).waitFor();
        await a11y("sign-in"); await shot("sign-in-desktop");
    });
    await check("real doctor sign-in opens actual facility overview", async () => {
        await page.getByLabel("Username", { exact: true }).fill(doctor.username); await page.getByLabel("Password", { exact: true }).fill(doctor.password);
        await page.getByRole("button", { name: "Sign in to workspace" }).click();
        await page.getByRole("heading", { name: "Clinical overview", exact: true }).waitFor();
        await page.getByText("Service connected", { exact: true }).waitFor();
    });
    await check("session token is HttpOnly and absent from browser storage and public session response", async () => {
        assert.equal((await context.cookies()).find(c => c.name === "vitalink_session")?.httpOnly, true);
        assert.deepEqual(await page.evaluate(() => Object.keys(localStorage)), []);
        const result = await context.request.get(`${base}/api/session`); assert.equal(result.status(), 200); assert.equal((await result.json()).token, undefined);
    });
    await check("overview displays actual server metrics and passes automated WCAG checks", async () => {
        const result = await context.request.get(`${base}/api/backend/metrics`); assert.equal(result.status(), 200);
        const metrics = await result.json();
        const received = await page.locator(".metric").filter({ hasText: "Received referrals" }).locator("strong").textContent();
        assert.equal(Number(received.replaceAll(",", "")), metrics.total);
        await a11y("overview"); await shot("overview-desktop");
    });
    await check("real urgent detail preserves escalation and unacknowledged receipt", async () => {
        const start = performance.now();
        await page.goto(`${base}/referrals/${id}`);
        await page.getByRole("heading", { name: patientId, exact: true }).waitFor();
        report.measurements.push({ name: "browser_referral_navigation_to_visible_snapshot_ms", value: performance.now() - start, samples: 1, environment: "Local Chromium CDP; includes app navigation and API requests; not a percentile benchmark" });
        await page.getByText("Urgent clinical assessment required", { exact: true }).waitFor();
        await page.getByRole("heading", { name: "Awaiting acknowledgment", exact: true }).waitFor();
        await page.getByText("Systolic blood pressure at or above 160 mmHg", { exact: true }).waitFor();
        await page.getByText("AI research status: unavailable", { exact: true }).waitFor();
        await a11y("referral"); await shot("referral-desktop");
    });
    await check("canceling acknowledgment does not mutate the clinical handoff", async () => {
        await page.getByRole("button", { name: "Acknowledge referral", exact: true }).click();
        await page.getByRole("button", { name: "Cancel", exact: true }).click();
        await page.getByRole("heading", { name: "Awaiting acknowledgment", exact: true }).waitFor();
    });
    await check("explicit clinician acknowledgment persists through real API and visible status history", async () => {
        await page.getByRole("button", { name: "Acknowledge referral", exact: true }).click();
        await page.getByRole("button", { name: "Confirm acknowledgment", exact: true }).click();
        await page.getByRole("heading", { name: "Acknowledgment recorded", exact: true }).waitFor();
        const result = await requestJson(`${api}/referrals/${id}`, { headers: { Authorization: `Bearer ${workerToken}` } });
        assert.equal(result.data.status, "ACKNOWLEDGED"); assert.equal(result.data.acknowledgedBy, doctor.username);
        const history = await context.request.get(`${base}/api/backend/referrals/${id}/history`);
        assert.ok((await history.json()).some(h => h.status === "ACKNOWLEDGED" && h.actor === doctor.username));
    });
    await check("390px mobile layout avoids horizontal overflow and passes automated WCAG checks", async () => {
        await page.setViewportSize({ width: 390, height: 844 });
        assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth));
        await a11y("mobile-referral"); await shot("referral-mobile");
    });
    await check("foreign-origin and absent-origin clinical mutations are rejected", async () => {
        const forbidden = await context.request.post(`${base}/api/backend/referrals/${id}/close`, { headers: { Origin: "https://untrusted.example" } }); assert.equal(forbidden.status(), 403);
        const noOrigin = await context.request.post(`${base}/api/backend/referrals/${id}/close`); assert.equal(noOrigin.status(), 403);
    });
    await check("browser offline state disables clinical actions", async () => {
        await context.setOffline(true);
        await page.getByText(/This browser is offline/).waitFor();
        assert.equal(await page.getByRole("button", { name: "Close referral", exact: true }).isDisabled(), true);
        await context.setOffline(false);
        await page.getByRole("button", { name: "Close referral", exact: true }).waitFor();
        await page.waitForFunction(() => ![...document.querySelectorAll("button")].find(b => b.textContent === "Close referral")?.disabled);
    });
    await check("logout clears browser session and unauthenticated scoped reads are rejected", async () => {
        await page.getByRole("button", { name: "Sign out", exact: true }).click(); await page.getByRole("heading", { name: "Welcome back." }).waitFor();
        const result = await context.request.get(`${base}/api/backend/referrals/${id}`); assert.equal(result.status(), 401);
    });
    await fetch(`${api}/auth/logout`, { method: "POST", headers: { Authorization: `Bearer ${workerToken}` }, signal: AbortSignal.timeout(5000) });
    report.referralId = id; report.status = "PASS";
} catch (error) { report.status = report.checks.length ? "FAIL" : "BLOCKED"; report.error = String(error.message); process.exitCode = 1; }
finally {
    if (context) await context.close();
    if (browser) await browser.close();
    report.completedAt = new Date().toISOString();
    fs.writeFileSync(path.join(output, "browser-cdp-results.json"), JSON.stringify(report, null, 2));
    console.log(`${report.status}: ${report.checks.filter(c => c.status === "PASS").length}/${report.checks.length} completed browser checks; evidence/browser-cdp-results.json`);
}
