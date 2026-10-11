"use strict";
const { test } = require("node:test");
const assert = require("node:assert/strict");
const { removableVolumes, storageState } = require("../src/volumes.cjs");

test("macOS uses only mounted external volumes, including APFS volumes", async () => {
  const data = {
    AllDisksAndPartitions: [
      {
        DeviceIdentifier: "disk4",
        OSInternal: false,
        Partitions: [
          { DeviceIdentifier: "disk4s1", VolumeName: "Unmounted" },
          {
            DeviceIdentifier: "disk4s2",
            MountPoint: "/Volumes/USB",
            VolumeUUID: "public-fixture-id",
          },
        ],
      },
      {
        DeviceIdentifier: "disk5",
        APFSVolumes: [
          { DeviceIdentifier: "disk5s1", MountPoint: "/Volumes/External APFS" },
        ],
      },
      {
        DeviceIdentifier: "disk1",
        OSInternal: true,
        MountPoint: "/Volumes/Internal",
      },
      { DeviceIdentifier: "invalid", MountPoint: "/Volumes/../Users/private" },
    ],
  };
  const calls = [];
  const result = await removableVolumes("darwin", async (file, args, input) => {
    calls.push({ file, args, input });
    return file.endsWith("diskutil") ? "synthetic-plist" : JSON.stringify(data);
  });
  assert.deepEqual(calls[0], {
    file: "/usr/sbin/diskutil",
    args: ["list", "-plist", "external"],
    input: undefined,
  });
  assert.equal(calls[1].input, "synthetic-plist");
  assert.deepEqual(
    result.volumes.map((v) => v.root),
    ["/Volumes/External APFS", "/Volumes/USB"],
  );
  assert.equal(result.unavailable, false);
});
test("Windows queries removable media and USB bus disks without shell or policy bypass", async () => {
  const result = await removableVolumes("win32", async (file, args) => {
    assert.ok(file.endsWith("\\WindowsPowerShell\\v1.0\\powershell.exe"));
    assert.deepEqual(args.slice(0, 4), [
      "-NoLogo",
      "-NoProfile",
      "-NonInteractive",
      "-EncodedCommand",
    ]);
    const script = Buffer.from(args[4], "base64").toString("utf16le");
    assert.ok(
      script.includes("DriveType=2") &&
        script.includes("Get-Disk") &&
        script.includes("BusType -eq 'USB'") &&
        script.includes("Get-Partition"),
    );
    assert.ok(!script.includes("ExecutionPolicy"));
    return (
      "\uFEFF" +
      JSON.stringify({
        volumes: [
          { root: "E:\\", id: "removable" },
          { root: "F:\\", id: "usb-fixed-disk" },
          { root: "e:\\", id: "removable" },
          { root: "\\\\network\\share", id: "network" },
          { root: "F:\\private", id: "not-a-volume-root" },
        ],
        unavailable: false,
      })
    );
  });
  assert.deepEqual(result.volumes, [
    { root: "E:\\", id: "removable" },
    { root: "F:\\", id: "usb-fixed-disk" },
  ]);
  assert.equal(result.unavailable, false);
});
test("volume query errors are distinguishable from no connected media", async () => {
  const failed = await removableVolumes("darwin", async () => {
    throw new Error("private OS details");
  });
  assert.deepEqual(failed, { volumes: [], unavailable: true });
  assert.deepEqual(
    await removableVolumes("darwin", async () => "not-json"),
    failed,
  );
  const empty = await removableVolumes("win32", async () =>
    JSON.stringify({ volumes: [], unavailable: false }),
  );
  assert.deepEqual(empty, { volumes: [], unavailable: false });
});
test("renderer storage state is pathless and changes on replacement at the same mount", () => {
  const snapshot = {
    volumes: [{ root: "/Volumes/USB", id: "first-volume" }],
    unavailable: false,
  };
  const first = storageState(snapshot);
  assert.deepEqual(Object.keys(first).sort(), [
    "deviceCount",
    "revision",
    "unavailable",
  ]);
  assert.equal(first.deviceCount, 1);
  assert.match(first.revision, /^[a-f\d]{64}$/);
  assert.ok(
    !JSON.stringify(first).includes("/Volumes/") &&
      !JSON.stringify(first).includes("first-volume"),
  );
  assert.equal(storageState(snapshot).revision, first.revision);
  assert.notEqual(
    storageState({
      ...snapshot,
      volumes: [{ root: "/Volumes/USB", id: "replacement" }],
    }).revision,
    first.revision,
  );
  assert.notEqual(
    storageState({ volumes: [], unavailable: false }).revision,
    first.revision,
  );
});
