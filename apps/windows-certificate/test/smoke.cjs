"use strict";
// Real Electron renderer/IPC with synthetic API + file-dialog boundary fixtures only.
const { app, BrowserWindow, dialog } = require("electron");
const http = require("node:http");
const fs = require("node:fs/promises");
const fsSync = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const assert = require("node:assert/strict");
const { X509Certificate } = require("node:crypto");
let server, directory;
let fixtureVolumes = [];
const hardTimeout = setTimeout(() => {
  console.error("Electron smoke timed out");
  app.exit(1);
}, 75000);
const fixturePort = 34000 + Math.floor(Math.random() * 10000);
process.env.HOWMUCH_CERTIFICATE_LOCAL = "1";
process.env.HOWMUCH_CERTIFICATE_API_ORIGIN = `http://127.0.0.1:${fixturePort}`;
require("../src/volumes.cjs").removableVolumes = async () => ({
  volumes: fixtureVolumes,
  unavailable: false,
});
require("../src/certificates.cjs").defaultRoots = () => [
  path.join(directory, "missing-computer"),
];
const id = "00000000-0000-4000-8000-000000000001";
let status = "CREATED",
  uploads = 0,
  failureCode,
  httpFailure = false,
  statusHttpFailure = false,
  statusNetworkFailure = false,
  qrHttpFailure = false,
  immediateFailure = false,
  claimResponseMode = "valid",
  claimDecisionRace = false,
  statusRequests = 0;
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
async function waitUntil(window, expression, description) {
  const end = Date.now() + 6000;
  while (Date.now() < end && !(await window.webContents.executeJavaScript(expression)))
    await delay(50);
  assert.equal(await window.webContents.executeJavaScript(expression), true, description);
}
async function run() {
  directory = fsSync.mkdtempSync(
    path.join(os.tmpdir(), "howmuch-certificate-smoke-"),
  );
  const der = new X509Certificate(
    fsSync.readFileSync(path.join(__dirname, "fixtures", "certificate.pem")),
  ).raw;
  fsSync.writeFileSync(path.join(directory, "signCert.der"), der);
  fsSync.writeFileSync(path.join(directory, "signPri.key"), Buffer.from([2]));
  fixtureVolumes = [{ root: directory, id: "synthetic-usb-1" }];
  dialog.showOpenDialog = async (_window, options) => {
    assert.deepEqual(options.properties, ["openDirectory"]);
    return { canceled: false, filePaths: [directory] };
  };
  server = http.createServer(async (req, res) => {
    let body = "";
    for await (const chunk of req) body += chunk;
    res.setHeader("Content-Type", "application/json");
    const send = (data) => res.end(JSON.stringify({ success: true, data }));
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
    if (req.method === "POST" && req.url.endsWith("/certificate-registrations"))
      return send({
        id,
        status: "CREATED",
        pcCapability: "A".repeat(43),
        uploadCapability: "B".repeat(43),
      });
    assert.equal(req.headers["x-pc-capability"], "A".repeat(43));
    if (req.url.endsWith("/upload")) {
      const input = JSON.parse(body);
      assert.equal(input.derFile, der.toString("base64"));
      assert.equal(input.keyFile, "Ag==");
      assert.equal(input.password, "synthetic-password");
      assert.equal(input.accountNumber, "123456");
      assert.equal(input.accountPassword, "1234");
      uploads++;
      if (httpFailure) {
        res.statusCode = 400;
        return res.end(JSON.stringify({ success: false, error: { code: "CERTIFICATE_INPUT_INVALID", message: "synthetic-private-response" } }));
      }
      status = failureCode ? "FAILED" : "READY_FOR_CLAIM";
      return send({ id, status: immediateFailure ? status : "SUBMITTED", failureCode,
        expiresAt: new Date(Date.now() + 600_000).toISOString().replace("Z", "") });
    }
    if (req.url.endsWith("/qr")) {
      if (qrHttpFailure) {
        res.statusCode = 400;
        return res.end(JSON.stringify({ success: false, error: {
          code: "BANK_CERTIFICATE_PASSWORD_INVALID", message: "synthetic-private-response",
        } }));
      }
      status = "READY_FOR_CLAIM";
      return send({
        sessionId: id,
        qrToken: "C".repeat(43),
        expiresAt: new Date(Date.now() + 60_000).toISOString().replace("Z", ""),
      });
    }
    if (req.url.endsWith("/claim") && claimDecisionRace) {
      claimDecisionRace = false;
      status = "SUBMITTED";
      res.statusCode = 404;
      return res.end(JSON.stringify({ success: false, error: { code: "CERTIFICATE_NOT_FOUND" } }));
    }
    if (req.url.endsWith("/claim"))
      return send({
        claimId: id,
        status: "RESERVED",
        confirmationCode: claimResponseMode === "invalid" ? "12345" : "123456",
        expiresAt: new Date(Date.now() + (claimResponseMode === "expired" ? -1000 : 60_000)).toISOString().replace("Z", ""),
      });
    if (req.method === "DELETE") return send(null);
    statusRequests++;
    if (statusNetworkFailure) {
      res.statusCode = 503;
      return res.end(JSON.stringify({ success: false, error: {
        code: "UNRECOGNIZED_PRIVATE_CODE", message: "synthetic-private-response",
      } }));
    }
    if (statusHttpFailure) {
      res.statusCode = 400;
      return res.end(JSON.stringify({ success: false, error: {
        code: "BANK_CERTIFICATE_PASSWORD_INVALID", message: "synthetic-private-response",
      } }));
    }
    return send({ id, status, failureCode, failureMessage: "synthetic-private-response",
      expiresAt: new Date(Date.now() + 600_000).toISOString().replace("Z", "") });
  });
  const listening = new Promise((resolve, reject) => {
    server.once("error", reject);
    server.listen(fixturePort, "127.0.0.1", resolve);
  });
  // Prepare files and bind the API before Electron can initialize the renderer.
  // Import synchronously, before app.ready, so privileged scheme registration remains valid.
  require("../src/main.cjs");
  await listening;
  process.env.HOWMUCH_CERTIFICATE_LOCAL = "1";
  process.env.HOWMUCH_CERTIFICATE_API_ORIGIN = `http://127.0.0.1:${server.address().port}`;
  await app.whenReady();
  const deadline = Date.now() + 65_000;
  let window;
  while (Date.now() < deadline) {
    window = BrowserWindow.getAllWindows()[0];
    if (
      window &&
      (await window.webContents
        .executeJavaScript(
          'Boolean(document.getElementById("organization")?.options.length > 1 && document.getElementById("certificatePanel")?.getAttribute("aria-busy") === "false")',
        )
        .catch(() => false))
    )
      break;
    await delay(100);
  }
  assert.ok(window, "Renderer window was not created");
  assert.equal(app.name, "얼마 인증서 가져오기");
  assert.equal(window.getTitle(), "인증서 가져오기");
  assert.equal(
    await window.webContents.executeJavaScript(
      "document.querySelector('.brand').textContent",
    ),
    "인증서 가져오기",
  );
  assert.equal(window.isMinimizable(), false);
  assert.equal(window.isMaximizable(), false);
  assert.equal(window.isFullScreenable(), false);
  assert.equal(await window.webContents.executeJavaScript(`
    getComputedStyle(document.querySelector('.app-header')).webkitAppRegion === 'drag'
    && getComputedStyle(document.getElementById('appClose')).webkitAppRegion === 'no-drag'
    && document.getElementById('appClose').getAttribute('aria-label') === '앱 종료'
  `), true);
  // Institution loading and asynchronous USB discovery complete independently.
  while (Date.now() < deadline && !(await window.webContents.executeJavaScript(
    "document.getElementById('usb').getAttribute('aria-pressed') === 'true' && document.querySelectorAll('.certificate-row').length === 1"
  ))) await delay(50);
  assert.equal(
    await window.webContents.executeJavaScript(
      `document.getElementById('usb').getAttribute('aria-pressed') === 'true' && document.querySelectorAll('.certificate-row').length === 1`,
    ),
    true,
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      `window.certificate.storage().then(s => Object.keys(s).sort().join(',') === 'deviceCount,revision,unavailable' && s.deviceCount === 1 && /^[a-f0-9]{64}$/.test(s.revision))`,
    ),
    true,
  );
  await window.webContents.executeJavaScript(
    `document.querySelector('input[name="certificate"]').click(); for (const id of ['password','accountNumber','accountPassword','birthDate']) document.getElementById(id).value='clear-on-unplug';`,
  );
  fixtureVolumes = [];
  while (
    Date.now() < deadline &&
    !(await window.webContents.executeJavaScript(
      `document.getElementById('computer').getAttribute('aria-pressed') === 'true' && document.querySelectorAll('.certificate-row').length === 0`,
    ))
  )
    await delay(50);
  assert.equal(
    await window.webContents.executeJavaScript(
      `['password','accountNumber','accountPassword','birthDate'].every(id => document.getElementById(id).value === '') && !document.querySelector('input[name="certificate"]:checked')`,
    ),
    true,
  );
  assert.equal(uploads, 0);
  fixtureVolumes = [{ root: directory, id: "synthetic-usb-2" }];
  while (
    Date.now() < deadline &&
    !(await window.webContents.executeJavaScript(
      `document.getElementById('usb').getAttribute('aria-pressed') === 'true' && document.querySelectorAll('.certificate-row').length === 1`,
    ))
  )
    await delay(50);
  assert.equal(
    await window.webContents.executeJavaScript(
      `document.getElementById('usb').getAttribute('aria-pressed') === 'true' && !document.querySelector('input[name="certificate"]:checked')`,
    ),
    true,
  );
  assert.equal(
    await window.webContents.executeJavaScript("typeof require"),
    "undefined",
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      `(async () => { await document.fonts.ready; return document.fonts.check('700 25px Pretendard') && !document.querySelector('.intro,.note,#pickDer,#pickKey') && typeof window.certificate.choose === 'undefined' && document.querySelector('.brand-icon').naturalWidth > 0 && document.getElementById('submit').disabled; })()`,
    ),
    true,
  );
  await window.webContents.executeJavaScript(
    `document.getElementById('organization').value='0020';document.getElementById('organization').dispatchEvent(new Event('change'));document.getElementById('folder').click()`,
  );
  while (
    Date.now() < deadline &&
    !(await window.webContents.executeJavaScript(
      `document.getElementById('usb').getAttribute('aria-pressed') === 'false' && document.getElementById('certificatePanel').getAttribute('aria-busy') === 'false'`,
    ))
  )
    await delay(50);
  assert.equal(
    await window.webContents.executeJavaScript(
      `document.querySelectorAll('.certificate-row').length`,
    ),
    1,
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      `document.querySelector('.certificate-owner strong').textContent`,
    ),
    "김하우",
  );
  await window.webContents.executeJavaScript(
    `document.querySelector('input[name="certificate"]').click()`,
  );
  await window.webContents.executeJavaScript(
    `for(const [id,value] of Object.entries({password:'synthetic-password',accountNumber:'123456',accountPassword:'1234',birthDate:'19900101'})) { document.getElementById(id).value=value; document.getElementById(id).dispatchEvent(new Event('input')); }`,
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      `!document.getElementById('submit').disabled && Boolean(document.querySelector('input[name="certificate"]:checked')) && !document.getElementById('accountFields').hidden && document.getElementById('accountNumber').required && document.getElementById('accountPassword').required && !document.getElementById('birthField').hidden`,
    ),
    true,
  );
  dialog.showOpenDialog = async () => ({ canceled: true, filePaths: [] });
  await window.webContents.executeJavaScript(
    `document.getElementById('folder').click()`,
  );
  while (
    Date.now() < deadline &&
    (await window.webContents.executeJavaScript(
      `document.getElementById('certificatePanel').getAttribute('aria-busy') !== 'false'`,
    ))
  )
    await delay(50);
  assert.equal(
    await window.webContents.executeJavaScript(
      `Boolean(document.querySelector('input[name="certificate"]:checked')) && document.getElementById('password').value === 'synthetic-password' && !document.getElementById('submit').disabled`,
    ),
    true,
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      `window.certificate.discover('../').then(() => false, () => true)`,
    ),
    true,
  );
  await fs.mkdir(path.join(__dirname, "..", "dist"), { recursive: true });
  await delay(250);
  await fs.writeFile(
    path.join(__dirname, "..", "dist", "electron-import-smoke.png"),
    (await window.webContents.capturePage()).toPNG(),
  );
  window.setSize(640, 780);
  await delay(250);
  assert.equal(
    await window.webContents.executeJavaScript(
      `document.documentElement.scrollWidth <= window.innerWidth`,
    ),
    true,
  );
  await fs.writeFile(
    path.join(__dirname, "..", "dist", "electron-import-compact-smoke.png"),
    (await window.webContents.capturePage()).toPNG(),
  );
  window.setSize(900, 780);
  await delay(250);
  await window.webContents.executeJavaScript(
    `document.getElementById('registration').requestSubmit()`,
  );
  while (
    Date.now() < deadline &&
    !(await window.webContents.executeJavaScript(
      '!document.getElementById("qrImage").hidden',
    ))
  )
    await delay(100);
  assert.equal(uploads, 1);
  assert.equal(
    await window.webContents.executeJavaScript(
      'document.getElementById("qrImage").src.startsWith("data:image/png;base64,")',
    ),
    true,
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      'document.getElementById("password").value',
    ),
    "",
  );
  await fs.mkdir(path.join(__dirname, "..", "dist"), { recursive: true });
  await window.webContents.executeJavaScript("window.scrollTo(0,0)");
  await delay(250);
  await fs.writeFile(
    path.join(__dirname, "..", "dist", "electron-qr-smoke.png"),
    (await window.webContents.capturePage()).toPNG(),
  );
  status = "CLAIM_RESERVED";
  while (
    Date.now() < deadline &&
    (await window.webContents.executeJavaScript(
      'document.getElementById("confirmationCode").textContent',
    )) !== "123456"
  )
    await delay(100);
  assert.equal(
    await window.webContents.executeJavaScript(
      'document.getElementById("confirmationCode").textContent',
    ),
    "123456",
  );
  assert.equal(
    await window.webContents.executeJavaScript(
      'document.getElementById("qrImage").hidden',
    ),
    true,
  );
  const confirmed = `(() => {
    const mark = document.querySelector('.progress-mark');
    return document.getElementById('progress').dataset.state === 'claim'
      && !document.getElementById('claimBox').hidden
      && document.getElementById('confirmationCode').textContent === '123456'
      && document.getElementById('progressTitle').textContent === '연결 요청이 확인됐어요'
      && getComputedStyle(mark).display !== 'none' && mark.getBoundingClientRect().height > 0
      && getComputedStyle(mark.firstElementChild).animationName === 'none';
  })()`;
  await waitUntil(window, confirmed, "Valid claim must show a visible success check");
  await fs.writeFile(path.join(__dirname, "..", "dist", "electron-claim-success-smoke.png"),
    (await window.webContents.capturePage()).toPNG());
  const cleared = `document.getElementById('progress').dataset.state === 'stopped'
    && document.getElementById('claimBox').hidden
    && document.getElementById('confirmationCode').textContent === ''
    && document.getElementById('qrCard').hidden
    && getComputedStyle(document.querySelector('.progress-mark')).display === 'none'`;
  // Losing status must remove the old success check; recovery clears the old error.
  statusNetworkFailure = true;
  await waitUntil(window, cleared, "A failed poll must clear the old confirmation");
  statusNetworkFailure = false;
  await waitUntil(window, confirmed, "A recovered poll must restore the confirmed request");
  assert.equal(await window.webContents.executeJavaScript(
    "document.getElementById('progressText').hidden && document.getElementById('message').hidden"
  ), true);
  claimResponseMode = "invalid";
  await waitUntil(window, cleared, "A malformed code must not show success");
  claimResponseMode = "valid";
  await waitUntil(window, confirmed, "A valid response after a malformed code must recover");
  claimResponseMode = "expired";
  await waitUntil(window, cleared + " && !document.getElementById('refreshQr').hidden", "An expired claim must offer a new QR without stale success");
  claimResponseMode = "valid";
  await window.webContents.executeJavaScript("document.getElementById('refreshQr').click()");
  await waitUntil(window, "!document.getElementById('qrImage').hidden", "Refreshing an expired claim must resume the QR flow");
  status = "CLAIM_RESERVED";
  await waitUntil(window, confirmed, "The new reservation must display its confirmed check");
  claimDecisionRace = true;
  await waitUntil(window, `document.getElementById('progressTitle').textContent === '금융사 인증 중'`, "Concurrent mobile approval must resume status polling after the claim disappears");
  status = "BOUND";
  await waitUntil(window, `document.getElementById('progressTitle').textContent === '연결이 완료됐어요'
    && document.getElementById('progress').dataset.state === 'claim'
    && document.getElementById('claimBox').hidden
    && document.getElementById('cancel').textContent === '다시 가져오기'`, "BOUND must show completed connection without a stale reservation code");
  await fs.writeFile(path.join(__dirname, "..", "dist", "electron-bound-success-smoke.png"),
    (await window.webContents.capturePage()).toPNG());
  await window.webContents.executeJavaScript("document.getElementById('cancel').click()");
  await waitUntil(window, "!document.getElementById('registration').hidden && document.getElementById('certificatePanel').getAttribute('aria-busy') === 'false' && Boolean(document.querySelector('input[name=certificate]'))", "Completion can reset and reload the certificate list");
  await window.webContents.executeJavaScript(`
    document.getElementById('organization').value='0020';
    document.getElementById('organization').dispatchEvent(new Event('change'));
    document.querySelector('input[name="certificate"]').click();
    for (const [id,value] of Object.entries({password:'synthetic-password',accountNumber:'123456',accountPassword:'1234',birthDate:'19900101'})) {
      document.getElementById(id).value=value; document.getElementById(id).dispatchEvent(new Event('input'));
    }
    document.getElementById('registration').requestSubmit();
  `);
  try {
    await waitUntil(window, "!document.getElementById('qrImage').hidden", "A new import after completion must issue a QR");
  } catch (error) {
    console.error("New import state", await window.webContents.executeJavaScript(`JSON.stringify({
      organization: document.getElementById('organization').value,
      formValid: document.getElementById('registration').checkValidity(),
      submitDisabled: document.getElementById('submit').disabled,
      selected: Boolean(document.querySelector('input[name="certificate"]:checked')),
      title: document.getElementById('progressTitle').textContent,
      message: document.getElementById('message').textContent,
      details: document.getElementById('progressText').textContent,
      scanning: document.getElementById('certificatePanel').getAttribute('aria-busy')
    })`));
    throw error;
  }
  status = "CLAIM_RESERVED";
  await waitUntil(window, confirmed, "A new import after completion must still work");
  // A known HTTP error while polling must not become a generic connection error.
  statusHttpFailure = true;
  const passwordError = require("../src/client.cjs").userErrorMessage("BANK_CERTIFICATE_PASSWORD_INVALID");
  const pollErrorDeadline = Date.now() + 4000;
  while (Date.now() < pollErrorDeadline && (await window.webContents.executeJavaScript(
    "document.getElementById('progressText').textContent"
  )) !== passwordError) await delay(50);
  assert.equal(await window.webContents.executeJavaScript(
    "document.getElementById('progressText').textContent"
  ), passwordError);
  assert.equal(await window.webContents.executeJavaScript(
    "document.getElementById('confirmationCode').textContent === '' && document.getElementById('claimBox').hidden && !document.body.textContent.includes('synthetic-private-response')"
  ), true);
  statusHttpFailure = false;
  await window.webContents.executeJavaScript(
    `document.getElementById('cancel').click()`,
  );
  while (
    Date.now() < deadline &&
    (await window.webContents.executeJavaScript(
      `document.getElementById('registration').hidden`,
    ))
  )
    await delay(100);
  assert.equal(
    await window.webContents.executeJavaScript(
      `!document.getElementById('registration').hidden && document.getElementById('step1').getAttribute('aria-current') === 'step' && !document.querySelector('input[name="certificate"]:checked') && document.getElementById('submit').disabled && document.getElementById('qrCard').hidden`,
    ),
    true,
  );
  // Real renderer + sandboxed IPC must show actionable errors and discard provider text.
  for (const { code, mode } of [
    { code: "BANK_CERTIFICATE_PASSWORD_INVALID", mode: "failed" },
    { code: "BANK_ACCOUNT_PASSWORD_LOCKED", mode: "failed" },
    { code: "CODEF_UNMAPPED_ERROR", mode: "failed" },
    { code: "CERTIFICATE_INPUT_INVALID", mode: "upload" },
    { code: "BANK_CERTIFICATE_PASSWORD_INVALID", mode: "status" },
    { code: "BANK_CERTIFICATE_PASSWORD_INVALID", mode: "qr" },
    { code: "BANK_CERTIFICATE_PASSWORD_INVALID", mode: "immediate" },
  ]) {
    failureCode = mode === "qr" ? undefined : code;
    httpFailure = mode === "upload";
    statusHttpFailure = mode === "status";
    qrHttpFailure = mode === "qr";
    immediateFailure = mode === "immediate";
    const previousStatusRequests = statusRequests;
    while (Date.now() < deadline && !(await window.webContents.executeJavaScript("Boolean(document.querySelector('input[name=certificate]'))"))) await delay(50);
    await window.webContents.executeJavaScript(`
      document.getElementById('organization').value='0020'; document.getElementById('organization').dispatchEvent(new Event('change'));
      document.querySelector('input[name="certificate"]').click();
      for(const [id,value] of Object.entries({password:'synthetic-password',accountNumber:'123456',accountPassword:'1234',birthDate:'19900101'})) {
        document.getElementById(id).value=value; document.getElementById(id).dispatchEvent(new Event('input'));
      }
      document.getElementById('registration').requestSubmit();
    `);
    const expected = require("../src/client.cjs").userErrorMessage(code);
    while (Date.now() < deadline && (await window.webContents.executeJavaScript("document.getElementById('progressText').textContent")) !== expected) await delay(50);
    assert.equal(await window.webContents.executeJavaScript("document.getElementById('progressText').textContent"), expected);
    assert.equal(await window.webContents.executeJavaScript(`(() => {
      const detail = document.getElementById('progressText');
      const rect = detail.getBoundingClientRect();
      return !detail.hidden && detail.getAttribute('role') === 'alert'
        && rect.height > 0 && rect.top >= 0 && rect.bottom <= window.innerHeight
        && Number(getComputedStyle(detail).fontWeight) >= 600
        && document.getElementById('confirmationCode').textContent === '';
    })()`), true, mode + " must show a visible, prominent error");
    if (mode === "immediate") assert.equal(statusRequests, previousStatusRequests, "An immediate terminal upload result must not be discarded for another poll");
    assert.equal(await window.webContents.executeJavaScript("document.body.textContent.includes('synthetic-private-response') || document.getElementById('password').value !== '' || !document.getElementById('qrCard').hidden"), false);
    if (code === "BANK_CERTIFICATE_PASSWORD_INVALID" && mode === "failed") await fs.writeFile(path.join(__dirname, "..", "dist", "electron-certificate-error-smoke.png"), (await window.webContents.capturePage()).toPNG());
    statusHttpFailure = qrHttpFailure = immediateFailure = httpFailure = false;
    await window.webContents.executeJavaScript("document.getElementById('cancel').click()");
    while (Date.now() < deadline && (await window.webContents.executeJavaScript("document.getElementById('registration').hidden"))) await delay(50);
  }
  let closeRequested = false;
  window.once("close", (event) => {
    event.preventDefault();
    closeRequested = true;
  });
  await window.webContents.executeJavaScript("document.getElementById('appClose').click()");
  for (let i = 0; i < 40 && !closeRequested; i++) await delay(50);
  assert.equal(closeRequested, true, "Custom close button must close through sandboxed IPC");
  console.log(
    "Electron smoke passed: custom close-only titlebar, USB discovery, sandbox, responsive layout, QR, visible reservation/completion checks, concurrent mobile approval, repeat import, invalid/expired claim rejection, transient recovery, terminal/upload/status/QR/immediate errors without stale success or private text.",
  );
}
run().then(
  () => finish(0),
  (error) => {
    console.error("Electron smoke failed:", error.stack);
    finish(1);
  },
);
async function finish(code) {
  clearTimeout(hardTimeout);
  if (server) server.close();
  if (directory) await fs.rm(directory, { recursive: true, force: true });
  app.exit(code);
}
