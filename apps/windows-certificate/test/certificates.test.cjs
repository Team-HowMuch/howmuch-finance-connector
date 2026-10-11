"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs/promises");
const path = require("node:path");
const os = require("node:os");
const { X509Certificate, randomBytes } = require("node:crypto");
const { CertificateStore, defaultRoots } = require("../src/certificates.cjs");
const { CertificateClient } = require("../src/client.cjs");

async function fixture(t) {
  const temporary = await fs.mkdtemp(
    path.join(os.tmpdir(), "howmuch-discovery-test-"),
  );
  const root = await fs.realpath(temporary);
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  const certificate = new X509Certificate(
    await fs.readFile(path.join(__dirname, "fixtures", "certificate.pem")),
  );
  async function pair(name) {
    const directory = path.join(root, name);
    await fs.mkdir(directory, { recursive: true });
    await fs.writeFile(path.join(directory, "signCert.der"), certificate.raw);
    await fs.writeFile(path.join(directory, "signPri.key"), Buffer.from([2]));
    return directory;
  }
  return {
    root,
    pair,
    certificate,
    now: Date.parse(certificate.validFrom) + 1000,
  };
}
test("default discovery is restricted to known OS certificate locations", () => {
  assert.ok(
    defaultRoots("win32", "C:\\Users\\sample", {}).includes(
      "C:\\Users\\sample\\AppData\\LocalLow\\NPKI",
    ),
  );
  assert.ok(
    defaultRoots("darwin", "/Users/sample", {}).includes(
      "/Users/sample/Library/Preferences/NPKI",
    ),
  );
  assert.deepEqual(defaultRoots("linux", "/home/sample", {}), [
    "/home/sample/NPKI",
  ]);
});
test("discovery returns pathless public metadata and ignores incomplete, malformed and linked pairs", async (t) => {
  const f = await fixture(t);
  const good = await f.pair("NPKI/yessign/USER/owner");
  const expectedKey = randomBytes(2048);
  await fs.writeFile(path.join(good, "signPri.key"), expectedKey);
  await f.pair("malformed");
  await fs.writeFile(
    path.join(f.root, "malformed", "signCert.der"),
    Buffer.from([1]),
  );
  await f.pair("incomplete");
  await fs.unlink(path.join(f.root, "incomplete", "signPri.key"));
  await fs.symlink(good, path.join(f.root, "linked"), "junction");
  const store = new CertificateStore(() => f.now);
  const result = await store.scan([f.root]);
  assert.equal(result.certificates.length, 1);
  const info = result.certificates[0];
  assert.deepEqual(Object.keys(info).sort(), [
    "id",
    "issuer",
    "owner",
    "status",
    "validTo",
  ]);
  assert.equal(info.owner, "김하우");
  assert.equal(info.issuer, "HowMuch Test CA");
  assert.equal(info.status, "VALID");
  assert.match(info.id, /^[a-f\d-]{36}$/);
  assert.ok(!JSON.stringify(result).includes(f.root));
  const material = await store.material(info.id);
  assert.deepEqual(material.der, f.certificate.raw);
  assert.deepEqual(material.key, expectedKey);
  // Keep fetch pending to exercise the same await/finally lifetime as main.cjs.
  let finishUpload, sentBody;
  const sessionId = "00000000-0000-4000-8000-000000000001";
  const client = new CertificateClient("https://api.example.test", async (_url, options) => {
    if (!_url.endsWith("/upload"))
      return new Response(JSON.stringify({ success: true, data: {
        id: sessionId, pcCapability: "p".repeat(43), uploadCapability: "u".repeat(43),
        status: "CREATED",
      } }));
    sentBody = options.body;
    await new Promise((resolve) => { finishUpload = resolve; });
    return new Response(JSON.stringify({ success: true, data: { id: sessionId, status: "SUBMITTED" } }));
  });
  await client.create("0020");
  const uploading = (async () => {
    try {
      return await client.upload({ password: " synthetic +한글! " }, material.der, material.key);
    } finally {
      material.der.fill(0);
      material.key.fill(0);
    }
  })();
  assert.deepEqual(material.key, expectedKey);
  const sent = JSON.parse(sentBody);
  assert.deepEqual(Buffer.from(sent.derFile, "base64"), f.certificate.raw);
  assert.deepEqual(Buffer.from(sent.keyFile, "base64"), expectedKey);
  assert.equal(sent.password, " synthetic +한글! ");
  finishUpload();
  await uploading;
  assert.ok(material.key.every((byte) => byte === 0));
  // Serialized strings must survive subsequent buffer clearing.
  assert.deepEqual(Buffer.from(JSON.parse(sentBody).keyFile, "base64"), expectedKey);
  const reread = await store.material(info.id);
  assert.deepEqual(reread.key, expectedKey);
  reread.der.fill(0);
  reread.key.fill(0);
  material.der.fill(0);
  material.key.fill(0);
  await assert.rejects(store.material(good));
  store.clear();
  await assert.rejects(store.material(info.id));
});
test("discovery does not read private keys until explicit material submission", async (t) => {
  const f = await fixture(t);
  await f.pair("owner");
  const originalOpen = fs.open;
  let keyOpens = 0;
  fs.open = async (file, ...args) => {
    if (path.basename(file) === "signPri.key") keyOpens++;
    return originalOpen(file, ...args);
  };
  try {
    const store = new CertificateStore(() => f.now);
    const result = await store.scan([f.root]);
    assert.equal(keyOpens, 0);
    const material = await store.material(result.certificates[0].id);
    assert.equal(keyOpens, 1);
    material.der.fill(0);
    material.key.fill(0);
  } finally {
    fs.open = originalOpen;
  }
});
test("expired and future certificates are visible but cannot be submitted", async (t) => {
  const f = await fixture(t);
  await f.pair("owner");
  for (const [now, status] of [
    [Date.parse(f.certificate.validFrom) - 1000, "NOT_YET_VALID"],
    [Date.parse(f.certificate.validTo), "EXPIRED"],
  ]) {
    const store = new CertificateStore(() => now);
    const result = await store.scan([f.root]);
    assert.equal(result.certificates[0].status, status);
    await assert.rejects(store.material(result.certificates[0].id));
  }
});
test("refresh invalidates old IDs and file replacement after discovery is rejected", async (t) => {
  const f = await fixture(t);
  const directory = await f.pair("owner");
  const store = new CertificateStore(() => f.now);
  const first = (await store.scan([f.root])).certificates[0];
  const second = (await store.scan([f.root])).certificates[0];
  await assert.rejects(store.material(first.id));
  await fs.writeFile(path.join(directory, "signPri.key"), Buffer.from([3, 4]));
  await assert.rejects(store.material(second.id));
  const third = (await store.scan([f.root])).certificates[0];
  await fs.unlink(path.join(directory, "signCert.der"));
  await fs.symlink(
    path.join(__dirname, "fixtures", "certificate.pem"),
    path.join(directory, "signCert.der"),
  );
  await assert.rejects(store.material(third.id));
});
test("discovery enforces traversal and result limits and reports partial results", async (t) => {
  const f = await fixture(t);
  await f.pair("deep/owner");
  await f.pair("another/owner");
  const store = new CertificateStore(() => f.now);
  const shallow = await store.scan([f.root], { maxDepth: 0 });
  assert.equal(shallow.certificates.length, 0);
  assert.equal(shallow.limited, true);
  const limited = await store.scan([f.root], { maxCertificates: 1 });
  assert.equal(limited.certificates.length, 1);
  assert.equal(limited.limited, true);
  assert.equal((await store.scan([f.root], { maxEntries: 1 })).limited, true);
  assert.equal(
    (await store.scan([f.root], { maxMilliseconds: 0 })).limited,
    true,
  );
  assert.deepEqual(
    (await store.scan([path.join(f.root, "absent")])).certificates,
    [],
  );
});
test("prioritized NPKI and volume roots do not duplicate a certificate", async (t) => {
  const f = await fixture(t);
  await f.pair("NPKI/issuer/USER/owner");
  const result = await new CertificateStore(() => f.now).scan([
    path.join(f.root, "NPKI"),
    f.root,
  ]);
  assert.equal(result.certificates.length, 1);
});
test("a stalled USB filesystem returns within the budget and cannot overwrite a later inventory", async (t) => {
  const f = await fixture(t);
  const directory = await f.pair("owner");
  const originalStat = fs.lstat;
  let release;
  fs.lstat = (file, ...args) =>
    file === f.root
      ? new Promise((resolve) => {
          release = resolve;
        })
      : originalStat(file, ...args);
  try {
    const store = new CertificateStore(() => f.now);
    const result = await store.scan([f.root], { maxMilliseconds: 20 });
    assert.equal(result.limited, true);
    assert.equal(result.certificates.length, 0);
    fs.lstat = originalStat;
    const next = await store.scan([directory]);
    release(await originalStat(f.root));
    await new Promise((resolve) => setImmediate(resolve));
    const material = await store.material(next.certificates[0].id);
    assert.deepEqual(material.der, f.certificate.raw);
    material.der.fill(0);
    material.key.fill(0);
    assert.equal(result.certificates.length, 0);
  } finally {
    fs.lstat = originalStat;
  }
});
