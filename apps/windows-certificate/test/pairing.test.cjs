"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { CertificateClient } = require("../src/client.cjs");
const id = "00000000-0000-4000-8000-000000000001",
  session = "00000000-0000-4000-8000-000000000002";
const expiry = () =>
  new Date(Date.now() + 600000).toISOString().replace("Z", "");
function fixture() {
  let status = "WAITING_FOR_MOBILE",
    calls = [],
    failCancel = false,
    organization = "0020";
  const client = new CertificateClient(
    "http://127.0.0.1:18081",
    async (url, options) => {
      calls.push({ url, options });
      if (failCancel && options.method === "DELETE") throw new Error("network");
      const data = url.endsWith("/certificate-pairings")
        ? {
            id,
            status: "WAITING_FOR_MOBILE",
            pcCapability: "p".repeat(43),
            qrToken: "q".repeat(43),
            expiresAt: expiry(),
            qrExpiresAt: expiry(),
          }
        : url.endsWith("/registrations")
          ? {
              id: session,
              status: "CREATED",
              pcCapability: "s".repeat(43),
              uploadCapability: "u".repeat(43),
              expiresAt: expiry(),
            }
          : url.endsWith("/pc")
            ? { id, status, organization: status === "WAITING_FOR_MOBILE" ? null : organization, expiresAt: expiry() }
            : { id: session, status, expiresAt: expiry() };
      return new Response(JSON.stringify({ success: true, data }));
    },
    true,
  );
  return {
    client,
    calls,
    setStatus: (s) => (status = s),
    setOrganization: (o) => (organization = o),
    failCancel: () => (failCancel = true),
  };
}
test("initial QR prevents registration until mobile pairing and keeps capabilities out of renderer results", async () => {
  const f = fixture();
  const qr = await f.client.startPairing();
  assert.equal(qr.pairingId, id);
  assert.equal(qr.pcCapability, undefined);
  await assert.rejects(f.client.create("0020"), /CERTIFICATE_CONFLICT/);
  assert.equal(f.calls.length, 1);
  f.setStatus("PAIRED");
  const status = await f.client.pairingStatus();
  assert.deepEqual(Object.keys(status).sort(), ["expiresAt", "organization", "status"]);
  assert.equal(status.organization, "0020");
  await assert.rejects(f.client.create("0004"), /CERTIFICATE_CONFLICT/);
  const s = await f.client.create("0020");
  assert.equal(s.id, session);
  assert.equal(s.pcCapability, undefined);
  assert.ok(f.calls[2].url.endsWith(`/${id}/registrations`));
  assert.equal(f.calls[2].options.headers["X-PC-Capability"], "p".repeat(43));
  await f.client.upload(
    { password: "fixture" },
    Buffer.from([1]),
    Buffer.from([2]),
  );
  assert.equal(f.calls[3].options.headers["X-PC-Capability"], "s".repeat(43));
  assert.equal(
    f.calls[3].options.headers["X-Upload-Capability"],
    "u".repeat(43),
  );
  await assert.rejects(f.client.qr(), /CERTIFICATE_CONFLICT/);
});
test("completed pairing resets without deleting bank connection", async () => {
  const f = fixture();
  await f.client.startPairing();
  f.setStatus("PAIRED");
  await f.client.pairingStatus();
  await f.client.create("0020");
  f.setStatus("BOUND");
  await f.client.status();
  await f.client.cancel();
  assert.equal(f.calls.filter((c) => c.options.method === "DELETE").length, 0);
  await f.client.startPairing();
});
test("uncertain initial pairing cancellation retains capability for explicit retry", async () => {
  const f = fixture();
  await f.client.startPairing();
  f.failCancel();
  await assert.rejects(f.client.cancel(), /network/);
  await assert.rejects(f.client.startPairing(), /SESSION_EXISTS/);
});
test("server response cannot switch pairing identity", async () => {
  let count = 0;
  const c = new CertificateClient(
    "http://127.0.0.1:18081",
    async () =>
      new Response(
        JSON.stringify({
          success: true,
          data:
            ++count === 1
              ? {
                  id,
                  status: "WAITING_FOR_MOBILE",
                  pcCapability: "p".repeat(43),
                  qrToken: "q".repeat(43),
                  expiresAt: expiry(),
                  qrExpiresAt: expiry(),
                }
              : { id: session, status: "PAIRED" },
        }),
      ),
    true,
  );
  await c.startPairing();
  await assert.rejects(c.pairingStatus(), /INVALID_RESPONSE/);
});

test("only confirmed failures retry within the same mobile pairing", async () => {
  const f = fixture();
  await f.client.startPairing();
  f.setStatus("PAIRED");
  await f.client.pairingStatus();
  await f.client.create("0020");
  assert.throws(() => f.client.prepareRetry(), /CERTIFICATE_CONFLICT/);
  f.setStatus("UNKNOWN");
  await f.client.status();
  assert.throws(() => f.client.prepareRetry(), /CERTIFICATE_CONFLICT/);
  f.setStatus("FAILED");
  await f.client.status();
  f.client.prepareRetry();
  await f.client.create("0020");
  assert.equal(f.calls.filter(c => c.url.endsWith("/certificate-pairings")).length, 1);
  assert.equal(f.calls.filter(c => c.url.endsWith("/registrations")).length, 2);
});

for (const organization of [null, "ABCD", "20"]) {
  test(`paired state cannot unlock without a valid mobile bank: ${organization}`, async () => {
    const f = fixture();
    await f.client.startPairing();
    f.setStatus("PAIRED");
    f.setOrganization(organization);
    await assert.rejects(f.client.pairingStatus(), /CERTIFICATE_BANK_SELECTION_REQUIRED/);
    await assert.rejects(f.client.create("0020"), /CERTIFICATE_CONFLICT/);
    assert.equal(f.calls.filter(c => c.url.endsWith("/registrations")).length, 0);
  });
}
test("polling cannot change a mobile-selected bank", async () => {
  const f = fixture();
  await f.client.startPairing();
  f.setStatus("PAIRED");
  await f.client.pairingStatus();
  f.setOrganization("0004");
  await assert.rejects(f.client.pairingStatus(), /INVALID_RESPONSE/);
});
