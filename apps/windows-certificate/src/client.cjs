"use strict";
const { apiOrigin } = require("./security.cjs");
const userErrors = require("./user-errors.json");
function userErrorMessage(code) {
  return typeof code === "string" && Object.hasOwn(userErrors, code)
    ? userErrors[code]
    : null;
}
class CertificateClient {
  #origin;
  #fetch;
  #session;
  #pairing;
  constructor(origin, fetchImpl = fetch, local = false) {
    this.#origin = apiOrigin(origin, local);
    this.#fetch = fetchImpl;
  }
  async #request(method, suffix = "", body, upload = false, pairing = false) {
    const headers = { "Content-Type": "application/json" };
    const capability = pairing
      ? this.#pairing?.pcCapability
      : this.#session?.pcCapability;
    if (capability) headers["X-PC-Capability"] = capability;
    if (upload) headers["X-Upload-Capability"] = this.#session.uploadCapability;
    const response = await this.#fetch(
      `${this.#origin}/api/v2/${pairing ? "certificate-pairings" : "certificate-registrations"}${suffix}`,
      {
        method,
        headers,
        body: body ? JSON.stringify(body) : undefined,
        redirect: "error",
        signal: AbortSignal.timeout(upload ? 30_000 : 10_000),
      },
    );
    let size = 0;
    const chunks = [];
    for await (const chunk of response.body || []) {
      size += chunk.length;
      if (size > 1024 * 1024) throw new Error("RESPONSE_TOO_LARGE");
      chunks.push(chunk);
    }
    let parsed;
    try {
      parsed = JSON.parse(Buffer.concat(chunks).toString("utf8"));
    } catch {
      throw new Error(
        response.ok ? "INVALID_RESPONSE" : `API_${response.status}`,
      );
    }
    if (!response.ok || !parsed.success) {
      const code = parsed.error?.code;
      // Only our known codes cross IPC. Never forward server/provider messages or details.
      throw new Error(userErrorMessage(code) ? code : `API_${response.status}`);
    }
    return parsed.data;
  }
  async institutions() {
    const list = await this.#request("GET", "/institutions");
    if (!Array.isArray(list)) throw new Error("INVALID_RESPONSE");
    return list;
  }
  async startPairing() {
    if (this.#pairing || this.#session) throw new Error("SESSION_EXISTS");
    const p = await this.#request("POST", "", {}, false, true);
    if (
      !/^[0-9a-f-]{36}$/.test(p.id) ||
      !/^[\w-]{43}$/.test(p.pcCapability) ||
      !/^[\w-]{43}$/.test(p.qrToken) ||
      p.status !== "WAITING_FOR_MOBILE" ||
      !Number.isFinite(Date.parse(`${p.expiresAt}Z`)) ||
      !Number.isFinite(Date.parse(`${p.qrExpiresAt}Z`))
    )
      throw new Error("INVALID_RESPONSE");
    this.#pairing = p;
    return { pairingId: p.id, qrToken: p.qrToken, expiresAt: p.qrExpiresAt };
  }
  async pairingStatus() {
    if (!this.#pairing) throw new Error("NO_SESSION");
    const p = await this.#request(
      "GET",
      `/${this.#pairing.id}/pc`,
      undefined,
      false,
      true,
    );
    if (
      p.id !== this.#pairing.id ||
      ![
        "WAITING_FOR_MOBILE",
        "PAIRED",
        "EXPIRED",
        "CANCELLED",
        "CREATED",
        "SUBMITTED",
        "REGISTERING",
        "READY_FOR_CLAIM",
        "CLAIM_RESERVED",
        "BOUND",
        "FAILED",
        "UNKNOWN",
      ].includes(p.status)
    )
      throw new Error("INVALID_RESPONSE");
    this.#pairing.status = p.status;
    return { status: p.status, expiresAt: p.expiresAt };
  }
  async create(organization) {
    if (this.#session) throw new Error("SESSION_EXISTS");
    if (!/^[0-9]{4}$/.test(organization)) throw new Error("INVALID_INPUT");
    if (this.#pairing && !["PAIRED", "FAILED"].includes(this.#pairing.status))
      throw new Error("CERTIFICATE_CONFLICT");
    const s = this.#pairing
      ? await this.#request(
          "POST",
          `/${this.#pairing.id}/registrations`,
          { organization },
          false,
          true,
        )
      : await this.#request("POST", "", { organization });
    if (
      !/^[0-9a-f-]{36}$/.test(s.id) ||
      !/^[\w-]{43}$/.test(s.pcCapability) ||
      !/^[\w-]{43}$/.test(s.uploadCapability)
    )
      throw new Error("INVALID_RESPONSE");
    this.#session = s;
    return this.#safe(s);
  }
  #safe(s) {
    if (this.#session?.id === s.id) this.#session.status = s.status;
    const legacyCodes = {
      REGISTRATION_REJECTED: "BANK_CONNECTION_FAILED",
      REGISTRATION_UNCERTAIN: "CERTIFICATE_REGISTRATION_UNCERTAIN",
      ADDITIONAL_AUTH_REQUIRED: "CODEF_ADDITIONAL_AUTH_REQUIRED",
      UPLOAD_EXPIRED: "CERTIFICATE_EXPIRED",
      PROVIDER_UNAVAILABLE: "CODEF_UPSTREAM_UNAVAILABLE",
      CONTRACT_CONFLICT: "CERTIFICATE_CONFLICT",
    };
    const code = Object.hasOwn(legacyCodes, s.failureCode)
      ? legacyCodes[s.failureCode]
      : s.failureCode;
    const failureCode = code
      ? userErrorMessage(code)
        ? code
        : "CODEF_UNMAPPED_ERROR"
      : null;
    return {
      id: s.id,
      status: s.status,
      expiresAt: s.expiresAt,
      failureCode,
      failureMessage: userErrorMessage(failureCode),
    };
  }
  #id() {
    if (!this.#session) throw new Error("NO_SESSION");
    return this.#session.id;
  }
  async upload(fields, der, key) {
    const result = await this.#request(
      "POST",
      `/${this.#id()}/upload`,
      {
        ...fields,
        derFile: der.toString("base64"),
        keyFile: key.toString("base64"),
      },
      true,
    );
    this.#session.uploadCapability = undefined;
    return this.#safe(result);
  }
  async status() {
    return this.#safe(await this.#request("GET", `/${this.#id()}`));
  }
  async qr() {
    if (this.#pairing) throw new Error("CERTIFICATE_CONFLICT");
    return this.#request("POST", `/${this.#id()}/qr`);
  }
  async claim() {
    return this.#request("GET", `/${this.#id()}/claim`);
  }
  async reject(claimId) {
    if (!/^[0-9a-f-]{36}$/.test(claimId)) throw new Error("INVALID_INPUT");
    return this.#request("DELETE", `/${this.#id()}/claims/${claimId}`);
  }
  prepareRetry() {
    if (!this.#pairing || this.#session?.status !== "FAILED")
      throw new Error("CERTIFICATE_CONFLICT");
    // Only a confirmed failure can be detached. The server revalidates it under lock.
    this.#session = undefined;
    this.#pairing.status = "FAILED";
  }
  async cancel() {
    try {
      if (this.#session && this.#session.status !== "BOUND")
        await this.#request("DELETE", `/${this.#id()}`);
    } catch (error) {
      if (!["API_404", "CERTIFICATE_NOT_FOUND"].includes(error.message))
        throw error;
    }
    if (this.#pairing && this.#session?.status !== "BOUND") {
      await this.#request(
        "DELETE",
        `/${this.#pairing.id}/pc`,
        undefined,
        false,
        true,
      );
    }
    this.clear();
  }
  clear() {
    this.#session = undefined;
    this.#pairing = undefined;
  }
}
module.exports = { CertificateClient, userErrorMessage };
