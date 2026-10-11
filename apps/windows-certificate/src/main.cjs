"use strict";
const {
  app,
  BrowserWindow,
  Menu,
  dialog,
  ipcMain,
  protocol,
  net,
  session,
} = require("electron");
const path = require("node:path");
const fs = require("node:fs/promises");
const { pathToFileURL } = require("node:url");
const QRCode = require("qrcode");
const { CertificateClient, userErrorMessage } = require("./client.cjs");
const { uploadFields, validSender } = require("./security.cjs");
const { CertificateStore, defaultRoots } = require("./certificates.cjs");
const { removableVolumes, storageState } = require("./volumes.cjs");
const DEFAULT_API_ORIGIN = "https://dev.howmuch.wars.p-e.kr";
app.setName("얼마 인증서 가져오기");
// Use software rendering for Windows compatibility in this small local form.
if (process.platform === "win32") app.disableHardwareAcceleration();
protocol.registerSchemesAsPrivileged([
  {
    scheme: "howmuch-cert",
    privileges: { standard: true, secure: true, supportFetchAPI: true },
  },
]);
app.enableSandbox();
let window,
  client,
  busy = false,
  discovering = false;
const certificates = new CertificateStore();
let volumeCache, volumeQuery;
async function volumes() {
  if (volumeCache && volumeCache.until > Date.now())
    return volumeCache.snapshot;
  if (!volumeQuery)
    volumeQuery = removableVolumes()
      .then((snapshot) => {
        volumeCache = { snapshot, until: Date.now() + 1000 };
        return snapshot;
      })
      .finally(() => {
        volumeQuery = undefined;
      });
  return volumeQuery;
}
function trusted(handler) {
  return async (event, ...args) => {
    if (!validSender(event, window)) throw new Error("FORBIDDEN");
    try {
      return await handler(...args);
    } catch (error) {
      throw new Error(
        userErrorMessage(error.message) || /^API_\d{3}$/.test(error.message)
          ? error.message : "OPERATION_FAILED",
      );
    }
  };
}
async function discover(source) {
  if (
    busy ||
    discovering ||
    !["auto", "computer", "usb", "folder"].includes(source)
  )
    throw new Error("INVALID_INPUT");
  discovering = true;
  try {
    const snapshot = await volumes();
    const storage = storageState(snapshot);
    if (source === "auto")
      source = snapshot.volumes.length ? "usb" : "computer";
    let roots =
      source === "usb"
        ? snapshot.volumes.flatMap(({ root }) => [
            path.join(root, "NPKI"),
            root,
          ])
        : defaultRoots();
    if (source === "folder") {
      const result = await dialog.showOpenDialog(window, {
        title: "인증서가 저장된 폴더 선택",
        buttonLabel: "인증서 찾기",
        properties: ["openDirectory"],
      });
      if (result.canceled) return null;
      roots = result.filePaths.slice(0, 1);
    }
    const result = await certificates.scan(roots);
    return {
      ...result,
      source,
      storage,
      unavailable:
        result.unavailable || (source === "usb" && storage.unavailable),
    };
  } finally {
    discovering = false;
  }
}
app.whenReady().then(async () => {
  const assets = new Map([
    ["/index.html", "text/html"],
    ["/renderer.js", "text/javascript"],
    ["/style.css", "text/css"],
    ["/assets/logo.png", "image/png"],
    ["/assets/fonts/Pretendard-Regular.otf", "font/otf"],
    ["/assets/fonts/Pretendard-SemiBold.otf", "font/otf"],
    ["/assets/fonts/Pretendard-Bold.otf", "font/otf"],
  ]);
  session
    .fromPartition("certificate-temporary")
    .protocol.handle("howmuch-cert", async (request) => {
      const url = new URL(request.url);
      if (
        url.hostname !== "app" ||
        !assets.has(url.pathname) ||
        url.search ||
        url.hash
      )
        return new Response(null, { status: 404 });
      const response = await net.fetch(
        pathToFileURL(path.join(__dirname, url.pathname.slice(1))).toString(),
      );
      return new Response(response.body, {
        headers: {
          "Content-Type": assets.get(url.pathname),
          "Content-Security-Policy":
            "default-src 'none'; script-src 'self'; style-src 'self'; font-src 'self'; img-src 'self' data:; connect-src 'none'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'",
        },
      });
    });
  let config;
  try {
    config = JSON.parse(
      await fs.readFile(
        path.join(app.getAppPath(), "release-config.json"),
        "utf8",
      ),
    );
  } catch {
    config = { apiOrigin: DEFAULT_API_ORIGIN };
  }
  const local =
    !app.isPackaged && process.env.HOWMUCH_CERTIFICATE_LOCAL === "1";
  const origin =
    !app.isPackaged && process.env.HOWMUCH_CERTIFICATE_API_ORIGIN
      ? process.env.HOWMUCH_CERTIFICATE_API_ORIGIN
      : config.apiOrigin;
  try {
    client = new CertificateClient(origin, fetch, local);
  } catch {
    /* init returns a sanitized configuration error */
  }
  const icon = path.join(__dirname, "assets", "icon.png");
  if (process.platform === "darwin") app.dock.setIcon(icon);
  Menu.setApplicationMenu(null);
  window = new BrowserWindow({
    title: "인증서 가져오기",
    frame: false,
    minimizable: false,
    maximizable: false,
    fullscreenable: false,
    width: 900,
    height: 780,
    minWidth: 640,
    minHeight: 680,
    autoHideMenuBar: true,
    icon,
    webPreferences: {
      preload: path.join(__dirname, "preload.cjs"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      devTools: !app.isPackaged,
      partition: "certificate-temporary",
    },
  });
  window.webContents.session.setPermissionRequestHandler(
    (_contents, _permission, callback) => callback(false),
  );
  window.webContents.session.setPermissionCheckHandler(() => false);
  window.webContents.setWindowOpenHandler(() => ({ action: "deny" }));
  window.webContents.on("will-navigate", (event) => event.preventDefault());
  window.webContents.on("will-attach-webview", (event) =>
    event.preventDefault(),
  );
  window.webContents.on("render-process-gone", () => {
    certificates.clear();
    client?.clear();
  });
  window.on("closed", () => {
    certificates.clear();
    client?.cancel().catch(() => {});
    window = undefined;
  });
  ipcMain.handle(
    "certificate:init",
    trusted(async () => {
      if (!client) throw new Error("CONFIG_REQUIRED");
      return client.institutions();
    }),
  );
  ipcMain.handle("certificate:close", trusted(() => window?.close()));
  ipcMain.handle("certificate:discover", trusted(discover));
  ipcMain.handle("certificate:failure-message", trusted(async (value) => {
    if (typeof value !== "string" || value.length > 256) return null;
    const code = value.match(/(?:^|:\s*)([A-Z][A-Z0-9_]+)$/)?.[1];
    return userErrorMessage(code);
  }));
  ipcMain.handle(
    "certificate:storage",
    trusted(async () => storageState(await volumes())),
  );
  ipcMain.handle(
    "certificate:submit",
    trusted(async (organization, certificateId, input) => {
      if (busy || discovering || !client) throw new Error("INVALID_INPUT");
      const fields = uploadFields(input);
      busy = true;
      let material;
      try {
        material = await certificates.material(certificateId);
        await client.create(organization);
        return await client.upload(fields, material.der, material.key);
      } finally {
        material?.der.fill(0);
        material?.key.fill(0);
        certificates.clear();
        busy = false;
      }
    }),
  );
  ipcMain.handle(
    "certificate:status",
    trusted(() => client.status()),
  );
  ipcMain.handle(
    "certificate:qr",
    trusted(async () => {
      const qr = await client.qr();
      return {
        image: await QRCode.toDataURL(
          JSON.stringify({
            v: 1,
            type: "HOWMUCH_CERTIFICATE_CLAIM",
            sessionId: qr.sessionId,
            qrToken: qr.qrToken,
          }),
          { width: 256, margin: 2 },
        ),
        expiresAt: qr.expiresAt,
      };
    }),
  );
  ipcMain.handle(
    "certificate:claim",
    trusted(() => client.claim()),
  );
  ipcMain.handle(
    "certificate:reject",
    trusted((id) => client.reject(id)),
  );
  ipcMain.handle(
    "certificate:cancel",
    trusted(async () => {
      if (busy || discovering) throw new Error("BUSY");
      certificates.clear();
      await client.cancel();
    }),
  );
  await window.loadURL("howmuch-cert://app/index.html");
});
app.on("window-all-closed", () => app.quit());
