"use strict";
const { execFile } = require("node:child_process");
const path = require("node:path");
const { createHash } = require("node:crypto");

// Fixed, read-only OS queries. No renderer text is interpolated into a command.
const WINDOWS_QUERY = `
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)
$roots = @{}
$failed = $false
try {
  foreach ($volume in @(Get-CimInstance Win32_LogicalDisk -Filter 'DriveType=2')) {
    $roots[$volume.DeviceID + '\\'] = [string]$volume.VolumeSerialNumber
  }
} catch { $failed = $true }
try {
  foreach ($disk in @(Get-Disk | Where-Object { $_.BusType -eq 'USB' })) {
    foreach ($partition in @(Get-Partition -DiskNumber $disk.Number)) {
      if ([string]$partition.DriveLetter -match '^[A-Za-z]$') {
        $roots[[string]$partition.DriveLetter + ':\\'] = [string]$disk.UniqueId + ':' + [string]$partition.PartitionNumber
      }
    }
  }
} catch { $failed = $true }
$volumes = @($roots.GetEnumerator() | ForEach-Object { @{ root = $_.Key; id = $_.Value } })
ConvertTo-Json -Depth 4 -Compress -InputObject @{ volumes = $volumes; unavailable = $failed }
`;

function run(file, args, input) {
  return new Promise((resolve, reject) => {
    const child = execFile(
      file,
      args,
      {
        timeout: 5000,
        maxBuffer: 256 * 1024,
        encoding: "utf8",
        windowsHide: true,
      },
      (error, stdout) =>
        error ? reject(new Error("VOLUME_QUERY_FAILED")) : resolve(stdout),
    );
    child.stdin.on("error", () => {});
    child.stdin.end(input);
  });
}
function normalize(volumes, platform) {
  const unique = new Map();
  for (const volume of volumes.slice(0, 64)) {
    if (typeof volume?.root !== "string" || typeof volume?.id !== "string")
      continue;
    const root = volume.root;
    const valid =
      platform === "win32"
        ? /^[A-Za-z]:\\$/.test(root)
        : root.startsWith("/Volumes/") &&
          !root.includes("\0") &&
          path.posix.normalize(root) === root &&
          root !== "/Volumes/";
    if (valid)
      unique.set(
        platform === "win32" ? root.toUpperCase() : root,
        volume.id.slice(0, 256),
      );
  }
  return [...unique]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([root, id]) => ({ root, id }));
}
function macVolumes(data) {
  const volumes = [];
  function visit(item, disk, depth) {
    if (!item || depth > 6 || volumes.length >= 64) return;
    if (item.OSInternal === true) return;
    if (typeof item.MountPoint === "string")
      volumes.push({
        root: item.MountPoint,
        id: `${disk}:${item.DeviceIdentifier || ""}:${item.VolumeUUID || item.DiskUUID || ""}`,
      });
    for (const key of ["Partitions", "APFSVolumes"]) {
      if (Array.isArray(item[key]))
        for (const child of item[key].slice(0, 64))
          visit(child, disk, depth + 1);
    }
  }
  if (!Array.isArray(data.AllDisksAndPartitions))
    throw new Error("INVALID_VOLUMES");
  for (const disk of data.AllDisksAndPartitions.slice(0, 64))
    visit(disk, disk.DeviceIdentifier || "", 0);
  return volumes;
}
async function removableVolumes(platform = process.platform, execute = run) {
  try {
    if (platform === "darwin") {
      const plist = await execute("/usr/sbin/diskutil", [
        "list",
        "-plist",
        "external",
      ]);
      const json = await execute(
        "/usr/bin/plutil",
        ["-convert", "json", "-o", "-", "--", "-"],
        plist,
      );
      return {
        volumes: normalize(macVolumes(JSON.parse(json)), platform),
        unavailable: false,
      };
    }
    if (platform === "win32") {
      const command = path.win32.join(
        process.env.SystemRoot || "C:\\Windows",
        "System32",
        "WindowsPowerShell",
        "v1.0",
        "powershell.exe",
      );
      const json = await execute(command, [
        "-NoLogo",
        "-NoProfile",
        "-NonInteractive",
        "-EncodedCommand",
        Buffer.from(WINDOWS_QUERY, "utf16le").toString("base64"),
      ]);
      const result = JSON.parse(json.replace(/^\uFEFF/, ""));
      if (!Array.isArray(result.volumes)) throw new Error("INVALID_VOLUMES");
      return {
        volumes: normalize(result.volumes, platform),
        unavailable: result.unavailable !== false,
      };
    }
    return { volumes: [], unavailable: false };
  } catch {
    return { volumes: [], unavailable: true };
  }
}
function storageState(snapshot) {
  return {
    revision: createHash("sha256")
      .update(JSON.stringify(snapshot.volumes))
      .digest("hex"),
    deviceCount: snapshot.volumes.length,
    unavailable: snapshot.unavailable,
  };
}
module.exports = { removableVolumes, storageState };
