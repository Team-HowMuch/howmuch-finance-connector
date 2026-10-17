"use strict";
// Local-only launcher: verifies the real backend pairing screen without printing QR capabilities.
const { app, BrowserWindow } = require("electron");
if (process.env.HOWMUCH_CERTIFICATE_LOCAL !== "1") {
  throw new Error("This launcher requires the explicit local development mode");
}
require("../src/main.cjs");
app.whenReady().then(async () => {
  const deadline = Date.now() + 15000;
  while (Date.now() < deadline) {
    const window = BrowserWindow.getAllWindows()[0];
    if (window) {
      const ready = await window.webContents.executeJavaScript(
        'Boolean(document.getElementById("pairingQr") && !document.getElementById("pairingQr").hidden)',
      ).catch(() => false);
      if (ready) {
        console.log("LOCAL_PC_PAIRING_QR_READY");
        return;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 150));
  }
  console.error("Local PC pairing did not become ready within 15 seconds");
});
