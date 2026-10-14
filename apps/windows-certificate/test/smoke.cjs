"use strict";
// Actual Electron renderer/IPC; all API and certificate inputs are synthetic fixtures.
const { app, BrowserWindow, dialog } = require("electron");
const http = require("node:http"),
  fs = require("node:fs/promises"),
  syncFs = require("node:fs"),
  path = require("node:path"),
  os = require("node:os"),
  assert = require("node:assert/strict"),
  { X509Certificate } = require("node:crypto");
app.disableHardwareAcceleration();
let server,
  directory,
  paired = false,
  expired = false,
  status = "CREATED",
  failureCode,
  uploads = 0,
  creates = 0,
  mobileCancelled = false,
  cancelAcknowledged = false;
const id = "00000000-0000-4000-8000-000000000001",
  pairId = "00000000-0000-4000-8000-000000000002";
const timeout = setTimeout(() => {
  console.error("Electron pairing smoke timed out");
  app.exit(1);
}, 75000);
const delay = (ms) => new Promise((r) => setTimeout(r, ms));
const expiry = () =>
  new Date(Date.now() + 600000).toISOString().replace("Z", "");
process.env.HOWMUCH_CERTIFICATE_LOCAL = "1";
require("../src/volumes.cjs").removableVolumes = async () => ({
  volumes: directory ? [{ root: directory, id: "synthetic-usb" }] : [],
  unavailable: false,
});
require("../src/certificates.cjs").defaultRoots = () => [directory];
async function wait(w, expression, label) {
  const until = Date.now() + 8000;
  while (Date.now() < until) {
    if (await w.webContents.executeJavaScript(expression)) return;
    await delay(50);
  }
  assert.fail(label);
}
async function capture(w, name) {
  await fs.mkdir(path.join(__dirname, "../dist"), { recursive: true });
  await fs.writeFile(
    path.join(__dirname, "../dist", name),
    (await w.webContents.capturePage()).toPNG(),
  );
}
async function run() {
  directory = syncFs.mkdtempSync(path.join(os.tmpdir(), "howmuch-pair-smoke-"));
  const der = new X509Certificate(
    syncFs.readFileSync(path.join(__dirname, "fixtures/certificate.pem")),
  ).raw;
  syncFs.writeFileSync(path.join(directory, "signCert.der"), der);
  syncFs.writeFileSync(path.join(directory, "signPri.key"), Buffer.from([2]));
  dialog.showOpenDialog = async () => ({
    canceled: false,
    filePaths: [directory],
  });
  server = http.createServer(async (req, res) => {
    let body = "";
    for await (const c of req) body += c;
    const send = (data) => {
      res.setHeader("Content-Type", "application/json");
      res.end(JSON.stringify({ success: true, data }));
    };
    if (req.url.endsWith("/institutions"))
      return send([
        {
          organization: "0020",
          name: "테스트 은행",
          supported: true,
          registrationAccountRequired: true,
          birthDateRequired: true,
        },
      ]);
    if (req.url.endsWith("/certificate-pairings")) {
      creates++;
      expired = false;
      return send({
        id: pairId,
        status: "WAITING_FOR_MOBILE",
        pcCapability: "P".repeat(43),
        qrToken: "Q".repeat(43),
        expiresAt: expiry(),
        qrExpiresAt: expiry(),
      });
    }
    if (req.url.endsWith("/pc")) {
      assert.equal(req.headers["x-pc-capability"], "P".repeat(43));
      if (req.method === "DELETE") {
        await delay(200);
        cancelAcknowledged = true;
        return send(null);
      }
      return send({
        id: pairId,
        status: mobileCancelled ? "CANCELLED" : expired ? "EXPIRED" : paired ? "PAIRED" : "WAITING_FOR_MOBILE",
        expiresAt: expiry(),
      });
    }
    if (req.url.endsWith("/registrations")) {
      assert.ok(paired);
      assert.equal(req.headers["x-pc-capability"], "P".repeat(43));
      return send({
        id,
        status: "CREATED",
        pcCapability: "A".repeat(43),
        uploadCapability: "B".repeat(43),
        expiresAt: expiry(),
      });
    }
    assert.equal(req.headers["x-pc-capability"], "A".repeat(43));
    if (req.url.endsWith("/upload")) {
      const b = JSON.parse(body);
      assert.equal(b.derFile, der.toString("base64"));
      assert.equal(b.keyFile, "Ag==");
      assert.equal(b.password, "synthetic-password");
      uploads++;
      status = "REGISTERING";
      return send({ id, status, expiresAt: expiry() });
    }
    if (req.url.endsWith("/qr"))
      assert.fail("No second QR is allowed after PC authentication");
    if (req.url.endsWith("/claim"))
      return send({
        claimId: id,
        status: "RESERVED",
        confirmationCode: "123456",
        expiresAt: expiry(),
      });
    if (req.method === "DELETE") {
      status = "CREATED";
      return send(null);
    }
    return send({ id, status, failureCode, expiresAt: expiry() });
  });
  const port = 34000 + Math.floor(Math.random() * 10000);
  server.listen(port, "127.0.0.1");
  process.env.HOWMUCH_CERTIFICATE_API_ORIGIN = `http://127.0.0.1:${port}`;
  require("../src/main.cjs");
  await app.whenReady();
  let w;
  const until = Date.now() + 10000;
  while (Date.now() < until) {
    w = BrowserWindow.getAllWindows()[0];
    if (w) break;
    await delay(50);
  }
  assert.ok(w);
  await wait(
    w,
    '!document.getElementById("pairingQr").hidden',
    "QR must appear before certificate input",
  );
  assert.equal(
    await w.webContents.executeJavaScript(
      'document.getElementById("registration").hidden && document.getElementById("submit").disabled && typeof require === "undefined"',
    ),
    true,
  );
  assert.equal(uploads, 0);
  assert.equal(w.isMinimizable(), false);
  await wait(w,
    `document.getElementById('clientVersion')?.textContent === ${JSON.stringify(`Client ver. ${app.getVersion()}`)} && !document.getElementById('clientVersion').hidden`,
    "Installed client version must be visible independently of the API",
  );
  assert.equal(w.isMaximizable(), false);
  assert.equal(w.isFullScreenable(), false);
  await capture(w, "electron-initial-pairing-smoke.png");
  expired = true;
  await wait(
    w,
    '!document.getElementById("retryPairing").hidden && document.getElementById("pairingQr").hidden',
    "Expired initial QR must be removed",
  );
  await w.webContents.executeJavaScript(
    'document.getElementById("retryPairing").click()',
  );
  await wait(
    w,
    '!document.getElementById("pairingQr").hidden',
    "Retry must mint a fresh pairing",
  );
  assert.equal(creates, 2);
  paired = true;
  await wait(
    w,
    '!document.getElementById("registration").hidden && document.querySelector("input[name=certificate]") && document.getElementById("certificatePanel").getAttribute("aria-busy")==="false"',
    "PC input unlocks only after mobile pairing",
  );
  await w.webContents.executeJavaScript(
    `document.getElementById('organization').value='0020';document.getElementById('organization').dispatchEvent(new Event('change'));document.querySelector('input[name=certificate]').click();for(const [id,value] of Object.entries({password:'synthetic-password',accountNumber:'123456',accountPassword:'1234',birthDate:'19900101'})){document.getElementById(id).value=value;document.getElementById(id).dispatchEvent(new Event('input'));}`,
  );
  assert.equal(
    await w.webContents.executeJavaScript(
      'document.getElementById("submit").disabled',
    ),
    false,
  );
  await capture(w, "electron-paired-import-smoke.png");
  await w.webContents.executeJavaScript(
    'document.getElementById("registration").requestSubmit()',
  );
  await wait(
    w,
    'document.getElementById("progressTitle").textContent==="금융사 인증 중"',
    "PC must display provider progress",
  );
  assert.equal(uploads, 1);
  assert.equal(
    await w.webContents.executeJavaScript(
      'document.getElementById("password").value==="" && document.getElementById("qrImage").hidden',
    ),
    true,
  );
  status = "READY_FOR_CLAIM";
  await wait(
    w,
    'document.getElementById("progressTitle").textContent==="휴대폰에서 연결을 승인해 주세요"',
    "Successful auth must wait for final mobile approval",
  );
  assert.equal(
    await w.webContents.executeJavaScript(
      'document.getElementById("qrImage").hidden',
    ),
    true,
  );
  status = "CLAIM_RESERVED";
  await wait(
    w,
    'document.getElementById("confirmationCode").textContent==="123456"',
    "Final claim confirmation must be shown",
  );
  await capture(w, "electron-final-approval-smoke.png");
  status = "BOUND";
  await wait(
    w,
    'document.getElementById("progressTitle").textContent==="연결이 완료됐어요" && document.getElementById("claimBox").hidden',
    "Final Yes completes binding",
  );
  await capture(w, "electron-bound-success-smoke.png");
  await w.webContents.executeJavaScript(
    'document.getElementById("cancel").click()',
  );
  await wait(
    w,
    '!document.getElementById("registration").hidden && document.querySelector("input[name=certificate]")',
    "New import reconnects after completion",
  );
  await w.webContents.executeJavaScript(
    `document.getElementById('organization').value='0020';document.getElementById('organization').dispatchEvent(new Event('change'));document.querySelector('input[name=certificate]').click();for(const [id,value] of Object.entries({password:'synthetic-password',accountNumber:'123456',accountPassword:'1234',birthDate:'19900101'})){document.getElementById(id).value=value;document.getElementById(id).dispatchEvent(new Event('input'));}document.getElementById('registration').requestSubmit();`,
  );
  await wait(
    w,
    'document.getElementById("progressTitle").textContent==="금융사 인증 중"',
    "Second import starts auth",
  );
  status = "FAILED";
  failureCode = "BANK_CERTIFICATE_PASSWORD_INVALID";
  await wait(
    w,
    '!document.getElementById("registration").hidden && document.getElementById("message").textContent.includes("암호")',
    "Provider error must display before any final approval",
  );
  assert.equal(
    await w.webContents.executeJavaScript(
      'document.getElementById("message").textContent',
    ),
    require("../src/client.cjs").userErrorMessage(failureCode),
  );
  await capture(w, "electron-password-error-smoke.png");
  assert.equal(await w.webContents.executeJavaScript('document.getElementById("password").value'), "");
  const beforeRetry = creates;
  await w.webContents.executeJavaScript(
    `document.getElementById('organization').value='0020';document.getElementById('organization').dispatchEvent(new Event('change'));document.querySelector('input[name=certificate]').click();for(const [id,value] of Object.entries({password:'synthetic-password',accountNumber:'123456',accountPassword:'1234',birthDate:'19900101'})){document.getElementById(id).value=value;document.getElementById(id).dispatchEvent(new Event('input'));}document.getElementById('registration').requestSubmit();`,
  );
  await wait(w, 'document.getElementById("progressTitle").textContent==="금융사 인증 중"', "Retry authentication");
  assert.equal(creates, beforeRetry);
  const retryDeadline = Date.now() + 5000;
  while (uploads < 3 && Date.now() < retryDeadline) await delay(25);
  assert.equal(uploads, 3);
  mobileCancelled = true;
  await wait(w, 'document.getElementById("progressTitle").textContent==="연결이 취소됐어요"', "Mobile cancellation reaches PC");
  app.on("before-quit", event => event.preventDefault());
  w.close();
  assert.equal(w.isDestroyed(), false);
  const deadline = Date.now() + 5000;
  while (!w.isDestroyed() && Date.now() < deadline) await delay(25);
  assert.equal(cancelAcknowledged, true);
  assert.equal(w.isDestroyed(), true);
  console.log(
    "Electron initial pairing / expiry / PC auth / final Yes / password error smoke passed",
  );
}
run()
  .then(() => app.exit(0))
  .catch((e) => {
    console.error(e);
    app.exit(1);
  })
  .finally(async () => {
    clearTimeout(timeout);
    server?.close();
    if (directory) await fs.rm(directory, { recursive: true, force: true });
  });
