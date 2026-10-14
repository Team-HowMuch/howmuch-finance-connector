"use strict";
const { contextBridge, ipcRenderer } = require("electron");
contextBridge.exposeInMainWorld(
  "certificate",
  Object.freeze({
    close: () => ipcRenderer.invoke("certificate:close"),
    pair: () => ipcRenderer.invoke("certificate:pair"),
    pairStatus: () => ipcRenderer.invoke("certificate:pair-status"),
    appVersion: () => ipcRenderer.invoke("certificate:app-version"),
    init: () => ipcRenderer.invoke("certificate:init"),
    discover: (source) => ipcRenderer.invoke("certificate:discover", source),
    storage: () => ipcRenderer.invoke("certificate:storage"),
    submit: (organization, certificateId, fields) =>
      ipcRenderer.invoke(
        "certificate:submit",
        organization,
        certificateId,
        fields,
      ),
    status: () => ipcRenderer.invoke("certificate:status"),
    failureMessage: (code) =>
      ipcRenderer.invoke("certificate:failure-message", code),
    qr: () => ipcRenderer.invoke("certificate:qr"),
    claim: () => ipcRenderer.invoke("certificate:claim"),
    reject: (id) => ipcRenderer.invoke("certificate:reject", id),
    retry: () => ipcRenderer.invoke("certificate:retry"),
    cancel: () => ipcRenderer.invoke("certificate:cancel"),
  }),
);
