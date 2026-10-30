// Real local doctor workflow. Secrets stay in ignored runtime configuration.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const require = createRequire(import.meta.url);
const { chromium } = require(path.join(root, 'apps/web/node_modules/playwright'));
const output = path.join(root, '.cache/video-demo');
const scenario = JSON.parse(fs.readFileSync(path.join(output, 'scenario.json'), 'utf8'));
if (!scenario.serverReceivedObserved) throw new Error('Record actual native server receipt first');
const users = JSON.parse(fs.readFileSync(path.join(root, 'services/api/.runtime/demo-credentials.json'), 'utf8')).users;
const doctor = users.find(u => u.role === 'DOCTOR' && u.facilityId === 'demo-facility');
const executablePath = 'C:/Users/HP/AppData/Local/ms-playwright/chromium-1194/chrome-win/chrome.exe';
const browser = await chromium.launch({ executablePath, headless: true, chromiumSandbox: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 900 },
  recordVideo: { dir: path.join(output, 'browser-video'), size: { width: 1440, height: 900 } } });
const page = await context.newPage();
const video = page.video();
const started = Date.now();
const events = [];
const mark = name => { events.push({ name, seconds: (Date.now() - started) / 1000 }); console.log(name); };
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
let step = 'sign-in';
try {
  await page.goto('http://localhost:3017/', { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: 'Welcome back.' }).waitFor();
  mark('Doctor sign-in screen');
  await pause(2500);
  await page.getByLabel('Username', { exact: true }).fill(doctor.username);
  await page.getByLabel('Password', { exact: true }).fill(doctor.password);
  await page.getByRole('button', { name: 'Sign in to workspace' }).click();
  await page.getByRole('heading', { name: 'Clinical overview', exact: true }).waitFor();
  mark('Authenticated facility overview');
  await pause(4000);
  await page.getByRole('button', { name: 'Referral inbox' }).click();
  await page.getByRole('heading', { name: 'Incoming referrals', exact: true }).first().waitFor();
  mark('Live referral inbox');
  await pause(3000);
  await page.getByRole('button', { name: /Urgent queue/ }).click();
  mark('Urgent referral queue');
  await pause(3000);
  step = 'matching-referral';
  await page.goto(`http://localhost:3017/referrals/${scenario.referralId}`, { waitUntil: 'networkidle' });
  await page.getByRole('heading', { name: scenario.patientId, exact: true }).waitFor();
  await page.getByRole('heading', { name: 'Awaiting acknowledgment', exact: true }).waitFor();
  mark('Same referral identifier and server-received state');
  await pause(4500);
  await page.getByText('Systolic blood pressure at or above 160 mmHg', { exact: true }).scrollIntoViewIfNeeded();
  await page.getByText('AI research status: unavailable', { exact: true }).waitFor();
  mark('Actual observations and deterministic rule explanation');
  await pause(4000);
  step = 'explicit-acknowledgment';
  await page.getByRole('button', { name: 'Acknowledge referral', exact: true }).scrollIntoViewIfNeeded();
  await page.getByRole('button', { name: 'Acknowledge referral', exact: true }).click();
  await page.getByRole('group', { name: 'Confirm clinical action' }).waitFor();
  mark('Explicit clinical action confirmation');
  await pause(3000);
  await page.getByRole('button', { name: 'Confirm acknowledgment', exact: true }).click();
  await page.getByRole('heading', { name: 'Acknowledgment recorded', exact: true }).waitFor();
  const response = await context.request.get(`http://localhost:3017/api/backend/referrals/${scenario.referralId}`);
  if (!response.ok()) throw new Error('Acknowledgment readback failed');
  const receipt = await response.json();
  if (receipt.id !== scenario.referralId || receipt.status !== 'ACKNOWLEDGED' ||
      receipt.acknowledgedBy !== doctor.username || !receipt.acknowledgedAt) {
    throw new Error('Unexpected real server acknowledgment evidence');
  }
  scenario.doctorAcknowledgmentObserved = true;
  scenario.doctorAcknowledgedAt = receipt.acknowledgedAt;
  scenario.doctorAcknowledgedBy = receipt.acknowledgedBy;
  fs.writeFileSync(path.join(output, 'scenario.json'), JSON.stringify(scenario, null, 2));
  mark('Server acknowledgment actor and timestamp verified');
  await pause(4500);
  await page.getByRole('heading', { name: 'Status history', exact: true }).scrollIntoViewIfNeeded();
  await pause(3500);
  await page.screenshot({ path: path.join(output, 'web-acknowledgment.png'), fullPage: true });
  await page.getByRole('heading', { name: scenario.patientId, exact: true }).scrollIntoViewIfNeeded();
  await pause(2000);
  await context.close();
  const recorded = await video.path();
  fs.copyFileSync(recorded, path.join(output, 'raw/07-doctor.webm'));
  fs.writeFileSync(path.join(output, 'doctor-recording.json'), JSON.stringify({
    source: 'Actual Playwright browser video; live local API and PostgreSQL; synthetic case',
    referralId: scenario.referralId, patientId: scenario.patientId, events,
    viewport: { width: 1440, height: 900 }, status: 'PASS'
  }, null, 2));
  console.log('Doctor recording and real acknowledgment: PASS');
} catch (error) {
  // Locator failure logs may contain fill arguments; never print them.
  console.error(`Doctor recording failed at ${step} (${error.name})`);
  await context.close().catch(() => {});
  process.exitCode = 1;
} finally {
  await browser.close();
}
