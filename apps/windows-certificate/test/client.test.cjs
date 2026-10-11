"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { CertificateClient } = require("../src/client.cjs");
test("A completed connection can be cleared without deleting the bank connection", async () => {
  const calls = [];
  const id = "00000000-0000-4000-8000-000000000001";
  let status = "CREATED";
  const client = new CertificateClient("http://127.0.0.1:18081", async (_url, options) => {
    calls.push(options.method);
    return new Response(JSON.stringify({ success: true, data: {
      id, status, pcCapability: "a".repeat(43), uploadCapability: "b".repeat(43),
    }}));
  }, true);
  await client.create("0020");
  status = "BOUND";
  assert.equal((await client.status()).status, "BOUND");
  await client.cancel();
  assert.deepEqual(calls, ["POST", "GET"]);
  await client.create("0020");
  assert.equal(calls.length, 3);
});
test("Cancelling a pending QR still calls the cancellation endpoint", async () => {
  const calls = [];
  const client = new CertificateClient("http://127.0.0.1:18081", async (_url, options) => {
    calls.push(options.method);
    return new Response(JSON.stringify({ success: true, data: {
      id: "00000000-0000-4000-8000-000000000001", status: "READY_FOR_CLAIM",
      pcCapability: "a".repeat(43), uploadCapability: "b".repeat(43),
    }}));
  }, true);
  await client.create("0020");
  await client.cancel();
  assert.deepEqual(calls, ["POST", "DELETE"]);
});
