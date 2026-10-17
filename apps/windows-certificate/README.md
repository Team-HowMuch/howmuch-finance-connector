# 얼마 인증서 가져오기

앱 실행 직후 QR로 휴대폰을 연결하고, PC·USB의 공동인증서로 금융사를 인증한 뒤 모바일 최종 승인으로 HowMuch 금융 연결을 저장하는 Electron 앱입니다. 설치 이름은 **얼마 인증서 가져오기**, 창 제목은 **인증서 가져오기**입니다.

앱 소스와 Windows/macOS 빌드는 이 저장소에서 관리합니다. 인증서 접수·CODEF 호출·connectedId 저장·Kafka Inbox/Outbox는 현재 [Backend API](https://github.com/Team-HowMuch/howmuch-backend-v2)에 있습니다. finance-connector Java 서버를 실행하는 것만으로 인증서 API가 열리지는 않습니다.

## 개발 실행

Node.js 24와 npm이 필요합니다. 저장소 루트에서 실행합니다.

```sh
cd apps/windows-certificate
npm ci
npm start
```

기본 API 주소는 `https://dev.howmuch.wars.p-e.kr`입니다. 해당 서버에 인증서 기능과 DB 마이그레이션이 배포되어 있어야 합니다. 개발 소스는 Windows와 macOS에서 실행할 수 있습니다.

로컬 Backend API를 사용할 때는 **패키징하지 않은 개발 앱**에서만 주소를 바꿀 수 있습니다.

macOS:

```sh
HOWMUCH_CERTIFICATE_LOCAL=1 HOWMUCH_CERTIFICATE_API_ORIGIN=http://127.0.0.1:18081 npm start
```

Windows PowerShell:

```powershell
$env:HOWMUCH_CERTIFICATE_LOCAL = "1"
$env:HOWMUCH_CERTIFICATE_API_ORIGIN = "http://127.0.0.1:18081"
npm start
```

Backend에는 PostgreSQL·Redis·Kafka, V46/V47/V50 마이그레이션 및 초기 pairing API과 인증서 기능 설정이 필요합니다. 실금융사 인증은 실제 CODEF 구성이 필요하며, 로컬 Mock 검증은 Mock 사용자에게 발급한 합성 인증서로 진행합니다. 설치본은 위 환경변수를 무시하고 빌드 시 지정한 HTTPS 주소만 사용합니다.

## 로컬 QR 연결 API 테스트

PC 앱이 만든 초기 연결 QR을 확인하고 모바일 연결 API를 호출하는 테스트 페이지입니다. Backend API를 로컬 `127.0.0.1:18081`로 실행한 뒤 아래 명령으로 페이지를 엽니다.

```sh
npm run test:manual
```

브라우저에서 <http://127.0.0.1:18082>에 접속합니다. 같은 PC에서 실행 중인 인증서 앱의 QR은 스크린샷을 찍어 **QR 이미지 선택**으로 읽을 수 있습니다. QR 디코더는 로컬 `jsqr`를 사용하며 외부 CDN으로 전송하지 않습니다.

1. **테스트 계정 채우기 → 로그인**: 로컬에 준비된 가상 계정 `01000000001`, PIN `123456`을 사용합니다. 새 DB에서는 이 계정을 별도로 seed해야 합니다.
2. PC 앱의 초기 QR을 읽고 **연결 API 호출**을 누릅니다.
3. PC 앱에서 우리은행을 선택하고 가상 인증서로 인증합니다. 이번 로컬 환경의 인증서 위치는 `~/NPKI/howmuch-local-test/USER/local-demo`, 이름은 `HowMuch LOCAL MOCK ONLY`, 암호는 `test1234!`입니다. 가상 계좌번호 `1002123456789`, 계좌 비밀번호 `1234`, 생년월일 `19990101`을 사용합니다.
4. 페이지의 **연결 상태 다시 확인**을 눌러 `READY_FOR_CLAIM`인지 확인합니다.
5. **승인 요청 · 6자리 확인**을 누르고 PC의 코드와 비교한 뒤 **YES** 또는 **NO**를 선택합니다.
6. YES 후 `BOUND`를 확인하고 **연결 목록 조회 → 계좌 조회**로 가상 계좌를 확인합니다.

CODEF mock 관리 웹은 <http://127.0.0.1:18080>입니다. 인증서 요청은 가상 사용자 `demo-user`의 계좌를 반환합니다. 사용자별 인증서를 발급하는 최신 mock은 발급된 인증서의 소유자·DER/key 일치·인증서 암호·계좌 소유자와 계좌 암호를 검증합니다. 실제 인증정보를 입력하지 않습니다.

테스트 서버는 loopback에만 바인딩하고 아래 경로와 메서드만 로컬 API `127.0.0.1:18081`로 전달합니다.

- `POST /api/v2/auth/login`
- `POST /api/v2/certificate-pairings/{id}/claim`
- `GET`, `DELETE /api/v2/certificate-pairings/{id}`
- `POST /api/v2/certificate-pairings/{id}/approval`
- `GET /api/v2/certificate-claims/{sessionId}/{claimId}`
- `POST /api/v2/certificate-claims/{sessionId}/{claimId}/decision`
- `GET /api/v2/accounts/connections`
- `GET /api/v2/accounts/connections/{connectionId}/accounts?organization=0020`

로그인 응답의 `accessToken`만 메모리에 유지하며 `requestToken`은 페이지에 전달하지 않습니다. PIN, Access Token, QR 값은 저장하거나 로그에 남기지 않습니다. 로그인 실패 또는 성공 시 입력한 PIN을 지웁니다.

## Windows 설치 파일 빌드

Windows에서 환경변수로 API 주소를 지정해 빌드합니다. `beforePack`이 주소를 검증하고 `release-config.json`에 기록합니다. 첫 실행 시 `npm ci`로 의존성을 설치합니다.

```powershell
$env:HOWMUCH_CERTIFICATE_API_ORIGIN = "https://dev.howmuch.wars.p-e.kr"
npm run dist:win
```

macOS 등에서 기존 명시적 주소 지정도 사용할 수 있습니다.

```sh
npm run configure -- https://dev.howmuch.wars.p-e.kr
npm run dist:win
```

`npm run configure`는 명시적 인자 → `HOWMUCH_CERTIFICATE_API_ORIGIN` 순서로 주소를 읽습니다. 빌드 시 환경변수가 있으면 기존 설정 파일보다 우선합니다. 다른 주소로 명시적 configure를 실행했다면 이전 환경변수도 제거하거나 같은 주소로 맞춥니다. `.env` 파일을 자동으로 읽지는 않습니다.

결과: `dist/얼마-인증서-가져오기-<version>-x64-Setup.exe`. x64 NSIS 설치본이며 설치 위치를 선택할 수 있습니다. 공개 API 주소만 `release-config.json`에 생성되고 이 파일은 커밋하지 않습니다. CODEF 키·JWT·인증서·비밀번호를 빌드 설정에 넣지 않습니다. 설치본의 API 주소는 빌드 때 고정되며 설치 후 환경변수로 바뀌지 않습니다.

## Release 자동 빌드

GitHub Actions의 **Certificate desktop app** workflow가 PR에서 Windows 테스트·Electron smoke·패키징 실행을 검증합니다. 아래 환경 설정 후 앱 전용 GitHub Release를 **Publish release**하면 같은 검증을 거쳐 Windows 설치 파일·macOS DMG와 각각의 `.sha256`을 Release Assets에 자동 첨부합니다. Draft 저장이나 태그 push만으로 게시하지 않습니다.

| 게시 방식 | 태그 예시 | API 환경 |
|---|---|---|
| 사전 릴리스 (Set as a pre-release) | `certificate-v0.1.6-rc.1` | `certificate-windows-dev` |
| 정식 릴리스 | `certificate-v0.1.6` | `certificate-windows-prod` |

태그의 버전은 **릴리스 커밋의** `package.json`과 `package-lock.json` 버전을 맞춘 뒤 `package.json.version`과 정확히 일치시킵니다. 예를 들어 앱 폴더에서 `npm version 0.1.6 --no-git-tag-version`으로 두 파일을 갱신한 후 릴리스 커밋을 준비합니다. `-rc.1` 같은 사전 버전은 반드시 pre-release로 게시합니다. `finance-contracts-v*` 릴리스는 앱 빌드를 실행하지 않습니다.

이미 게시된 사전 릴리스를 정식으로 바꾸는 방식 대신 사전 접미사가 없는 새 버전/태그로 정식 Release를 게시합니다. 이 workflow는 `release.published` 이벤트를 사용합니다. 자세한 이벤트 동작은 [GitHub 공식 문서](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#release)를 참고합니다.

### GitHub Environment 설정

저장소 **Settings → Environments**에서 다음 두 환경을 만들고 환경마다 값을 설정합니다. 기존 `certificate-windows-release`에 서명 Secret이 있다면 새 두 환경에도 등록합니다.

| 항목 | `certificate-windows-dev` | `certificate-windows-prod` |
|---|---|---|
| Variable `HOWMUCH_CERTIFICATE_API_ORIGIN` | `https://dev.howmuch.wars.p-e.kr` | `https://api.howmuch.wars.p-e.kr` |
| Variable `HOWMUCH_REQUIRE_SIGNING` | 현재 `false` | 현재 `false` |
| Secret `WINDOWS_CSC_LINK` | 서명 인증서 (서명 필수일 때) | 서명 인증서 (서명 필수일 때) |
| Secret `WINDOWS_CSC_KEY_PASSWORD` | 인증서 비밀번호 | 인증서 비밀번호 |

주소에는 `/api/v2` 같은 경로·쿼리·인증정보를 넣지 않습니다. 주소가 비었거나 HTTPS가 아니면 실패하며 dev 주소로 대체하지 않습니다. `HOWMUCH_REQUIRE_SIGNING`은 Windows 전용입니다. 현재 두 환경은 사용자 결정에 따라 `false`를 사용합니다. 미설정 시 **true**이고 true일 때 서명 키가 없거나 실제 코드 서명이 실패하면 게시하지 않습니다. 미서명 설치본이 필요하면 해당 환경에 `false`를 명시합니다. 이는 Windows SmartScreen 신뢰를 보장하지 않습니다. Environment에 승인/태그 보호 규칙이 설정돼 있다면 그 규칙을 따릅니다.

### 다운로드와 재실행

검증 성공 후 해당 Release의 Assets에서 다음을 받습니다. GitHub의 한글 파일명 변환을 피하기 위해 배포 파일명은 영문으로 사용하고, 앱 표시 이름은 얼마 인증서 가져오기를 유지합니다.

- `HowMuch-Certificate-<version>-dev-x64-Setup.exe` 또는 `...-prod-x64-Setup.exe`
- `HowMuch-Certificate-<version>-dev-arm64.dmg` 또는 `...-prod-arm64.dmg` (Apple Silicon)
- `HowMuch-Certificate-<version>-dev-x64.dmg` 또는 `...-prod-x64.dmg` (Intel)
- 각 설치 파일과 같은 이름의 `.sha256`

다운로드한 설치 파일을 PowerShell `Get-FileHash -Algorithm SHA256`으로 확인할 수 있습니다. 자동 업데이트 기능은 포함하지 않습니다. 같은 workflow를 재실행하면 [GitHub CLI `--clobber`](https://cli.github.com/manual/gh_release_upload)로 같은 이름의 asset을 교체하므로 기존 다운로드와 checksum이 달라질 수 있습니다. 정식 배포 후 변경은 새 버전 Release로 게시합니다.

**Run workflow** 수동 실행도 유지합니다. `target_environment`로 dev/prod를 선택하며 `api_origin`을 비우면 해당 Environment의 주소를 사용합니다. 필요할 때만 공개 HTTPS 주소를 재정의합니다. `signed`는 Windows 서명 옵션이며 현재 기본값은 false입니다. Mac은 이 옵션과 무관하게 ad-hoc 서명을 사용합니다. 수동 실행은 Release를 만들거나 첨부하지 않고 **howmuch-certificate-windows-dev/prod**, **howmuch-certificate-macos-dev/prod-arm64/x64** Actions artifact에 7일간 보관합니다.

## macOS 임시 서명 빌드와 설치

Mac에서 저장소의 앱 폴더로 이동한 뒤 실행합니다. Apple 개발자 인증서나 공증용 Secret 없이 빌드합니다.

```sh
npm ci
HOWMUCH_CERTIFICATE_API_ORIGIN=https://dev.howmuch.wars.p-e.kr npm run dist:mac
```

`dist/얼마-인증서-가져오기-<version>-arm64.dmg`와 `...-x64.dmg`가 생성됩니다. Apple Silicon(M1 이상)은 arm64, Intel Mac은 x64를 선택합니다. `mac.identity: "-"`로 앱과 내부 실행 파일에 ad-hoc 서명을 적용합니다. hardened runtime, sandbox와 ASAR 보안 fuse를 유지합니다. Apple 공증은 수행하지 않습니다.

DMG 설치 화면은 얼마의 파란색, 짧은 설치 안내와 앱 → Applications 화살표를 사용합니다. 아이콘은 실제 Finder 항목이며 앱을 오른쪽 폴더로 드래그합니다. `installer/dmg-background.png`(720×460)와 `installer/dmg-background@2x.png`(1440×920)를 빌드에 사용합니다. 배경만 바꿀 때는 macOS에서 다음을 실행하고 두 배경 PNG와 `src/assets/icon-mac.png`를 함께 반영합니다. macOS 설치 아이콘과 Dock은 이 투명 여백·둥근 모서리의 전용 아이콘을 공유하며, Windows는 기존 아이콘을 유지합니다. CI는 커밋된 이미지를 사용하므로 Swift나 폰트 설치가 필요하지 않습니다.

```sh
swift scripts/render-dmg.swift --preview
```

`dist/dmg-preview.png`는 배경에 아이콘을 합성한 디자인 미리보기이며 Finder 스크린샷은 아닙니다. 실제 배치는 `package.json`의 `build.dmg.contents`에서 관리합니다. 레이아웃 크기와 아이콘 좌표를 바꾸면 생성 스크립트도 맞춥니다. 디스크 이름은 `HowMuch Certificate <version>`을 사용합니다. dmgbuild/mac_alias의 한글 NFD/NFC 경로 불일치로 호스트 APFS file ID가 배경 alias에 들어가는 것을 방지하기 위한 선택이며, 앱 표시 이름은 한글을 유지합니다. 첫 배포 전에는 native Intel DMG 생성도 확인합니다.

DMG를 열고 앱을 Applications로 드래그한 뒤 실행합니다. 개발자 확인 경고가 나오면, 직접 받은 신뢰 가능한 파일인지 확인한 뒤 **시스템 설정 → 개인정보 보호 및 보안 → 확인 없이 열기**를 사용합니다. ad-hoc 서명은 Developer ID나 Apple 공증을 대신하지 않으며 Gatekeeper의 자동 허용을 보장하지 않습니다. [Apple 실행 안내](https://support.apple.com/en-us/102445)를 참고합니다. 코드/리소스가 손상되었다는 오류는 단순 승인 대상으로 처리하지 말고 설치 파일과 checksum을 다시 확인합니다.

PR에서는 Mac 두 CPU의 native runner에서 Electron 및 패키징 실행을 검증합니다. Release에서는 두 DMG의 ad-hoc 서명·API origin·초기 실행을 검증하고, Windows까지 모두 성공한 뒤 한 게시 job에서 설치 파일을 첨부합니다. Apple Silicon 로컬 검증은 Intel의 native 실행 및 인터넷 다운로드 후 Gatekeeper 동작을 대신하지 않습니다.

## 사용 방법

앱 상단 오른쪽의 `Client ver.`에서 설치한 앱 버전을 확인할 수 있습니다. 패키지 버전을 자동으로 읽으며 `0.1.9-rc.1` 같은 프리릴리스 표기도 그대로 표시합니다. 서버 연결 상태와 무관하게 확인할 수 있습니다.

1. 앱을 실행하고 첫 화면의 QR을 로그인한 얼마 모바일 앱에서 스캔합니다. 첫 QR은 PC·모바일 연결이며 금융사 연결 승인은 아닙니다.
2. 모바일에는 연결 완료 후 PC 인증 대기를 표시합니다. PC에 인증서 선택 화면이 열리면 PC·USB 인증서를 선택하고 금융사·인증서 비밀번호를 입력합니다. 필요한 계좌번호·계좌 비밀번호·생년월일도 입력합니다.
3. PC에서 가져오기를 진행합니다. 서버는 연결된 사용자의 기존 connectedId가 있으면 재사용해 CODEF를 호출합니다. 오류가 나면 PC/모바일에 공통 오류를 표시하고 최종 승인 화면은 열지 않습니다.
4. 인증 성공 후 모바일에 금융사와 최종 Yes/No를 표시합니다. 두 번째 QR은 없습니다. 최종 요청의 6자리 확인 코드는 PC에서 확인할 수 있습니다.
5. Yes이면 이미 검증된 임시 결과를 사용자 DB에 저장하고 양쪽에 완료를 표시합니다. No이면 임시 결과를 폐기하고 사용자 DB에 새 연결을 저장하지 않습니다.

CODEF의 기존 connectedId add/update는 PC 인증 단계에서 수행됩니다. No가 외부 금융사 인증자료의 이전 상태까지 복원한다는 뜻은 아닙니다. 기존 또는 재사용된 ID를 삭제하지 않도록 pairing 취소는 우리 서버의 임시 암호문만 폐기합니다. 미귀속 CODEF 등록 자료의 정리는 별도 운영 대상입니다.

### 모바일 연동 API · QR v2

QR 형식은 `{ "v": 2, "type": "HOWMUCH_CERTIFICATE_PAIRING", "pairingId": "UUID", "qrToken": "일회용 토큰" }`입니다. QR/토큰을 로그나 공개 채팅에 남기지 않습니다. 기존 v1 `HOWMUCH_CERTIFICATE_CLAIM` QR는 구버전 흐름이며 새 모바일 연동에서 구분해야 합니다.

- `POST /api/v2/certificate-pairings/{pairingId}/claim`, JWT + `{ "qrToken": "...", "organization": "0020" }`: 모바일에서 선택한 금융기관을 필수로 전달합니다. 최초 사용자와 금융기관에 고정되며 동일 사용자·동일 금융기관 재시도만 멱등입니다. 다른 사용자는 404, 같은 사용자의 은행 변경은 409입니다.
- `GET /api/v2/certificate-pairings/{pairingId}`, JWT: `organization`은 등록 전부터 반환되며 PC 앱에서 해당 은행을 선택·비활성화합니다. `PAIRED`는 PC 입력 대기, `SUBMITTED`/`REGISTERING`은 금융사 인증 대기. 모바일에서 “연결됐어요 / PC에서 인증을 진행해 주세요”와 대기 화면을 표시합니다.
- `READY_FOR_CLAIM`이면 `POST /api/v2/certificate-pairings/{pairingId}/approval`을 호출합니다. 응답의 `claimId`와 상태 응답의 `sessionId`를 사용합니다.
- `POST /api/v2/certificate-claims/{sessionId}/{claimId}/decision`, `{ "approved": true | false }`: 금융사 확인 후 최종 Yes/No. Yes는 추가 CODEF 호출 없이 DB 저장하며 성공 상태는 `BOUND`입니다.
- `FAILED`는 `failureCode`/`failureMessage`를 표시합니다. `UNKNOWN`은 결과 불명으로 자동 재등록하지 않고 운영 확인이 필요합니다. `EXPIRED`/`CANCELLED`는 현재 흐름을 종료합니다.
- `GET /api/v2/accounts/connections`와 `GET /api/v2/accounts/connections/{connectionId}/accounts?organization=...`로 완료된 연결·보유계좌를 조회합니다.

모바일 화면 구현은 이 Electron 저장소에 포함되지 않습니다. 모바일 저장소에서 QR v2 분기·pairing 상태 폴링·최종 approval/decision을 연결해야 합니다. 서버 변경은 [Backend #517](https://github.com/Team-HowMuch/howmuch-backend-v2/issues/517), SQL은 [#518](https://github.com/Team-HowMuch/howmuch-backend-v2/pull/518)입니다. **신규 서버 API/V50 배포 전에는 이번 설치본의 초기 QR을 발급할 수 없습니다.**

## 인증서 탐색과 지원 범위

- 같은 폴더의 `signCert.der`와 `signPri.key` 쌍을 찾습니다. DER/KEY를 각각 직접 선택하지 않습니다.
- Windows는 `%USERPROFILE%/AppData/LocalLow/NPKI`, `%APPDATA%/NPKI`, `%LOCALAPPDATA%/NPKI`, `%PROGRAMFILES%/NPKI`, `%PROGRAMFILES(X86)%/NPKI`를 검색합니다. macOS는 `~/Library/Preferences/NPKI`, `~/Library/NPKI`, `~/NPKI`를 검색합니다. 실제 경로 목록은 `src/certificates.cjs`를 기준으로 합니다.
- USB는 Windows의 이동식 디스크 및 USB 버스 디스크, macOS의 마운트된 외부 볼륨을 탐색합니다. macOS가 이동식 볼륨 접근을 요청하면 허용해야 합니다. 잠긴/마운트되지 않은 USB는 운영체제에서 먼저 열어야 합니다.
- 연결/분리 여부는 3초마다 확인하며 처리 중에는 탐색을 멈춥니다. 같은 USB에 파일만 추가했다면 새로고침합니다.
- 탐색은 최대 깊이 6, 항목 4,000개, 인증서 100개, 5초 예산으로 제한합니다. 심볼릭 링크, 128 KiB 초과 파일, 불완전한 인증서 쌍은 제외합니다.
- 만료/미개시 인증서는 표시하되 선택할 수 없습니다. 유효기간 표시는 은행 사용 가능 여부나 명의 검증 완료를 뜻하지 않습니다.
- 브라우저 인증서, OS 키체인, 보안 토큰, P12/PFX 가져오기는 지원하지 않습니다. Linux는 폴더 직접 선택만 지원합니다.

## 검증

```sh
npm test
npm run test:smoke
npm audit --omit=dev --audit-level=high
```

단위 테스트와 smoke는 공개 테스트 인증서·합성 API를 사용합니다. 실제 Electron에서 USB 선택, 추가 입력, 오류, QR 수신, 최종 완료, 다시 가져오기와 창 제어를 검증합니다. 패키징 후에는 다음으로 초기 화면을 확인합니다.

```powershell
node test/packaged-smoke.cjs "dist/win-unpacked/얼마 인증서 가져오기.exe"
```

테스트 빌드의 `certificate-api.example.test`는 실제 서버가 아닙니다. 실제 인증정보를 입력하지 않습니다. macOS 실행 검증은 Windows 설치·USB·SmartScreen 검증을 대신하지 않습니다.

## 보안 경계

인증서 탐색·파일 읽기·업로드는 Electron main에서 처리합니다. renderer에는 파일 경로·개인키·PC capability를 전달하지 않으며 sandbox/contextIsolation과 제한된 preload API를 사용합니다. 개인키는 명시적 제출 때만 읽고 전송 뒤 버퍼를 비웁니다. QR에는 connectedId·인증서·암호·JWT를 넣지 않습니다. 오류 화면에는 정해진 한국어 문구만 표시하고 CODEF 원문은 표시하지 않습니다.


### 인증 실패 재시도와 종료 취소

인증이 확정 실패하면 오류 안내와 함께 인증서 선택으로 돌아갑니다. 암호를 다시 입력하고 인증서를 선택합니다. 휴대폰 연결과 QR은 유지됩니다. 처리 결과가 불확실한 UNKNOWN 상태에서는 자동 재시도하지 않습니다.

PC 앱을 정상 종료하면 진행 중 요청이 끝난 뒤 서버의 취소 응답을 기다립니다. 취소를 확인하지 못하면 창을 유지하고 안내합니다. 이미 승인된 계좌 연결은 삭제하지 않습니다.

모바일은 연결 화면 이탈·취소·정상 종료에서 소유자 JWT로 `DELETE /api/v2/certificate-pairings/{id}`를 호출합니다(body 없음). PC는 취소 상태를 감시합니다. 실패 후에도 pairing 상태 조회를 유지하고 새 시도의 최신 sessionId를 사용해야 합니다. 모바일 lifecycle 구현은 별도 모바일 저장소에서 연결해야 합니다. OS 강제 종료/네트워크 단절에서는 요청을 보장할 수 없어 기존 세션 만료가 적용됩니다.

업데이트한 앱은 backend V51 migration과 위 모바일 취소 API 배포 후 사용합니다.

### v3 은행 카탈로그 · 0.1.12-rc.2

이 버전부터 은행 목록은 공개 `GET /api/v3/banks`를 사용합니다. Backend의 V53 인증 수준 데이터와 v3 API를 먼저 배포해야 합니다. 기존 pairing·등록·승인 API는 v2입니다. 인증서 앱에서는 수준 1·2일 때 계좌 입력을 숨기고 수준 3일 때 계좌번호·계좌 비밀번호를 필수로 표시합니다. 레거시 `/api/v2/accounts/banks` 응답은 서버에서 유지됩니다.

### 모바일 선택 금융기관 고정 (0.1.12-rc.3)

모바일은 `GET /api/v3/banks`로 선택한 은행의 `organization`을 초기 QR claim에 전달합니다. PC는 해당 은행을 변경할 수 없으며 `authenticationLevel: 3`에만 계좌번호·계좌 비밀번호 입력란이 표시됩니다. 생년월일 입력은 은행별 기존 요구를 따릅니다. 실패 후 재시도에도 같은 은행을 유지합니다. 은행을 바꾸려면 기존 연결을 취소하고 새 QR로 연결합니다.

서버 V55 migration 및 모바일 organization 저장/반환 구현이 선행되어야 합니다. 구형 서버가 은행을 반환하지 않으면 PC 앱은 임의 은행을 선택하지 않고 새 연결 안내를 표시합니다. 실제 모바일 저장소는 별도 반영이 필요하며 수동 테스트 HTML은 은행 선택과 organization 전송을 포함합니다.

### SMS 추가 인증 대기

서버가 금융사 SMS 추가 인증을 요구하면 PC에 ‘휴대폰에서 SMS 인증을 진행해 주세요’를 표시합니다. 인증번호는 모바일에서 입력하며 PC에는 전달하지 않습니다. 확인 중에는 ‘SMS 인증 확인 중’을 표시하고, 성공 후 기존 모바일 최종 승인으로 이어집니다. 이 단계에서 QR을 다시 찍지 않습니다.

서버의 `CODEF_SMS_ADDITIONAL_AUTH_ENABLED` 설정과 모바일 SMS API 연동이 필요합니다. 자세한 변경·검증 범위는 [SMS 추가 인증 대기 표시](../../docs/features/active/codef-sms-additional-authentication.md)를 참고하세요.


### 금융 SMS 로컬 통합 (2026-10-17)

PC는 SMS 번호를 입력받지 않고 휴대폰에서 인증하도록 대기 상태만 표시합니다. 금융사 인증이 끝나면 기존 모바일 Yes/No 승인과 PC 6자리 확인 흐름을 유지합니다. 모바일의 명시적 ‘인증 취소’는 서버 인증을 취소합니다. 성공 확인 시에는 모달만 닫고 다른 화면으로 이동하지 않으며, 알림 목록에서도 인증 상태를 확인할 수 있습니다.

```bash
HOWMUCH_CERTIFICATE_LOCAL=1 \
HOWMUCH_CERTIFICATE_API_ORIGIN=http://127.0.0.1:18081 npm start
```

이번 로컬 Mock fixture는 `~/NPKI/howmuch-sms-local/USER/`에서 자동 검색합니다. 사용자 `howmuch-sms`, 우리은행 `0020`, 합성 계좌 `1002000000017`, 계좌 암호 `1234`, 생년월일 `19900101`, 인증서 암호 `MockCert!2026`을 사용합니다. 실제 사용자의 인증서가 아닙니다. Mock의 BANK_CONNECT SMS를 켜면 모바일 입력 번호는 `123456`입니다. 이 fixture는 로컬 테스트 DB에 발급/등록된 파일이므로 다른 서버에서는 그대로 인증되지 않습니다.
