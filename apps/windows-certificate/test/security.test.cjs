"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { apiOrigin, uploadFields, validSender } = require("../src/security.cjs");
const { CertificateClient, userErrorMessage } = require("../src/client.cjs");
const id = "00000000-0000-4000-8000-000000000001",
  pc = "A".repeat(43),
  upload = "B".repeat(43);
function response(data) {
  return new Response(JSON.stringify({ success: true, data }), {
    headers: { "Content-Type": "application/json" },
  });
}
test("HTTPS-only origin rejects credentials, paths, redirects and nonlocal HTTP", () => {
  assert.equal(
    apiOrigin("https://api.example.test"),
    "https://api.example.test",
  );
  for (const value of [
    "http://api.example.test",
    "https://user:pass@api.example.test",
    "https://api.example.test/path",
    "https://api.example.test?token=x",
    "https://api.example.test#token",
  ])
    assert.throws(() => apiOrigin(value));
  assert.equal(
    apiOrigin("http://127.0.0.1:8080", true),
    "http://127.0.0.1:8080",
  );
  assert.throws(() => apiOrigin("http://127.0.0.1:8080"));
});
test("IPC requires the exact trusted main frame", () => {
  const frame = { url: "howmuch-cert://app/index.html" },
    contents = { mainFrame: frame },
    window = { isDestroyed: () => false, webContents: contents };
  assert.equal(
    validSender({ sender: contents, senderFrame: frame }, window),
    true,
  );
  assert.equal(
    validSender({ sender: contents, senderFrame: { url: frame.url } }, window),
    false,
  );
  frame.url = "https://evil.test";
  assert.equal(
    validSender({ sender: contents, senderFrame: frame }, window),
    false,
  );
});
test("upload bridge accepts only bounded typed fields, never renderer paths or URLs", () => {
  assert.deepEqual(
    uploadFields({
      password: "fixture",
      accountNumber: "",
      accountPassword: "",
    }),
    { password: "fixture" },
  );
  for (const value of [
    { password: "fixture", path: "/secret" },
    { password: "fixture", url: "https://evil.test" },
    { password: "x".repeat(257) },
    { password: "fixture", accountNumber: "123" },
    { password: "fixture", birthDate: "1990secret" },
  ])
    assert.throws(() => uploadFields(value));
});
test("capabilities stay private and are sent only through designated headers", async () => {
  const calls = [];
  const client = new CertificateClient(
    "https://api.example.test",
    async (url, options) => {
      calls.push({ url, options });
      return response({
        id,
        status: calls.length === 1 ? "CREATED" : "SUBMITTED",
        pcCapability: pc,
        uploadCapability: upload,
      });
    },
  );
  const created = await client.create("0020");
  assert.equal(created.pcCapability, undefined);
  assert.equal(created.uploadCapability, undefined);
  const submitted = await client.upload(
    { password: "fixture" },
    Buffer.from([1]),
    Buffer.from([2]),
  );
  assert.equal(submitted.pcCapability, undefined);
  assert.equal(calls[1].options.headers["X-PC-Capability"], pc);
  assert.equal(calls[1].options.headers["X-Upload-Capability"], upload);
  assert.equal(calls[1].options.redirect, "error");
  assert.ok(!calls[1].url.includes(pc));
  assert.ok(!calls[1].options.body.includes(pc));
  await client.status();
  assert.equal(calls[2].options.headers["X-Upload-Capability"], undefined);
  await client.cancel();
  await assert.rejects(client.status(), /NO_SESSION/);
});
test("network uncertainty never silently repeats upload", async () => {
  let calls = 0;
  const client = new CertificateClient("https://api.example.test", async () => {
    calls++;
    if (calls === 1)
      return response({
        id,
        pcCapability: pc,
        uploadCapability: upload,
        status: "CREATED",
      });
    throw new Error("fixture-timeout");
  });
  await client.create("0020");
  await assert.rejects(
    client.upload({ password: "fixture" }, Buffer.from([1]), Buffer.from([2])),
  );
  assert.equal(calls, 2);
  await assert.rejects(client.create("0020"), /SESSION_EXISTS/);
});
test("oversized response is rejected", async () => {
  const client = new CertificateClient(
    "https://api.example.test",
    async () => new Response("x".repeat(1024 * 1024 + 1)),
  );
  await assert.rejects(client.banks(), /RESPONSE_TOO_LARGE/);
});
test("uncertain cancellation keeps the capability available for an explicit retry", async () => {
  for (const failure of ["network", "server"]) {
    let deletes = 0;
    const client = new CertificateClient(
      "https://api.example.test",
      async (_url, options) => {
        if (options.method === "POST")
          return response({
            id,
            pcCapability: pc,
            uploadCapability: upload,
            status: "CREATED",
          });
        assert.equal(options.headers["X-PC-Capability"], pc);
        if (options.method === "DELETE" && ++deletes === 1) {
          if (failure === "network") throw new Error("fixture-timeout");
          return new Response(null, { status: 503 });
        }
        return response({ id, status: "CREATED" });
      },
    );
    await client.create("0020");
    await assert.rejects(client.cancel());
    assert.equal((await client.status()).id, id);
    await client.cancel();
    assert.equal(deletes, 2);
    await assert.rejects(client.status(), /NO_SESSION/);
  }
});
test("an absent registration completes local cancellation", async () => {
  const client = new CertificateClient(
    "https://api.example.test",
    async (_url, options) =>
      options.method === "POST"
        ? response({
            id,
            pcCapability: pc,
            uploadCapability: upload,
            status: "CREATED",
          })
        : new Response(null, { status: 404 }),
  );
  await client.create("0020");
  await client.cancel();
  await assert.rejects(client.status(), /NO_SESSION/);
});

