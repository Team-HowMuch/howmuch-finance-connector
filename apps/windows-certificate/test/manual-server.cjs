"use strict";

const fs = require("node:fs");
const http = require("node:http");
const path = require("node:path");

const apiOrigin = "http://127.0.0.1:18081";
const port = Number(process.env.HOWMUCH_CERTIFICATE_TEST_PORT || 18082);
const staticFiles = new Map([
  ["/test/vendor/jsqr.js", [path.join(__dirname, "../node_modules/jsqr/dist/jsQR.js"), "text/javascript; charset=utf-8"]],
  ["/", [path.join(__dirname, "manual.html"), "text/html; charset=utf-8"]],
  ["/manual.html", [path.join(__dirname, "manual.html"), "text/html; charset=utf-8"]],
  ["/src/style.css", [path.join(__dirname, "../src/style.css"), "text/css; charset=utf-8"]],
  ["/src/assets/logo.png", [path.join(__dirname, "../src/assets/logo.png"), "image/png"]],
  ["/src/assets/fonts/Pretendard-Regular.otf", [path.join(__dirname, "../src/assets/fonts/Pretendard-Regular.otf"), "font/otf"]],
  ["/src/assets/fonts/Pretendard-SemiBold.otf", [path.join(__dirname, "../src/assets/fonts/Pretendard-SemiBold.otf"), "font/otf"]],
  ["/src/assets/fonts/Pretendard-Bold.otf", [path.join(__dirname, "../src/assets/fonts/Pretendard-Bold.otf"), "font/otf"]],
]);

function respond(res, status, value) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(JSON.stringify(value));
}

async function readJsonBody(req, maxBytes = 4096) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > maxBytes) throw Object.assign(new Error("REQUEST_TOO_LARGE"), { status: 413 });
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw Object.assign(new Error("INVALID_REQUEST"), { status: 400 });
  }
}

