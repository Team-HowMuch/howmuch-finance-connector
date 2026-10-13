"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const { configure } = require("../scripts/configure.cjs");
const { releaseTarget } = require("../scripts/release.cjs");

function sandbox(t) {
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "certificate-build-"));
  t.after(() => fs.rmSync(root, { recursive: true, force: true }));
  for (const name of ["scripts/configure.cjs", "scripts/before-pack.cjs", "src/security.cjs"]) {
    const file = path.join(root, name);
    fs.mkdirSync(path.dirname(file), { recursive: true });
    fs.copyFileSync(path.join(__dirname, "..", name), file);
  }
  return root;
}

function runConfigure(root, value, args = []) {
  return spawnSync(process.execPath, [path.join(root, "scripts/configure.cjs"), ...args], {
    encoding: "utf8",
    env: { ...process.env, HOWMUCH_CERTIFICATE_API_ORIGIN: value },
  });
}

function readConfig(root) {
  return JSON.parse(fs.readFileSync(path.join(root, "release-config.json"), "utf8"));
}

test("configure reads the build environment and explicit arguments take precedence", (t) => {
  const root = sandbox(t);
  assert.equal(runConfigure(root, "https://dev.example.test/").status, 0);
  assert.deepEqual(readConfig(root), { apiOrigin: "https://dev.example.test" });
  assert.equal(runConfigure(root, "https://dev.example.test", ["https://prod.example.test"]).status, 0);
  assert.deepEqual(readConfig(root), { apiOrigin: "https://prod.example.test" });
});

test("missing or invalid origins fail without replacing the last valid config", (t) => {
  const root = sandbox(t);
  const output = path.join(root, "release-config.json");
  configure("https://valid.example.test", output);
  for (const value of ["", "invalid", "http://127.0.0.1:18081", "https://example.test/path",
    "https://user:password@example.test", "https://example.test?token=fixture", "https://example.test#fragment"]) {
    assert.notEqual(runConfigure(root, value).status, 0);
    assert.deepEqual(readConfig(root), { apiOrigin: "https://valid.example.test" });
  }
  const missing = spawnSync(process.execPath, [path.join(root, "scripts/configure.cjs")], {
    env: Object.fromEntries(Object.entries(process.env).filter(([key]) => key !== "HOWMUCH_CERTIFICATE_API_ORIGIN")),
  });
  assert.notEqual(missing.status, 0);
});

test("beforePack refreshes stale origin from env and enforces signing when required", async (t) => {
  const root = sandbox(t);
  const hook = require(path.join(root, "scripts/before-pack.cjs"));
  const keys = ["HOWMUCH_CERTIFICATE_API_ORIGIN", "HOWMUCH_REQUIRE_SIGNING", "CSC_LINK"];
  const previous = Object.fromEntries(keys.map((key) => [key, process.env[key]]));
  t.after(() => keys.forEach((key) => {
    if (previous[key] === undefined) delete process.env[key];
    else process.env[key] = previous[key];
  }));
  configure("https://old.example.test", path.join(root, "release-config.json"));
  const context = { electronPlatformName: "win32", packager: { platformSpecificBuildOptions: {} } };
  process.env.HOWMUCH_CERTIFICATE_API_ORIGIN = "https://prod.example.test";
  process.env.HOWMUCH_REQUIRE_SIGNING = "true";
  delete process.env.CSC_LINK;
  await assert.rejects(hook(context), /signing certificate is required/);
  assert.deepEqual(readConfig(root), { apiOrigin: "https://prod.example.test" });
  process.env.CSC_LINK = "synthetic-signing-file.pfx";
  await hook(context);
  assert.equal(context.packager.platformSpecificBuildOptions.forceCodeSigning, true);
  process.env.HOWMUCH_REQUIRE_SIGNING = "false";
  delete process.env.CSC_LINK;
  await hook({ electronPlatformName: "win32", packager: { platformSpecificBuildOptions: {} } });
  process.env.HOWMUCH_REQUIRE_SIGNING = "typo";
  await assert.rejects(hook(context), /must be true or false/);
  process.env.HOWMUCH_REQUIRE_SIGNING = "false";
  process.env.HOWMUCH_CERTIFICATE_API_ORIGIN = "";
  await assert.rejects(hook(context), /Set HOWMUCH_CERTIFICATE_API_ORIGIN/);
  delete process.env.HOWMUCH_CERTIFICATE_API_ORIGIN;
  await hook(context);
});

test("stable releases use prod; prereleases use dev; manual runs explicitly choose either", () => {
  assert.deepEqual(releaseTarget("release", { release: { tag_name: "certificate-v0.2.0", prerelease: false } }, "0.2.0"),
    { channel: "prod", environment: "certificate-windows-prod" });
  assert.deepEqual(releaseTarget("release", { release: { tag_name: "certificate-v0.2.0-rc.1", prerelease: true } }, "0.2.0-rc.1"),
    { channel: "dev", environment: "certificate-windows-dev" });
  for (const channel of ["dev", "prod"]) {
    assert.equal(releaseTarget("workflow_dispatch", { inputs: { target_environment: channel } }, "0.2.0").channel, channel);
  }
});

