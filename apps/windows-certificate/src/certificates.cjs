"use strict";
const fs = require("node:fs/promises");
const { constants } = require("node:fs");
const path = require("node:path");
const os = require("node:os");
const { X509Certificate, randomUUID } = require("node:crypto");
const { MAX_FILE } = require("./security.cjs");

function defaultRoots(
  platform = process.platform,
  home = os.homedir(),
  env = process.env,
) {
  if (platform === "win32") {
    const p = path.win32;
    return [
      ...new Set([
        p.join(home, "AppData", "LocalLow", "NPKI"),
        p.join(env.APPDATA || p.join(home, "AppData", "Roaming"), "NPKI"),
        p.join(env.LOCALAPPDATA || p.join(home, "AppData", "Local"), "NPKI"),
        p.join(env.ProgramFiles || "C:\\Program Files", "NPKI"),
        p.join(env["ProgramFiles(x86)"] || "C:\\Program Files (x86)", "NPKI"),
      ]),
    ];
  }
  return platform === "darwin"
    ? [
        path.join(home, "Library", "Preferences", "NPKI"),
        path.join(home, "Library", "NPKI"),
        path.join(home, "NPKI"),
      ]
    : [path.join(home, "NPKI")];
}

function sameFile(a, b) {
  return (
    a.dev === b.dev &&
    a.ino === b.ino &&
    a.size === b.size &&
    a.mtimeMs === b.mtimeMs &&
    a.ctimeMs === b.ctimeMs
  );
}
async function fileStat(file) {
  const stat = await fs.lstat(file);
  if (!stat.isFile() || stat.size < 1 || stat.size > MAX_FILE)
    throw new Error("INVALID_FILE");
  return stat;
}
// Reject links/replacements, and read at most MAX_FILE + 1 even if a file grows.
async function readFile(file, expected) {
  if ((await fs.realpath(file)) !== file) throw new Error("INVALID_FILE");
  const before = await fileStat(file);
  if (expected && !sameFile(before, expected)) throw new Error("CHANGED_FILE");
  const handle = await fs.open(
    file,
    constants.O_RDONLY | (constants.O_NOFOLLOW || 0),
  );
  const value = Buffer.alloc(MAX_FILE + 1);
  try {
    if (!sameFile(before, await handle.stat())) throw new Error("CHANGED_FILE");
    const { bytesRead } = await handle.read(value, 0, value.length, 0);
    if (bytesRead !== before.size || !sameFile(before, await handle.stat()))
      throw new Error("CHANGED_FILE");
    return Buffer.from(value.subarray(0, bytesRead));
  } finally {
    value.fill(0);
    await handle.close();
  }
}
function commonName(dn, fallback) {
  const value = Array.isArray(dn?.CN) ? dn.CN[0] : dn?.CN;
  return typeof value === "string" ? value : fallback;
}
function label(value) {
  return (
    value
      .replace(/\([^)]*\)/g, "")
      .replace(/\d{7,}/g, "")
      .replace(/[\p{Cc}\p{Cf}]/gu, "")
      .trim()
      .slice(0, 80) || "이름 없음"
  );
}
function metadata(cert, now) {
  const legacy = cert.toLegacyObject();
  const start = Date.parse(cert.validFrom);
  const end = Date.parse(cert.validTo);
  if (!Number.isFinite(start) || !Number.isFinite(end))
    throw new Error("INVALID_CERTIFICATE");
  return {
    owner: label(commonName(legacy.subject, "이름 없음")),
    issuer: label(commonName(legacy.issuer, "발급기관 정보 없음")),
    validTo: new Date(end).toISOString(),
    status: now < start ? "NOT_YET_VALID" : now >= end ? "EXPIRED" : "VALID",
  };
}

