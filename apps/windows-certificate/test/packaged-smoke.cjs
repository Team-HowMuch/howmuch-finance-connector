"use strict";
// Run the real packaged executable, including its ASAR and security fuses.
// No credentials or synthetic environment overrides enter the packaged app.
const { spawn } = require("node:child_process");
const path = require("node:path");
const fs = require("node:fs/promises");
const assert = require("node:assert/strict");

const executable = process.argv[2];
if (!executable) throw new Error("Pass the packaged executable path.");
const screenshot = path.resolve(
  process.argv[3] || "dist/packaged-startup-smoke.png",
);
const delay = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
let child, socket, debuggerAddress;
const pending = new Map();
let nextId = 1;

async function run() {
  await fs.access(executable);
  child = spawn(
    executable,
    ["--remote-debugging-address=127.0.0.1", "--remote-debugging-port=0"],
    { stdio: ["ignore", "pipe", "pipe"] },
  );
  let output = "";
  const inspect = (chunk) => {
    output = (output + chunk.toString()).slice(-8192);
    const match = output.match(/DevTools listening on (ws:\/\/[^\s]+)/);
    if (match) debuggerAddress = new URL(match[1]);
  };
  child.stdout.on("data", inspect);
  child.stderr.on("data", inspect);
  let exit, startupError;
  child.on("error", (error) => {
    startupError = error;
  });
  child.on("exit", (code, signal) => {
    exit = { code, signal };
  });
  const deadline = Date.now() + 20_000;
  while (!debuggerAddress && !exit && !startupError && Date.now() < deadline)
    await delay(100);
  assert.ok(!startupError, "Packaged executable could not be started.");
  assert.ok(!exit, "Packaged app exited before creating its renderer.");
  assert.ok(debuggerAddress, "Packaged app did not expose its test debugger.");
  assert.equal(debuggerAddress.hostname, "127.0.0.1");
  const origin = `http://127.0.0.1:${debuggerAddress.port}`;
  let target;
  while (!target && Date.now() < deadline) {
    const list = await (await fetch(`${origin}/json`)).json();
    target = list.find((item) => item.type === "page");
    if (!target) await delay(100);
  }
  assert.ok(target, "Packaged renderer target was not created.");
  const targetAddress = new URL(target.webSocketDebuggerUrl);
  assert.equal(targetAddress.hostname, "127.0.0.1");
  assert.equal(targetAddress.port, debuggerAddress.port);
  socket = new WebSocket(targetAddress);
  await new Promise((resolve, reject) => {
    socket.onopen = resolve;
    socket.onerror = reject;
  });
  socket.onmessage = (event) => {
    const result = JSON.parse(event.data);
    if (pending.has(result.id)) {
      pending.get(result.id)(result);
      pending.delete(result.id);
    }
  };
  let state;
  while (Date.now() < deadline) {
    const result = await send("Runtime.evaluate", {
      expression: `JSON.stringify({
        url: location.href,
        title: document.title,
        brand: document.querySelector('.brand')?.textContent,
        heading: document.querySelector('h1')?.textContent,
        form: Boolean(document.getElementById('registration')),
        bridge: typeof window.certificate,
        discovery: typeof window.certificate?.discover,
        rawPicker: typeof window.certificate?.choose,
        iconLoaded: Boolean(document.querySelector('.brand-icon')?.naturalWidth),
        scanDone: document.getElementById('certificatePanel')?.getAttribute('aria-busy') === 'false',
        node: typeof require,
        styles: document.styleSheets.length,
        message: document.getElementById('message')?.textContent,
        messageHidden: document.getElementById('message')?.hidden,
        fontsLoaded: document.fonts.status === 'loaded' && document.fonts.check('700 25px Pretendard')
      })`,
      returnByValue: true,
    });
    if (result.result?.result?.value) {
      state = JSON.parse(result.result.result.value);
      if (
        state.form &&
        state.bridge === "object" &&
        state.styles > 0 &&
        state.fontsLoaded &&
        state.iconLoaded &&
        state.scanDone &&
        state.message !== "금융사 불러오는 중…"
      )
        break;
    }
    await delay(100);
  }
  assert.equal(state?.url, "howmuch-cert://app/index.html");
  assert.equal(state?.title, "인증서 가져오기");
  assert.equal(state?.brand, "인증서 가져오기");
  assert.equal(state?.heading, "인증서 가져오기");
  assert.equal(state?.form, true);
  assert.equal(state?.bridge, "object");
  assert.equal(state?.discovery, "function");
  assert.equal(state?.rawPicker, "undefined");
  assert.ok(state?.iconLoaded && state?.scanDone);
  assert.equal(state?.node, "undefined");
  assert.ok(state?.styles > 0);
  assert.ok(state?.fontsLoaded, "Bundled Pretendard fonts did not load.");
  assert.notEqual(state?.message, "금융사 불러오는 중…");
  assert.ok(state?.messageHidden || state?.message?.trim());
  await delay(250);
  const capture = await send("Page.captureScreenshot", { format: "png" });
  assert.ok(
    capture.result?.data,
    "Packaged renderer screenshot was not captured.",
  );
  await fs.mkdir(path.dirname(screenshot), { recursive: true });
  await fs.writeFile(screenshot, Buffer.from(capture.result.data, "base64"));
  console.log(
    "Packaged startup smoke passed: HTML, styles, icon, discovery, sandboxed preload, screenshot.",
  );
}

function send(method, params) {
  return new Promise((resolve) => {
    const id = nextId++;
    pending.set(id, resolve);
    socket.send(JSON.stringify({ id, method, params }));
  });
}

const timeout = setTimeout(() => {
  console.error("Packaged startup smoke timed out.");
  finish(1);
}, 25_000);
function finish(code) {
  clearTimeout(timeout);
  socket?.close();
  if (child && child.exitCode === null && child.signalCode === null) {
    child.kill();
    // A pending removable-volume permission/IO request can delay normal exit.
    const termination = setTimeout(() => child.kill("SIGKILL"), 1000);
    child.once("exit", () => clearTimeout(termination));
  }
  process.exitCode = code;
}
run().then(
  () => finish(0),
  (error) => {
    console.error("Packaged startup smoke failed:", error.message);
    finish(1);
  },
);
