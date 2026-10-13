"use strict";
const fs = require("node:fs");
const path = require("node:path");
const { apiOrigin } = require("../src/security.cjs");
const CONFIG_PATH = path.join(__dirname, "..", "release-config.json");

function configure(value, output = CONFIG_PATH) {
  if (!value)
    throw new Error("Set HOWMUCH_CERTIFICATE_API_ORIGIN or pass an HTTPS API origin.");
  const origin = apiOrigin(value);
  fs.writeFileSync(output, JSON.stringify({ apiOrigin: origin }, null, 2) + "\n");
  return origin;
}

if (require.main === module) {
  configure(process.argv[2] ?? process.env.HOWMUCH_CERTIFICATE_API_ORIGIN);
  console.log("HTTPS API origin configured for this build.");
}
module.exports = { configure, CONFIG_PATH };
