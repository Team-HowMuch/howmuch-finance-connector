"use strict";
const fs = require("node:fs");
const path = require("node:path");
const { apiOrigin } = require("../src/security.cjs");
const origin = apiOrigin(process.argv[2]);
fs.writeFileSync(
  path.join(__dirname, "..", "release-config.json"),
  JSON.stringify({ apiOrigin: origin }, null, 2) + "\n",
);
console.log("HTTPS API origin configured for this build.");
