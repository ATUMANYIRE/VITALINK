import test from "node:test";
import assert from "node:assert/strict";
import { configuredSessionKey, secureCookie } from "../src/lib/session-policy.ts";
test("production server refuses an absent encryption secret", () => assert.throws(() => configuredSessionKey(undefined, "production"), /required/));
test("development may use ephemeral session keys", () => assert.equal(configuredSessionKey(undefined, "development"), undefined));
test("canonical 32-byte session secret is accepted", () => assert.equal(configuredSessionKey(Buffer.alloc(32, 7).toString("base64"), "production")?.length, 32));
test("short, malformed and noncanonical secrets are rejected", () => { for (const key of ["test", Buffer.alloc(31).toString("base64"), Buffer.alloc(32).toString("base64") + "!"]) assert.throws(() => configuredSessionKey(key, "production")); });
test("production cookies default to Secure", () => assert.equal(secureCookie("production", undefined, "https://clinical.example"), true));
test("explicit insecure cookies are restricted to localhost demonstrations", () => {
    assert.equal(secureCookie("production", "false", "http://localhost:3000"), false);
    assert.throws(() => secureCookie("production", "false", "https://clinical.example"));
});
test("invalid secure-cookie configuration is rejected", () => assert.throws(() => secureCookie("production", "sometimes", "https://clinical.example")));

test("insecure production cookie override requires an explicit localhost origin", () => assert.throws(() => secureCookie("production", "false", undefined), /APP_ORIGIN/));
