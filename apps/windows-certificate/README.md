# 얼마 인증서 가져오기

PC·USB의 공동인증서를 선택하고, 모바일 QR 승인으로 HowMuch 금융 연결을 만드는 Electron 앱입니다. 설치 이름은 **얼마 인증서 가져오기**, 창 제목은 **인증서 가져오기**입니다.

앱 소스와 Windows 빌드는 이 저장소에서 관리합니다. 인증서 접수·CODEF 호출·connectedId 저장·Kafka Inbox/Outbox는 현재 [Backend API](https://github.com/Team-HowMuch/howmuch-backend-v2)에 있습니다. finance-connector Java 서버를 실행하는 것만으로 인증서 API가 열리지는 않습니다.

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

Windows에서 다음을 실행합니다. 첫 실행 시 `npm ci`로 의존성을 설치합니다.

```sh
npm run configure -- https://dev.howmuch.wars.p-e.kr
npm run dist:win
```

결과: `dist/얼마-인증서-가져오기-<version>-x64-Setup.exe`. x64 NSIS 설치본이며 설치 위치를 선택할 수 있습니다. 공개 API 주소만 `release-config.json`에 생성되고 이 파일은 커밋하지 않습니다. CODEF 키·JWT·인증서·비밀번호를 빌드 설정에 넣지 않습니다.

GitHub Actions의 **Certificate Windows app** workflow는 PR에서 Windows 테스트와 패키징 실행 검증을 수행합니다. 설치본은 **Run workflow**에서 대상 브랜치와 `api_origin`을 선택해 빌드합니다. `signed` 기본값은 true입니다. 저장소의 `certificate-windows-release` Environment에 `WINDOWS_CSC_LINK`, `WINDOWS_CSC_KEY_PASSWORD`를 설정해야 서명 빌드가 가능합니다. 내부 검증용 미서명 빌드는 `signed=false`로 실행하며, 서명/SmartScreen 통과를 의미하지 않습니다.

완료된 실행의 **howmuch-certificate-windows** artifact에서 설치 파일을 다운로드합니다. 보관 기간은 7일입니다. 자동 Release 게시나 자동 배포는 하지 않습니다.

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