test("only known user codes survive HTTP errors, never provider messages or details", async () => {
  for (const code of ["BANK_ACCOUNT_PASSWORD_LOCKED", "CF-13032", "constructor"]) {
    let calls = 0;
    const client = new CertificateClient("https://api.example.test", async () => {
      calls++;
      return new Response(JSON.stringify({ success: false, error: {
        code, message: "synthetic-private-password", details: { keyFile: "synthetic-private-key" },
      } }), { status: 422 });
    });
    await assert.rejects(client.create("0020"), (error) => {
      assert.equal(error.message, code === "BANK_ACCOUNT_PASSWORD_LOCKED" ? code : "API_422");
      assert.ok(!error.message.includes("synthetic-private"));
      return true;
    });
    assert.equal(calls, 1);
  }
});

test("status uses static common-code messages, including legacy and unknown fallbacks", async () => {
  for (const [code, expected] of [
    ["BANK_CERTIFICATE_PASSWORD_INVALID", "BANK_CERTIFICATE_PASSWORD_INVALID"],
    ["BANK_ACCOUNT_PASSWORD_LOCKED", "BANK_ACCOUNT_PASSWORD_LOCKED"],
    ["REGISTRATION_UNCERTAIN", "CERTIFICATE_REGISTRATION_UNCERTAIN"],
    ["CF-99998", "CODEF_UNMAPPED_ERROR"],
  ]) {
    let calls = 0;
    const client = new CertificateClient("https://api.example.test", async () => response(++calls === 1
      ? { id, pcCapability: pc, uploadCapability: upload, status: "CREATED" }
      : { id, status: "FAILED", failureCode: code, failureMessage: "synthetic-private-password", keyFile: "synthetic-private-key" }));
    await client.create("0020");
    const state = await client.status();
    assert.equal(state.failureCode, expected);
    assert.equal(state.failureMessage, userErrorMessage(expected));
    assert.ok(!JSON.stringify(state).includes("synthetic-private"));
    assert.equal(state.keyFile, undefined);
  }
});
