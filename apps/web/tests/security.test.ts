import test from "node:test";
import assert from "node:assert/strict";
import { allowedBackendPath, isSameOrigin } from "../src/lib/security.ts";
test("mutation origin must match configured application origin", () => {
  assert.equal(
    isSameOrigin(
      "http://web:3000/api/login",
      "https://clinical.example",
      "https://clinical.example",
    ),
    true,
  );
  assert.equal(
    isSameOrigin(
      "http://web:3000/api/login",
      "https://hostile.example",
      "https://clinical.example",
    ),
    false,
  );
});
test("missing, malformed and different-port origins are rejected", () => {
  for (const origin of [
    null,
    "null",
    "garbage",
    "http://localhost:3001",
    "https://localhost:3000",
  ])
    assert.equal(
      isSameOrigin("http://localhost:3000/api/login", origin),
      false,
    );
});
test("same-origin browser mutation is allowed", () =>
  assert.equal(
    isSameOrigin("http://localhost:3000/api/login", "http://localhost:3000"),
    true,
  ));
test("proxy cannot be used to submit referrals or invoke arbitrary server endpoints", () => {
  for (const p of [
    ["auth", "login"],
    ["sync", "referrals"],
    ["actuator", "env"],
    ["..", "metrics"],
    ["https:", "evil.example"],
  ])
    assert.equal(allowedBackendPath(p, "POST"), false);
});
test("proxy permits scoped reads and only explicit clinical mutations", () => {
  const id = "611147a5-4073-491d-9227-3731f1f1a101";
  assert.equal(allowedBackendPath(["referrals"], "GET"), true);
  assert.equal(allowedBackendPath(["referrals", id], "GET"), true);
  assert.equal(allowedBackendPath(["referrals", id, "history"], "GET"), true);
  assert.equal(
    allowedBackendPath(["referrals", id, "acknowledge"], "POST"),
    true,
  );
  assert.equal(allowedBackendPath(["referrals", id, "close"], "DELETE"), false);
  assert.equal(
    allowedBackendPath(["referrals", id, "acknowledge", "extra"], "POST"),
    false,
  );
});
