# 얼마 인증서 가져오기

PC·USB의 공동인증서를 선택하고, 모바일 QR 승인으로 HowMuch 금융 연결을 만드는 Electron 앱입니다. 설치 이름은 **얼마 인증서 가져오기**, 창 제목은 **인증서 가져오기**입니다.

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

Backend에는 PostgreSQL·Redis·Kafka, V46/V47 마이그레이션과 인증서 기능 설정이 필요합니다. 실제 인증서 테스트에는 mock 서버가 아닌 실제 CODEF 구성이 필요합니다. 설치본은 위 환경변수를 무시하고 빌드 시 지정한 HTTPS 주소만 사용합니다.

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

1. 앱을 실행하고 USB를 연결합니다. USB가 있으면 자동으로 선택하며, PC 인증서 또는 다른 폴더도 선택할 수 있습니다.
2. 인증서를 선택하고 금융사와 인증서 비밀번호를 입력합니다. 금융사에서 요구하면 계좌번호·계좌 비밀번호·생년월일도 입력합니다. 우리은행은 계좌번호와 계좌 비밀번호가 필요합니다.
3. 가져오기를 진행하면 QR이 표시됩니다. 로그인한 모바일 사용자가 QR을 스캔하면 PC에 6자리 확인 코드와 수신 체크가 표시됩니다.
4. 모바일에서 금융사를 확인하고 Yes/No를 선택합니다. Yes이면 서버가 CODEF 등록을 진행하고 성공 후 사용자 연결을 저장합니다. No이면 CODEF 등록 없이 취소합니다.
5. 앱에 **연결이 완료됐어요** 체크가 뜨면 완료입니다. `다시 가져오기`로 다음 인증서를 연결할 수 있습니다. 이 버튼은 기존 금융 연결을 삭제하지 않습니다.

모바일 Yes/No 화면은 모바일 앱 저장소의 별도 구현 대상입니다. 화면 연동 전에는 로그인 JWT로 아래 API를 호출해 같은 흐름을 검증할 수 있습니다. QR과 토큰을 공개 채팅·로그에 남기지 않습니다.

- `POST /api/v2/certificate-claims`: QR의 sessionId·qrToken과 idempotencyKey로 예약.
- `GET /api/v2/certificate-claims/{sessionId}/{claimId}`: 금융사와 처리 상태 확인.
- `POST /api/v2/certificate-claims/{sessionId}/{claimId}/decision`: `{ "approved": true }` 또는 false.
- Yes의 `SUBMITTED`는 접수 상태입니다. GET을 반복해 `BOUND`와 로컬 connectionId를 확인합니다.
- `GET /api/v2/accounts/connections`: 내 금융사 연결 목록.
- `GET /api/v2/accounts/connections/{connectionId}/accounts?organization=0020`: 계좌 후보 조회. 조회만으로 계좌가 선택·등록되지는 않습니다.

QR이 만료되면 앱에서 새 QR을 만듭니다. 인증서/계좌 비밀번호 오류와 잠김 등은 구분해 표시합니다. `UNKNOWN`은 외부 처리 결과를 확인할 수 없는 상태이므로 자동으로 재등록하지 않습니다.

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
