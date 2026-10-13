"use strict";
const fs = require("node:fs");

function releaseTarget(eventName, event, version) {
  let channel;
  if (eventName === "workflow_dispatch") {
    channel = event.inputs?.target_environment;
    if (!["dev", "prod"].includes(channel))
      throw new Error("Choose dev or prod for target_environment.");
  } else if (eventName === "release") {
    const release = event.release;
    if (release?.draft || release?.tag_name !== `certificate-v${version}`)
      throw new Error("Release tag must match certificate-v<package.json version>.");
    if (Boolean(release.prerelease) !== version.includes("-"))
      throw new Error("Prerelease flag must match the package version prerelease suffix.");
    channel = release.prerelease ? "dev" : "prod";
  } else {
    throw new Error("Unsupported installer event.");
  }
  return { channel, environment: `certificate-windows-${channel}` };
}

if (require.main === module) {
  const event = JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, "utf8"));
  const { version } = require("../package.json");
  const target = releaseTarget(process.env.GITHUB_EVENT_NAME, event, version);
  fs.appendFileSync(process.env.GITHUB_OUTPUT,
    `channel=${target.channel}\nenvironment=${target.environment}\n`);
}
module.exports = { releaseTarget };
