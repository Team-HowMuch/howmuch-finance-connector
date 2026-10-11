"use strict";
const fs = require("node:fs");
const path = require("node:path");
const { apiOrigin } = require("../src/security.cjs");
module.exports = async (context) => {
  const config = JSON.parse(
    fs.readFileSync(path.join(__dirname, "..", "release-config.json"), "utf8"),
  );
  apiOrigin(config.apiOrigin);
  if (process.env.HOWMUCH_REQUIRE_SIGNING === "true" && !process.env.CSC_LINK)
    throw new Error(
      "A Windows signing certificate is required for a release build.",
    );
  if (context.electronPlatformName === "darwin") {
    // Builder 26 uses NFD helper filenames; Electron resolves them from CFBundleName.
    const options = context.packager.platformSpecificBuildOptions;
    options.extendInfo = {
      ...options.extendInfo,
      CFBundleName: context.packager.appInfo.sanitizedProductName,
    };
  }
};
