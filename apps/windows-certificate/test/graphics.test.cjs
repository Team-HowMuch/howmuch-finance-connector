"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");
const { createRequire } = require("node:module");

function startup(platform) {
  const filename = path.join(__dirname, "..", "src", "main.cjs");
  const realRequire = createRequire(filename);
  const calls = [];
  const app = {
    setName() {},
    disableHardwareAcceleration: () => calls.push("software-rendering"),
    enableSandbox: () => calls.push("sandbox"),
    whenReady: () => {
      calls.push("ready");
      return { then() {} };
    },
    on() {},
  };
  vm.runInNewContext(
    fs.readFileSync(filename, "utf8"),
    {
      require: (id) =>
        id === "electron"
          ? { app, protocol: { registerSchemesAsPrivileged() {} } }
          : realRequire(id),
      process: { platform },
    },
    { filename },
  );
  return calls;
}

test("Windows chooses software rendering before Electron starts", () => {
  const calls = startup("win32");
  assert.equal(calls.filter((value) => value === "software-rendering").length, 1);
  assert.ok(calls.indexOf("software-rendering") < calls.indexOf("ready"));
  assert.ok(calls.includes("sandbox"));
});

test("other platforms retain their graphics defaults and sandbox", () => {
  for (const platform of ["darwin", "linux"]) {
    const calls = startup(platform);
    assert.ok(!calls.includes("software-rendering"));
    assert.ok(calls.includes("sandbox"));
    assert.ok(calls.includes("ready"));
  }
});
