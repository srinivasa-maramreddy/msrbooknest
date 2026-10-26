import test, { afterEach } from "node:test";
import assert from "node:assert/strict";
import { fetchTextWithDeadline, RequestTimeoutError } from "./http.js";

const originalFetch = globalThis.fetch;
afterEach(() => { globalThis.fetch = originalFetch; });

test("normal response returns its status and complete body", async () => {
  const response = new Response('{"status":"READY"}', { status: 200 });
  globalThis.fetch = async () => response;
  const result = await fetchTextWithDeadline("https://example.test/status", {}, 200);
  assert.equal(result.response.status, 200);
  assert.equal(result.raw, '{"status":"READY"}');
});

test("stalled connection reaches a deadline and aborts its request", async () => {
  let requestSignal;
  globalThis.fetch = (_url, options) => { requestSignal = options.signal; return new Promise(() => {}); };
  await assert.rejects(fetchTextWithDeadline("https://example.test/order", {}, 15), RequestTimeoutError);
  assert.equal(requestSignal.aborted, true);
});

test("response headers without a completed body also reach a deadline", async () => {
  let requestSignal;
  globalThis.fetch = async (_url, options) => {
    requestSignal = options.signal;
    return { ok: true, status: 200, text: () => new Promise(() => {}) };
  };
  await assert.rejects(fetchTextWithDeadline("https://example.test/payment", {}, 15), RequestTimeoutError);
  assert.equal(requestSignal.aborted, true);
});

test("a timed-out payment POST is sent once and is never automatically retried", async () => {
  let attempts = 0;
  let sent;
  globalThis.fetch = (_url, options) => { attempts += 1; sent = options; return new Promise(() => {}); };
  await assert.rejects(fetchTextWithDeadline("https://example.test/pay", {
    method: "POST", body: '{"orderRequestId":"same-order"}', headers: { "Idempotency-Key": "same-attempt" },
  }, 15), RequestTimeoutError);
  assert.equal(attempts, 1);
  assert.equal(sent.method, "POST");
  assert.equal(sent.headers["Idempotency-Key"], "same-attempt");
});

test("caller cancellation aborts an in-flight request even when fetch never settles", async () => {
  const caller = new AbortController();
  let requestSignal;
  globalThis.fetch = (_url, options) => { requestSignal = options.signal; return new Promise(() => {}); };
  const pending = fetchTextWithDeadline("https://example.test/order", { signal: caller.signal }, 200);
  caller.abort();
  await assert.rejects(pending, { name: "AbortError" });
  assert.equal(requestSignal.aborted, true);
});

test("an already-cancelled request is not sent", async () => {
  const caller = new AbortController();
  caller.abort();
  let attempts = 0;
  globalThis.fetch = async () => { attempts += 1; return new Response("unused"); };
  await assert.rejects(fetchTextWithDeadline("https://example.test/order", { signal: caller.signal }, 200), { name: "AbortError" });
  assert.equal(attempts, 0);
});

test("HTTP failures remain available to the caller for safe status-specific handling", async () => {
  globalThis.fetch = async () => new Response('{"error":"Payment was not created"}', { status: 404 });
  const result = await fetchTextWithDeadline("https://example.test/payment", {}, 200);
  assert.equal(result.response.status, 404);
  assert.equal(result.raw, '{"error":"Payment was not created"}');
});