test("release validation rejects wrong tags, version mismatches, drafts and wrong channels", () => {
  for (const release of [
    { tag_name: "finance-contracts-v0.2.0" },
    { tag_name: "certificate-v0.1.0" },
    { tag_name: "certificate-v0.2.0", draft: true },
    { tag_name: "certificate-v0.2.0", prerelease: true },
  ]) assert.throws(() => releaseTarget("release", { release }, "0.2.0"));
  assert.throws(() => releaseTarget("release", { release: { tag_name: "certificate-v0.2.0-rc.1", prerelease: false } }, "0.2.0-rc.1"));
  assert.throws(() => releaseTarget("workflow_dispatch", { inputs: { target_environment: "invalid" } }, "0.2.0"));
  assert.throws(() => releaseTarget("push", {}, "0.2.0"));
});

test("release entry point writes validated GitHub job outputs", (t) => {
  const root = sandbox(t);
  const eventPath = path.join(root, "event.json"), output = path.join(root, "output.txt");
  const { version } = require("../package.json");
  fs.writeFileSync(eventPath, JSON.stringify({ release: { tag_name: `certificate-v${version}`, prerelease: version.includes("-") } }));
  const result = spawnSync(process.execPath, [path.join(__dirname, "../scripts/release.cjs")], {
    encoding: "utf8",
    env: { ...process.env, GITHUB_EVENT_NAME: "release", GITHUB_EVENT_PATH: eventPath, GITHUB_OUTPUT: output },
  });
  assert.equal(result.status, 0, result.stderr);
  const channel = version.includes("-") ? "dev" : "prod";
  assert.equal(fs.readFileSync(output, "utf8"), `channel=${channel}\nenvironment=certificate-windows-${channel}\n`);
});

test("release assets identify platform, environment and architecture with matching SHA-256", async (t) => {
  const { prepareArtifact } = require("../scripts/prepare-artifacts.cjs");
  const { createHash } = require("node:crypto");
  const dist = sandbox(t);
  const content = Buffer.from("synthetic-installer-payload");
  for (const [platform, arch, channel, suffix] of [
    ["win", "x64", "prod", "-Setup.exe"],
    ["mac", "arm64", "dev", ".dmg"],
    ["mac", "x64", "prod", ".dmg"],
  ]) {
    fs.writeFileSync(path.join(dist, `얼마-인증서-가져오기-0.2.0-${arch}${suffix}`), content);
    const output = await prepareArtifact({ platform, arch, channel, version: "0.2.0", dist });
    assert.equal(path.basename(output), `HowMuch-Certificate-0.2.0-${channel}-${arch}${suffix}`);
    assert.deepEqual(fs.readFileSync(output), content);
    assert.equal(fs.readFileSync(`${output}.sha256`, "utf8"),
      `${createHash("sha256").update(content).digest("hex")}  ${path.basename(output)}\n`);
  }
});

test("missing installers and invalid release coordinates fail instead of using a stale build", async (t) => {
  const { prepareArtifact } = require("../scripts/prepare-artifacts.cjs");
  const dist = sandbox(t);
  const valid = { platform: "mac", arch: "arm64", channel: "dev", version: "0.2.0", dist };
  fs.writeFileSync(path.join(dist, "unrelated-old-installer.dmg"), "old");
  await assert.rejects(prepareArtifact(valid), { code: "ENOENT" });
  for (const change of [{ platform: "linux" }, { channel: "../prod" }, { arch: "../x64" },
    { version: "../0.2.0" }, { platform: "win", arch: "arm64" }]) {
    await assert.rejects(prepareArtifact({ ...valid, ...change }), /Invalid installer/);
  }
});

test("Mac release configuration uses explicit ad-hoc signing without disabling runtime protections", () => {
  const { build } = require("../package.json");
  assert.equal(build.mac.identity, "-");
  assert.equal(build.mac.notarize, false);
  assert.equal(build.mac.hardenedRuntime, true);
  assert.equal(build.electronFuses.onlyLoadAppFromAsar, true);
  assert.equal(build.electronFuses.enableEmbeddedAsarIntegrityValidation, true);
});


test("DMG uses a solid background without creating a background-image Finder alias", async () => {
  const { DmgTarget } = require("dmg-builder");
  const { build } = require("../package.json");
  const packager = {
    config: { dmg: build.dmg },
    appInfo: { productFilename: "Synthetic Certificate" },
    getIconPath: async () => null,
  };
  const target = new DmgTarget(packager, "dist");
  const options = await target.computeDmgOptions("/synthetic/Certificate.app");
  assert.equal(options.backgroundColor, "#ffffff");
  assert.equal(options.background, undefined);
  assert.ok(options.contents.some((item) => item.type === "link" && item.path === "/Applications"));
});