async function proxyLogin(req, res) {
  if (req.method !== "POST") return respond(res, 405, { success: false, error: { code: "METHOD_NOT_ALLOWED" } });
  if (!/^application\/json(?:\s*;|$)/i.test(req.headers["content-type"] || ""))
    return respond(res, 415, { success: false, error: { code: "UNSUPPORTED_MEDIA_TYPE" } });

  let body;
  try {
    body = await readJsonBody(req);
  } catch (error) {
    return respond(res, error.status || 400, { success: false, error: { code: error.message || "INVALID_REQUEST" } });
  }
  if (!body || typeof body.phoneNumber !== "string" || body.phoneNumber.length > 32 ||
      typeof body.pin !== "string" || !/^\d{6}$/.test(body.pin))
    return respond(res, 400, { success: false, error: { code: "INVALID_REQUEST" } });

  try {
    const upstream = await fetch(`${apiOrigin}/api/v2/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ phoneNumber: body.phoneNumber.trim(), pin: body.pin }),
      redirect: "error",
      signal: AbortSignal.timeout(10_000),
    });
    let payload;
    try {
      payload = await upstream.json();
    } catch {
      return respond(res, 502, { success: false, error: { code: "INVALID_UPSTREAM_RESPONSE" } });
    }

    // The login response also contains a request token. The QR test only needs the
    // access token, so deliberately keep every other upstream field out of the page.
    if (upstream.ok && payload?.success === true && typeof payload.data?.accessToken === "string") {
      return respond(res, upstream.status, { success: true, data: { accessToken: payload.data.accessToken } });
    }
    const code = typeof payload?.error?.code === "string" && /^[A-Z0-9_-]{1,80}$/.test(payload.error.code)
      ? payload.error.code
      : "LOGIN_FAILED";
    return respond(res, upstream.status, { success: false, error: { code } });
  } catch {
    return respond(res, 502, { success: false, error: { code: "LOCAL_API_UNAVAILABLE" } });
  }
}

async function proxyApi(req, res, pathname, query) {
  if (pathname === "/api/v2/auth/login") return proxyLogin(req, res);
  const uuid = "[0-9a-f-]{36}";
  const routes = [
    [new RegExp(`^/api/v2/certificate-pairings/${uuid}/claim$`, "i"), ["POST"], "claim"],
    [new RegExp(`^/api/v2/certificate-pairings/${uuid}/approval$`, "i"), ["POST"], "approval"],
    [new RegExp(`^/api/v2/certificate-pairings/${uuid}$`, "i"), ["GET", "DELETE"], "pairing"],
    [new RegExp(`^/api/v2/certificate-claims/${uuid}/${uuid}/decision$`, "i"), ["POST"], "decision"],
    [new RegExp(`^/api/v2/certificate-claims/${uuid}/${uuid}$`, "i"), ["GET"], "decisionStatus"],
    [/^\/api\/v2\/accounts\/connections$/, ["GET"], "connections"],
    [/^\/api\/v2\/accounts\/connections\/[1-9][0-9]*\/accounts$/, ["GET"], "accounts"],
  ];
  const route = routes.find(([pattern]) => pattern.test(pathname));
  if (!route) return respond(res, 404, { success: false, error: { code: "NOT_FOUND" } });
  if (!route[1].includes(req.method))
    return respond(res, 405, { success: false, error: { code: "METHOD_NOT_ALLOWED" } });
  let querySuffix = "";
  if (route[2] === "accounts") {
    const organization = query.get("organization");
    const birthDate = query.get("birthDate");
    if (!/^[0-9]{4}$/.test(organization || "") || (birthDate && !/^[0-9]{8}$/.test(birthDate)))
      return respond(res, 400, { success: false, error: { code: "INVALID_REQUEST" } });
    const allowed = new URLSearchParams({ organization });
    if (birthDate) allowed.set("birthDate", birthDate);
    querySuffix = `?${allowed}`;
  }
  const authorization = req.headers.authorization;
  if (typeof authorization !== "string" || authorization.length > 4096 || !/^Bearer\s+\S+$/i.test(authorization))
    return respond(res, 401, { success: false, error: { code: "UNAUTHORIZED" } });
  let body;
  if (route[2] === "claim" || route[2] === "decision") {
    try { body = await readJsonBody(req, 2048); }
    catch (error) { return respond(res, error.status || 400, { success: false, error: { code: "INVALID_REQUEST" } }); }
    if (route[2] === "claim") {
      if (typeof body?.qrToken !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(body.qrToken))
        return respond(res, 400, { success: false, error: { code: "INVALID_REQUEST" } });
      body = { qrToken: body.qrToken };
    } else {
      if (typeof body?.approved !== "boolean")
        return respond(res, 400, { success: false, error: { code: "INVALID_REQUEST" } });
      body = { approved: body.approved };
    }
  }

  try {
    const upstream = await fetch(`${apiOrigin}${pathname}${querySuffix}`, {
      method: req.method,
      headers: {
        Authorization: authorization,
        ...(body ? { "Content-Type": "application/json" } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
      redirect: "error",
      signal: AbortSignal.timeout(10_000),
    });
    const responseBody = await upstream.text();
    if (responseBody.length > 1_000_000)
      return respond(res, 502, { success: false, error: { code: "RESPONSE_TOO_LARGE" } });
    res.writeHead(upstream.status, {
      "Content-Type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
      "Cache-Control": "no-store",
      "X-Content-Type-Options": "nosniff",
    });
    res.end(responseBody);
  } catch {
    respond(res, 502, { success: false, error: { code: "LOCAL_API_UNAVAILABLE" } });
  }
}

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, "http://127.0.0.1");
  const pathname = url.pathname;
  if (pathname.startsWith("/api/")) return proxyApi(req, res, pathname, url.searchParams);
  const entry = staticFiles.get(pathname);
  if (!entry || req.method !== "GET") {
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8", "X-Content-Type-Options": "nosniff" });
    res.end("Not found");
    return;
  }
  res.writeHead(200, {
    "Content-Type": entry[1],
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
    "Referrer-Policy": "no-referrer",
  });
  fs.createReadStream(entry[0]).pipe(res);
});

server.listen(port, "127.0.0.1", () => {
  process.stdout.write(`QR API test page: http://127.0.0.1:${port}\n`);
});
