import { createRequire } from "node:module";
const require = createRequire(import.meta.url);
const build = require("next/dist/build").default;
const { Bundler } = require("next/dist/lib/bundler");
try { await build(process.cwd(), false, false, true, false, false, false, Bundler.Webpack); }
catch (error) { console.error(error?.stack || error); process.exitCode = 1; }
