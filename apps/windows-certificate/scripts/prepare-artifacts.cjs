"use strict";
const fs = require("node:fs");
const path = require("node:path");
const { createHash } = require("node:crypto");

async function prepareArtifact({ platform, arch, channel, version, dist = "dist" }) {
  if (!["win", "mac"].includes(platform) || !["dev", "prod"].includes(channel) ||
      !["arm64", "x64"].includes(arch) || (platform === "win" && arch !== "x64") ||
      !/^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version))
    throw new Error("Invalid installer platform, architecture, channel or version.");
  const suffix = platform === "win" ? "-Setup.exe" : ".dmg";
  const source = path.join(dist, `얼마-인증서-가져오기-${version}-${arch}${suffix}`);
  const name = `얼마-인증서-가져오기-${version}-${channel}-${arch}${suffix}`;
  const output = path.join(dist, "release", name);
  const hash = createHash("sha256");
  await fs.promises.mkdir(path.dirname(output), { recursive: true });
  await fs.promises.copyFile(source, output);
  for await (const chunk of fs.createReadStream(output)) hash.update(chunk);
  await fs.promises.writeFile(`${output}.sha256`, `${hash.digest("hex")}  ${name}\n`);
  return output;
}

if (require.main === module) {
  prepareArtifact({
    platform: process.argv[2],
    arch: process.argv[3],
    channel: process.env.CERTIFICATE_CHANNEL,
    version: require("../package.json").version,
  }).then(() => console.log("Installer and SHA-256 prepared."))
    .catch((error) => { console.error(error.message); process.exitCode = 1; });
}
module.exports = { prepareArtifact };
