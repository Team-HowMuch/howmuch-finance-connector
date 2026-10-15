"use strict";
const $ = (id) => document.getElementById(id);
// Local app metadata remains available even when the API is unreachable.
window.certificate.appVersion().then((version) => {
  $("clientVersion").textContent = `Client ver. ${version}`;
  $("clientVersion").hidden = false;
}).catch(() => {});
$("appClose").addEventListener("click", () => {
  window.certificate?.close().catch(() => {});
});
let institutions = [],
  selected,
  timer,
  claimId,
  qrDeadline,
  sessionDeadline,
  retryContext;
let pairedOrganization,
  paired = false,
  pairTimer,
  pairEpoch = 0;
let generation = 0,
  qrLoading = false,
  scanning = false,
  scanGeneration = 0,
  storageRevision,
  retryUsbOnFocus = false;
function message(text) {
  $("message").setAttribute("role", "status");
  $("message").textContent = text;
  $("message").hidden = !text;
}
function clearCredentials() {
  for (const id of [
    "password",
    "accountNumber",
    "accountPassword",
    "birthDate",
  ])
    $(id).value = "";
}
function details(text) {
  $("progressText").textContent = text;
  $("progressText").hidden = !text;
}
function step(number) {
  for (let i = 1; i <= 3; i++) {
    const item = $(`step${i}`);
    item.removeAttribute("aria-current");
    if (i === number) item.setAttribute("aria-current", "step");
    item.dataset.complete = String(i < number);
  }
}
function clearQr() {
  $("qrCard").hidden = true;
  $("qrImage").hidden = true;
  $("qrImage").removeAttribute("src");
  $("qrExpiry").textContent = "";
  qrDeadline = undefined;
}
function clearClaim() {
  claimId = undefined;
  $("claimBox").hidden = true;
  $("confirmationCode").textContent = "";
}
function stopped(title, text) {
  clearQr();
  clearClaim();
  $("refreshQr").hidden = true;
  $("progress").dataset.state = "stopped";
  $("progressTitle").textContent = title;
  details(text);
  message("");
}
async function knownError(error) {
  try {
    return await window.certificate.failureMessage(error?.message);
  } catch {
    return null;
  }
}
async function showAuthenticationFailure(title, text, button = "확인") {
  $("authenticationFailureTitle").textContent = title;
  $("authenticationFailureText").textContent = text;
  $("retryAuthentication").textContent = button;
  await new Promise((resolve) => {
    const dialog = $("authenticationFailure");
    const finish = () => {
      dialog.removeEventListener("close", finish);
      $("retryAuthentication").removeEventListener("click", finish);
      resolve();
    };
    dialog.addEventListener("close", finish, { once: true });
    $("retryAuthentication").addEventListener("click", finish, { once: true });
    dialog.showModal();
  });
}
async function restoreCertificateSelection(epoch) {
  if (epoch !== generation) return;
  const retryEpoch = ++generation;
  clearTimeout(timer);
  clearQr();
  clearClaim();
  clearCredentials();
  selected = undefined;
  $("progress").hidden = true;
  $("registration").hidden = false;
  $("submit").disabled = true;
  step(2);
  await discover(retryContext?.source || "auto");
  if (retryEpoch !== generation) return;
  requirements();
  message("");
}
async function terminal(session) {
  const terminalEpoch = generation;
  if (session.status === "BOUND") {
    clearTimeout(timer);
    clearQr();
    clearClaim();
    step(3);
    $("progress").dataset.state = "claim";
    $("progressTitle").textContent = "연결이 완료됐어요";
    details("");
    message("");
    $("claimBox").hidden = true;
    $("cancel").textContent = "다시 가져오기";
    $("refreshQr").hidden = true;
    return true;
  }
  if (session.status === "FAILED") {
    const failureText =
      session.failureMessage || "인증에 실패했어요. 인증서를 다시 선택해 주세요.";
    await showAuthenticationFailure("금융사 인증에 실패했어요", failureText, "다시 선택");
    if (terminalEpoch !== generation) return true;
    await window.certificate.retry();
    await restoreCertificateSelection(terminalEpoch);
    return true;
  }
  if (!["FAILED", "UNKNOWN", "EXPIRED", "CANCELLED"].includes(session.status))
    return false;
  const titles = {
    FAILED: "금융사 인증에 실패했어요",
    UNKNOWN: "금융사 처리 결과 확인이 필요합니다",
    EXPIRED: "연결 시간이 만료됐어요",
    CANCELLED: "연결이 취소됐어요",
  };
  stopped(
    titles[session.status],
    session.failureMessage ||
      (session.status === "UNKNOWN"
        ? "처리 결과를 확인한 뒤 다시 연결해 주세요."
        : "취소한 뒤 다시 시도해 주세요."),
  );
  return true;
}
function reset() {
  ++pairEpoch;
  clearTimeout(pairTimer);
  clearTimeout(timer);
  clearQr();
  clearClaim();
  sessionDeadline = undefined;
  retryContext = undefined;
  if ($("authenticationFailure").open) $("authenticationFailure").close();
  selected = undefined;
  $("reject").hidden = false;
  $("cancel").textContent = "취소";
  $("registration").reset();
  $("registration").hidden = true;
  paired = false;
  $("pairedNotice").hidden = true;
  $("progress").hidden = true;
  $("refreshQr").hidden = true;
  $("progressTitle").textContent = "인증서 확인 중";
  $("progress").dataset.state = "processing";
  details("");
  step(1);
  $("certificateList").replaceChildren();
  $("submit").disabled = true;
  requirements();
  beginPairing();
}
function requirements() {
  $("organization").value = pairedOrganization || "";
  $("organization").disabled = true;
  const i = institutions.find((i) => i.organization === pairedOrganization);
  const account = i?.authenticationLevel === 3;
  $("accountFields").hidden = !account;
  $("accountNumber").required = $("accountPassword").required = account;
  $("birthField").hidden = !i?.birthDateRequired;
  $("birthDate").required = Boolean(i?.birthDateRequired);
  $("submit").disabled =
    !paired ||
    !i ||
    !selected ||
    scanning ||
    !$("password").value ||
    (account && (!$("accountNumber").value || !$("accountPassword").value)) ||
    (i.birthDateRequired && !$("birthDate").value);
}
function certificateRows(list) {
  const rows = list.map((certificate) => {
    const row = document.createElement("label");
    row.className = "certificate-row";
    const radio = document.createElement("input");
    radio.type = "radio";
    radio.name = "certificate";
    radio.value = certificate.id;
    radio.disabled = certificate.status !== "VALID";
    const owner = document.createElement("span");
    owner.className = "certificate-owner";
    const name = document.createElement("strong");
    name.textContent = certificate.owner;
    owner.append(name);
    if (radio.disabled) {
      const state = document.createElement("small");
      state.textContent =
        certificate.status === "EXPIRED" ? "기간 만료" : "사용 전";
      owner.append(state);
    }
    const issuer = document.createElement("span");
    issuer.textContent = certificate.issuer;
    const expiry = document.createElement("span");
    expiry.textContent = new Intl.DateTimeFormat("ko-KR", {
      year: "numeric",
      month: "2-digit",
      day: "2-digit",
      timeZone: "Asia/Seoul",
    }).format(new Date(certificate.validTo));
    row.append(radio, owner, issuer, expiry);
    radio.addEventListener("change", () => {
      selected = certificate.id;
      clearCredentials();
      requirements();
    });
    return row;
  });
  $("certificateList").replaceChildren(...rows);
  $("certificateEmpty").hidden = list.length > 0;
  $("certificateEmptyText").textContent = "저장된 인증서가 없어요";
}
async function discover(source) {
  if (scanning) return;
  const epoch = ++scanGeneration;
  scanning = true;
  $("certificatePanel").setAttribute("aria-busy", "true");
  for (const id of ["computer", "usb", "folder", "refreshCertificates"])
    $(id).disabled = true;
  $("certificateList").inert = true;
  $("scanNotice").hidden = true;
  if (!$("certificateList").children.length)
    $("certificateEmptyText").textContent = "인증서 찾는 중…";
  requirements();
  try {
    const result = await window.certificate.discover(source);
    if (epoch !== scanGeneration) return;
    if (!result) {
      if (!$("certificateList").children.length)
        $("certificateEmptyText").textContent = "저장된 인증서가 없어요";
      return;
    }
    selected = undefined;
    clearCredentials();
    storageRevision = result.storage.revision;
    retryUsbOnFocus =
      result.source === "usb" &&
      !result.certificates.length &&
      (result.limited || result.unavailable);
    certificateRows(result.certificates);
    for (const id of ["computer", "usb"])
      $(id).setAttribute("aria-pressed", String(id === result.source));
    if (result.source === "usb" && !result.certificates.length)
      $("certificateEmptyText").textContent = result.storage.deviceCount
        ? "USB에서 인증서를 찾지 못했어요"
        : "연결된 USB가 없어요";
    const notice = result.limited
      ? "일부 폴더만 확인했어요. 인증서가 있는 폴더를 직접 선택해 주세요."
      : result.unavailable
        ? "저장 위치를 확인하지 못했어요. 다른 위치에서 다시 찾아 주세요."
        : "";
    $("scanNotice").textContent = notice;
    $("scanNotice").hidden = !notice;
  } catch {
    selected = undefined;
    clearCredentials();
    certificateRows([]);
    $("certificateEmptyText").textContent = "인증서를 찾지 못했어요";
    $("scanNotice").textContent =
      "다른 위치에서 인증서가 있는 폴더를 선택해 주세요.";
    $("scanNotice").hidden = false;
  } finally {
    scanning = false;
    $("certificateList").inert = false;
    $("certificatePanel").setAttribute("aria-busy", "false");
    for (const id of ["computer", "usb", "folder", "refreshCertificates"])
      $(id).disabled = false;
    requirements();
  }
}
for (const source of ["computer", "usb", "folder"])
  $(source).addEventListener("click", () => discover(source));
