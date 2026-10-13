"use strict";
const fs = require("node:fs");
const path = require("node:path");
const { configure } = require("./configure.cjs");
const { apiOrigin } = require("../src/security.cjs");
module.exports = async (context) => {
  if (process.env.HOWMUCH_CERTIFICATE_API_ORIGIN !== undefined)
    configure(process.env.HOWMUCH_CERTIFICATE_API_ORIGIN);
  const config = JSON.parse(
    fs.readFileSync(path.join(__dirname, "..", "release-config.json"), "utf8"),
  );
  apiOrigin(config.apiOrigin);
  const signing = process.env.HOWMUCH_REQUIRE_SIGNING;
  if (signing !== undefined && !["true", "false"].includes(signing))
    throw new Error("HOWMUCH_REQUIRE_SIGNING must be true or false.");
  if (context.electronPlatformName === "win32" && signing === "true") {
    if (!process.env.CSC_LINK)
      throw new Error("A Windows signing certificate is required for a release build.");
    context.packager.platformSpecificBuildOptions.forceCodeSigning = true;
  }
  if (context.electronPlatformName === "darwin") {
    // Builder 26 uses NFD helper filenames; Electron resolves them from CFBundleName.
    const options = context.packager.platformSpecificBuildOptions;
    options.extendInfo = {
      ...options.extendInfo,
      CFBundleName: context.packager.appInfo.sanitizedProductName,
    };
  }
};
