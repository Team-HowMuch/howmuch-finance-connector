"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { CertificateClient } = require("../src/client.cjs");
test("The shared public bank catalog exposes all authentication levels", async () => {
  let requestedUrl;
  const client = new CertificateClient(
    "http://127.0.0.1:18081",
    async (url) => {
      requestedUrl = String(url);
      return new Response(JSON.stringify({
        success: true,
        data: [
          { organization: "0001", name: "1단계", authenticationLevel: 1 },
          { organization: "0002", name: "2단계", authenticationLevel: 2 },
          { organization: "0020", name: "3단계", authenticationLevel: 3 },
        ],
      }));
    },
    true,
  );
  const banks = await client.banks();
  assert.equal(requestedUrl, "http://127.0.0.1:18081/api/v3/banks");
  assert.deepEqual(banks.map((bank) => bank.authenticationLevel), [1, 2, 3]);
  assert.equal(banks[2].requiresAccountCredentials, undefined);
});

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