$("refreshCertificates").addEventListener("click", () =>
  discover(
    $("usb").getAttribute("aria-pressed") === "true" ? "usb" : "computer",
  ),
);
async function watchStorage() {
  try {
    if (!scanning && !$("registration").hidden && !document.hidden) {
      const state = await window.certificate.storage();
      if (
        !scanning &&
        !$("registration").hidden &&
        (!state.unavailable || state.deviceCount > 0) &&
        state.revision !== storageRevision
      )
        await discover(state.deviceCount ? "usb" : "computer");
    }
  } catch {
    /* Retry the read-only volume query on the next interval. */
  } finally {
    setTimeout(watchStorage, 3000);
  }
}
// macOS may return focus after its first removable-volume permission prompt.
window.addEventListener("focus", () => {
  if (retryUsbOnFocus && !scanning && !$("registration").hidden)
    discover("usb");
});
$("organization").addEventListener("change", requirements);
$("retryAuthentication").addEventListener("click", () => {
  if ($("authenticationFailure").open) $("authenticationFailure").close();
});
for (const id of ["password", "accountNumber", "accountPassword", "birthDate"])
  $(id).addEventListener("input", requirements);
$("registration").addEventListener("submit", async (event) => {
  event.preventDefault();
  if (!paired) return;
  const epoch = ++generation;
  const organization = pairedOrganization;
  retryContext = {
    organization,
    source: $("usb").getAttribute("aria-pressed") === "true" ? "usb" : "computer",
  };
  clearTimeout(timer);
  clearQr();
  clearClaim();
  $("submit").disabled = true;
  $("registration").hidden = true;
  $("progress").hidden = false;
  $("progress").dataset.state = "processing";
  step(3);
  message("");
  try {
    const submitted = await window.certificate.submit(
      organization,
      selected,
      {
        password: $("password").value,
        accountNumber: $("accountFields").hidden
          ? ""
          : $("accountNumber").value,
        accountPassword: $("accountFields").hidden
          ? ""
          : $("accountPassword").value,
        birthDate: $("birthField").hidden ? "" : $("birthDate").value,
      },
    );
    if (epoch !== generation) return;
    sessionDeadline = Date.parse(`${submitted.expiresAt}Z`);
    if (!(await terminal(submitted))) poll(epoch);
  } catch (error) {
    const text = await knownError(error);
    if (epoch !== generation) return;
    // INPUT/FILES fail before a registration request. Keep the paired device and bank.
    if (/CERTIFICATE_SUBMIT_(INPUT|FILES)_/.test(error?.message || "")) {
      clearCredentials();
      selected = undefined;
      await showAuthenticationFailure("입력 정보를 확인해 주세요", text || "인증서와 입력 정보를 확인해 주세요.");
      await restoreCertificateSelection(epoch);
    } else {
      stopped(
        "입력 정보를 확인해 주세요",
        text || "접수 결과를 확인한 뒤 다시 시도해 주세요.",
      );
    }
  } finally {
    if (epoch === generation) {
      clearCredentials();
      selected = undefined;
    }
  }
});
async function poll(epoch = generation) {
  if (epoch !== generation) return;
  try {
    const s = await window.certificate.status();
    if (epoch !== generation) return;
    sessionDeadline = Date.parse(`${s.expiresAt}Z`);
    if (await terminal(s)) return;
    if (Number.isFinite(sessionDeadline) && sessionDeadline <= Date.now()) {
      stopped(
        "연결 시간이 만료됐어요",
        "취소한 뒤 인증서 가져오기를 다시 시작해 주세요.",
      );
      return;
    }
    message("");
    if (s.status === "READY_FOR_CLAIM") {
      step(3);
      $("progress").dataset.state = "ready";
      $("progressTitle").textContent = "휴대폰에서 연결을 승인해 주세요";
      details("");
      clearClaim();
      clearQr();
      $("refreshQr").hidden = true;
    } else if (s.status === "CLAIM_RESERVED") {
      clearQr();
      step(3);
      let claim;
      try {
        claim = await window.certificate.claim();
      } catch (error) {
        // The mobile decision can finish between the status and claim requests.
        if (
          ["CERTIFICATE_NOT_FOUND", "CERTIFICATE_CONFLICT"].some((code) =>
            error.message.endsWith(code),
          )
        ) {
          clearClaim();
          $("progress").dataset.state = "processing";
          $("progressTitle").textContent = "금융사 인증 중";
          if (epoch === generation) timer = setTimeout(() => poll(epoch), 200);
          return;
        }
        throw error;
      }
      if (epoch !== generation) return;
      if (
        claim.status !== "RESERVED" ||
        !/^\d{6}$/.test(claim.confirmationCode)
      )
        throw new Error("INVALID_RESPONSE");
      if (!Number.isFinite(Date.parse(`${claim.expiresAt}Z`)))
        throw new Error("INVALID_RESPONSE");
      if (Date.parse(`${claim.expiresAt}Z`) <= Date.now())
        throw new Error("CERTIFICATE_EXPIRED");
      claimId = claim.claimId;
      $("progress").dataset.state = "claim";
      $("refreshQr").hidden = true;
      $("claimBox").hidden = false;
      $("confirmationCode").textContent = claim.confirmationCode;
      $("progressTitle").textContent = "휴대폰에서 연결을 승인해 주세요";
      details("");
    } else {
      clearQr();
      clearClaim();
      $("refreshQr").hidden = true;
      $("progress").dataset.state = "processing";
      $("progressTitle").textContent = "금융사 인증 중";
      details("");
    }
    if (qrDeadline) {
      const seconds = Math.max(0, Math.ceil((qrDeadline - Date.now()) / 1000));
      $("qrExpiry").textContent = `${seconds}초 남음`;
      if (!seconds) {
        clearQr();
        $("refreshQr").hidden = false;
      }
    }
  } catch (error) {
    const text = await knownError(error);
    if (epoch !== generation) return;
    stopped(
      "연결 상태를 확인해 주세요",
      text || "연결 상태를 확인하지 못했습니다. 잠시 후 다시 확인합니다.",
    );
    if (text) {
      $("refreshQr").hidden = true;
      return;
    }
    $("refreshQr").hidden =
      Number.isFinite(sessionDeadline) && sessionDeadline <= Date.now();
  }
  if (epoch === generation) timer = setTimeout(() => poll(epoch), 2000);
}
$("reject").addEventListener("click", () => $("cancel").click());
$("cancel").addEventListener("click", async () => {
  generation++;
  clearTimeout(timer);
  try {
    await window.certificate.cancel();
    reset();
    message("");
  } catch {
    message("취소 결과를 확인하지 못했습니다. 잠시 후 다시 시도해주세요.");
  }
});
window.certificate
  .init()
  .then((list) => {
    institutions = list;
    for (const i of institutions) {
      const option = document.createElement("option");
      option.value = i.organization;
      option.textContent = i.name;
      $("organization").append(option);
    }
    $("organization").disabled = true;
    message(institutions.length ? "" : "현재 연결 가능한 금융사가 없어요.");
    if (institutions.length) beginPairing();
  })
  .catch((error) =>
    message(
      /API_(401|403|404)/.test(error.message)
        ? "인증서 서비스가 아직 준비되지 않았어요."
        : "서버에 연결할 수 없어요. 잠시 후 다시 실행해 주세요.",
    ),
  );
