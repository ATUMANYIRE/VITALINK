import { defineConfig } from "@playwright/test";
import fs from "node:fs";
const cachedChromium =
  "C:/Users/HP/AppData/Local/ms-playwright/chromium-1194/chrome-win/chrome.exe";
export default defineConfig({
  testDir: "./tests/e2e",
  timeout: 45_000,
  expect: { timeout: 12_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [
    ["list"],
    ["json", { outputFile: "evidence/playwright-results.json" }],
  ],
  use: {
    baseURL: process.env.WEB_BASE_URL || "http://localhost:3017",
    screenshot: "only-on-failure",
    trace: "retain-on-failure",
    launchOptions: {
      executablePath:
        process.env.PLAYWRIGHT_EXECUTABLE_PATH ||
        (fs.existsSync(cachedChromium) ? cachedChromium : undefined),
    },
  },
  projects: [
    { name: "desktop", use: { viewport: { width: 1440, height: 1000 } } },
  ],
});
