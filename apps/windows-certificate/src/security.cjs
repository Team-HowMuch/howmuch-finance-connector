"use strict";
const MAX_FILE = 128 * 1024;
function apiOrigin(value, local = false) {
  const u = new URL(value);
  if (u.username || u.password || u.search || u.hash || u.pathname !== "/")
    throw new Error("INVALID_ORIGIN");
  if (
    u.protocol !== "https:" &&
    !(
      local &&
      u.protocol === "http:" &&
      ["127.0.0.1", "[::1]"].includes(u.hostname)
    )
  )
    throw new Error("HTTPS_REQUIRED");
  return u.origin;
}
function uploadFields(value) {
  if (!value || typeof value !== "object" || Array.isArray(value))
    throw new Error("INVALID_INPUT");
  if (
    Object.keys(value).some(
      (k) =>
        !["password", "accountNumber", "accountPassword", "birthDate"].includes(
          k,
        ),
    )
  )
    throw new Error("INVALID_INPUT");
  if (
    typeof value.password !== "string" ||
    !value.password.length ||
    value.password.length > 256
  )
    throw new Error("INVALID_INPUT");
  const out = { password: value.password };
  for (const [key, pattern] of Object.entries({
    accountNumber: /^[0-9-]{1,30}$/,
    accountPassword: /^[0-9]{4,8}$/,
    birthDate: /^(?:[0-9]{6}|[0-9]{8})$/,
  })) {
    if (value[key] == null || value[key] === "") continue;
    if (typeof value[key] !== "string" || !pattern.test(value[key]))
      throw new Error("INVALID_INPUT");
    out[key] = value[key];
  }
  if (Boolean(out.accountNumber) !== Boolean(out.accountPassword))
    throw new Error("INVALID_INPUT");
  return out;
}
function validSender(event, window) {
  return Boolean(
    window &&
    !window.isDestroyed() &&
    event.sender === window.webContents &&
    event.senderFrame === window.webContents.mainFrame &&
    event.senderFrame.url === "howmuch-cert://app/index.html",
  );
}
module.exports = { MAX_FILE, apiOrigin, uploadFields, validSender };