setTimeout(watchStorage, 3000);

async function beginPairing() {
  const epoch = ++pairEpoch;
  clearTimeout(pairTimer);
  paired = false;
  pairedOrganization = undefined;
  requirements();
  $("pairing").hidden = false;
  $("pairingQr").hidden = true;
  $("retryPairing").hidden = true;
  $("pairingStatus").textContent = "QR을 준비하고 있어요";
  $("registration").hidden = true;
  $("pairedNotice").hidden = true;
  step(1);
  try {
    const q = await window.certificate.pair();
    if (epoch !== pairEpoch) return;
    $("pairingQr").src = q.image;
    $("pairingQr").hidden = false;
    await pollPairing(epoch, Date.parse(`${q.expiresAt}Z`));
  } catch (error) {
    if (epoch !== pairEpoch) return;
    $("pairingStatus").textContent =
      (await knownError(error)) || "휴대폰 연결 서비스를 확인해 주세요";
    $("retryPairing").hidden = false;
  }
}
async function pollPairing(epoch, deadline) {
  if (epoch !== pairEpoch) return;
  try {
    const p = await window.certificate.pairStatus();
    if (epoch !== pairEpoch) return;
    if (p.status === "PAIRED") {
      if (!institutions.some(i => i.organization === p.organization))
        throw new Error("CERTIFICATE_BANK_UNAVAILABLE");
      pairedOrganization = p.organization;
      paired = true;
      $("pairingQr").removeAttribute("src");
      $("pairing").hidden = true;
      $("pairedNotice").hidden = false;
      $("registration").hidden = false;
      step(2);
      requirements();
      await discover("auto");
      pairTimer = setTimeout(() => monitorPairing(epoch), 1500);
      return;
    }
    if (p.status !== "WAITING_FOR_MOBILE" || deadline <= Date.now())
      throw new Error("CERTIFICATE_EXPIRED");
    $("pairingStatus").textContent =
      `${Math.max(0, Math.ceil((deadline - Date.now()) / 1000))}초 남음`;
  } catch (error) {
    if (epoch !== pairEpoch) return;
    const text = await knownError(error);
    $("pairingStatus").textContent = text || "연결 상태를 다시 확인하고 있어요";
    if (text) {
      $("pairingQr").hidden = true;
      $("pairingQr").removeAttribute("src");
      $("retryPairing").hidden = false;
      return;
    }
  }
  if (epoch === pairEpoch)
    pairTimer = setTimeout(() => pollPairing(epoch, deadline), 1500);
}
async function monitorPairing(epoch) {
  if (epoch !== pairEpoch || !paired) return;
  try {
    const state = await window.certificate.pairStatus();
    if (epoch !== pairEpoch || !paired) return;
    if (["CANCELLED", "EXPIRED"].includes(state.status)) {
      ++generation;
      paired = false;
      if ($("authenticationFailure").open) $("authenticationFailure").close();
      clearTimeout(timer);
      clearCredentials();
      selected = undefined;
      $("registration").hidden = true;
      $("pairedNotice").hidden = true;
      $("progress").hidden = false;
      stopped(state.status === "CANCELLED" ? "연결이 취소됐어요" : "연결 시간이 만료됐어요",
        "다시 연결하려면 취소를 눌러 새로운 QR을 받아주세요.");
      return;
    }
    if (state.status === "BOUND") return;
  } catch {
    // A network failure is not evidence that the mobile user cancelled.
  }
  if (epoch === pairEpoch && paired)
    pairTimer = setTimeout(() => monitorPairing(epoch), 1500);
}
$("retryPairing").addEventListener("click", async () => {
  ++pairEpoch;
  clearTimeout(pairTimer);
  try {
    await window.certificate.cancel();
    await beginPairing();
  } catch (error) {
    $("pairingStatus").textContent =
      (await knownError(error)) || "잠시 후 다시 시도해 주세요";
  }
});
