"use strict";
// Verify the final builder signature after fuses are applied, then launch the real app.
const fs = require("node:fs");
const path = require("node:path");
const { execFileSync, spawnSync } = require("node:child_process");
const assert = require("node:assert/strict");
const asar = require("@electron/asar");
const { apiOrigin } = require("../src/security.cjs");
const arch = process.argv[2];
assert.ok(["arm64", "x64"].includes(arch), "Pass arm64 or x64.");
const dir = path.resolve("dist", arch === "arm64" ? "mac-arm64" : "mac");
const apps = fs.readdirSync(dir).filter((name) => name.endsWith(".app"));
assert.equal(apps.length, 1, "Expected exactly one packaged app.");
const app = path.join(dir, apps[0]);
execFileSync("codesign", ["--verify", "--deep", "--strict", app], { stdio: "inherit" });
// codesign writes signature details to stderr, including on success.
const display = spawnSync("codesign", ["--display", "--verbose=4", app], { encoding: "utf8" });
assert.equal(display.status, 0, "Signature details unavailable.");
assert.match(display.stderr, /^Signature=adhoc$/m, "Expected ad-hoc signing.");
const flags = Number.parseInt(/flags=0x([0-9a-f]+)/i.exec(display.stderr)?.[1], 16);
assert.ok((flags & 0x10000) !== 0, "Hardened runtime must remain enabled.");
const config = JSON.parse(asar.extractFile(path.join(app, "Contents/Resources/app.asar"), "release-config.json"));
assert.equal(config.apiOrigin, apiOrigin(process.env.HOWMUCH_CERTIFICATE_API_ORIGIN));
const executables = fs.readdirSync(path.join(app, "Contents/MacOS"));
assert.equal(executables.length, 1);
execFileSync(process.execPath, ["test/packaged-smoke.cjs", path.join(app, "Contents/MacOS", executables[0])], { stdio: "inherit" });
console.log(`macOS ${arch}: ad-hoc signature, packaged API origin and startup verified.`);
