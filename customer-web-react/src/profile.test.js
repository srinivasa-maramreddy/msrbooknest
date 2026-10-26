import test, { beforeEach } from "node:test";
import assert from "node:assert/strict";
import { demoApi } from "./demo-api.js";
import { saveSession } from "./auth.js";

beforeEach(() => {
  const values = new Map();
  globalThis.localStorage = { getItem: (key) => values.get(key) ?? null, setItem: (key, value) => values.set(key, String(value)), removeItem: (key) => values.delete(key) };
});

async function signIn(email = "profile-reader@example.test") {
  const session = await demoApi.registerDemo({ name: "Profile Reader", email, password: "TestProfile@123" });
  saveSession(session);
  return session;
}

test("profile saves name, normalized mobile and address without changing verified email", async () => {
  await signIn();
  const saved = await demoApi.updateProfile("unused-demo-token", { name: " Reader Name ", phoneNumber: "+91 98765 43210", address: " Demo street, Hyderabad, 500001 ", email: "someone-else@example.test" });
  assert.equal(saved.name, "Reader Name");
  assert.equal(saved.phoneNumber, "9876543210");
  assert.equal(saved.address, "Demo street, Hyderabad, 500001");
  assert.equal(saved.email, "profile-reader@example.test");
  assert.equal("password" in saved, false);
  assert.deepEqual(await demoApi.getProfile(), saved);
});

test("a duplicate mobile cannot be assigned to a different account", async () => {
  await signIn("first@example.test");
  await demoApi.updateProfile("", { name: "First Reader", phoneNumber: "9876543210", address: "" });
  await signIn("second@example.test");
  await assert.rejects(demoApi.updateProfile("", { name: "Second Reader", phoneNumber: "9876543210", address: "" }), /already linked/);
  assert.equal((await demoApi.getProfile()).phoneNumber, "");
});

test("invalid mobile and signed-out profile access are rejected", async () => {
  await assert.rejects(demoApi.getProfile(), /Sign in/);
  await signIn();
  await assert.rejects(demoApi.updateProfile("", { name: "Reader", phoneNumber: "12345", address: "" }), /valid 10-digit/);
});