class CertificateStore {
  #entries = new Map();
  #now;
  #version = 0;
  constructor(now = () => Date.now()) {
    this.#now = now;
  }
  clear() {
    this.#entries.clear();
    this.#version++;
  }
  async scan(
    roots,
    {
      maxDepth = 6,
      maxEntries = 4000,
      maxCertificates = 100,
      maxMilliseconds = 5000,
    } = {},
  ) {
    this.clear();
    const version = this.#version,
      inventory = new Map();
    let active = true;
    const certificates = [],
      seen = new Set();
    const deadline = Date.now() + maxMilliseconds;
    let entries = 0,
      limited = false,
      unavailable = false;
    const walk = async (directory, depth) => {
      if (!active) return;
      if (seen.has(directory)) return;
      seen.add(directory);
      if (
        entries >= maxEntries ||
        certificates.length >= maxCertificates ||
        Date.now() >= deadline
      ) {
        limited = true;
        return;
      }
      let names = new Map(),
        directories = [];
      try {
        const dir = await fs.opendir(directory);
        for await (const item of dir) {
          if (!active || ++entries > maxEntries || Date.now() >= deadline) {
            limited = true;
            break;
          }
          if (item.isDirectory()) directories.push(item.name);
          else if (item.isFile()) names.set(item.name.toLowerCase(), item.name);
        }
      } catch (error) {
        if (!["ENOENT", "ENOTDIR"].includes(error.code)) unavailable = true;
        return;
      }
      if (names.has("signcert.der") && names.has("signpri.key")) {
        let der;
        try {
          const derPath = path.join(directory, names.get("signcert.der"));
          const keyPath = path.join(directory, names.get("signpri.key"));
          const derStat = await fileStat(derPath),
            keyStat = await fileStat(keyPath);
          if ((await fs.realpath(keyPath)) !== keyPath)
            throw new Error("INVALID_FILE");
          der = await readFile(derPath, derStat);
          if (!active) return;
          const cert = new X509Certificate(der);
          const info = metadata(cert, this.#now());
          const id = randomUUID();
          const item = { id, ...info };
          inventory.set(id, {
            derPath,
            keyPath,
            derStat,
            keyStat,
            fingerprint: cert.fingerprint256,
          });
          certificates.push(item);
        } catch {
          /* Incomplete or malformed certificates are not selectable. */
        } finally {
          der?.fill(0);
        }
      }
      for (const name of directories) {
        if (!active) break;
        if (depth >= maxDepth) {
          limited = true;
          break;
        }
        if (
          entries >= maxEntries ||
          certificates.length >= maxCertificates ||
          Date.now() >= deadline
        ) {
          limited = true;
          break;
        }
        const child = path.join(directory, name);
        try {
          if (
            (await fs.lstat(child)).isDirectory() &&
            (await fs.realpath(child)) === child
          )
            await walk(child, depth + 1);
        } catch {
          /* A directory may disappear during discovery. */
        }
      }
    };
    const traverse = async () => {
      for (const root of roots) {
        if (!active) break;
        try {
          if (!(await fs.lstat(root)).isDirectory()) continue;
          const canonical = await fs.realpath(root);
          await walk(canonical, 0);
        } catch (error) {
          if (!["ENOENT", "ENOTDIR"].includes(error.code)) unavailable = true;
        }
      }
    };
    let timeout;
    try {
      await Promise.race([
        traverse(),
        new Promise((resolve) => {
          timeout = setTimeout(() => {
            limited = true;
            active = false;
            resolve();
          }, maxMilliseconds);
        }),
      ]);
    } finally {
      active = false;
      clearTimeout(timeout);
    }
    if (version === this.#version) this.#entries = inventory;
    return { certificates, limited, unavailable };
  }
  async material(id) {
    const item = typeof id === "string" && this.#entries.get(id);
    if (!item) throw new Error("INVALID_CERTIFICATE");
    let der, key;
    try {
      der = await readFile(item.derPath, item.derStat);
      const cert = new X509Certificate(der);
      if (
        cert.fingerprint256 !== item.fingerprint ||
        metadata(cert, this.#now()).status !== "VALID"
      )
        throw new Error("INVALID_CERTIFICATE");
      key = await readFile(item.keyPath, item.keyStat);
      return { der, key };
    } catch (error) {
      der?.fill(0);
      key?.fill(0);
      throw error;
    }
  }
}
module.exports = { CertificateStore, defaultRoots };
