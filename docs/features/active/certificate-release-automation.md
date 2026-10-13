# Feature Plan: 인증서 앱 릴리스 자동 빌드

- Status: 로컬 구현·검증 완료, Windows/macOS 로컬 구현·검증 완료, GitHub 설정 완료, 원격 실행 대기
- Last updated: 2026-10-13
- Related issue: [#27](https://github.com/Team-HowMuch/howmuch-finance-connector/issues/27)
- Related PR: 기존 앱 [#25](https://github.com/Team-HowMuch/howmuch-finance-connector/pull/25)의 후속
- Related ADR: N/A (기존 Electron/Backend 경계 유지)

## 1. 목적과 성공 기준

수동 설치본 빌드를 GitHub Release 게시에 연결한다. API 주소를 dev/prod별 GitHub Environment 변수로 주입하고 설치 파일과 SHA-256을 같은 Release에서 받는다.

- [x] `certificate-v<package.json version>` Release 게시 시 Windows x64 NSIS 설치본 자동 생성·첨부
- [x] 사전 릴리스는 dev, 정식 릴리스는 prod 설정 사용
- [x] 환경변수 누락·잘못된 주소·태그/버전 불일치는 빌드 실패
- [x] PR의 기존 Windows unit/Electron/패키징 검증 및 수동 빌드 유지
- 제외: 자동 업데이트, 서명 인증서 구매, 서버 변경·배포, 실제 Release 생성. macOS 배포는 이후 승인된 7절에서 추가한다.

## 2. 경계와 계약

기존 `apps/windows-certificate`와 `.github/workflows/certificate-windows.yml`만 동작 변경한다. 인증서 탐색·업로드·모바일 승인 API는 변경하지 않는다. 새 UseCase, Port, Domain, Application Policy, Web DTO는 N/A다.

- Flyway migration required: **No**. DB/Redis/Kafka 변경 없음.
- `HOWMUCH_CERTIFICATE_API_ORIGIN`: 빌드 시 HTTPS origin. 기존 개발 앱의 같은 환경변수 이름을 재사용한다. `configure` 명령의 명시적 인자가 우선한다.
- `certificate-windows-dev`, `certificate-windows-prod`: GitHub Environments. 각각 위 이름의 Variable로 API 주소를 설정한다. dev는 `https://dev.howmuch.wars.p-e.kr`, prod는 사용자가 지정한 `https://api.howmuch.wars.p-e.kr`로 등록한다. 설치본 빌드에는 해당 환경변수를 필수로 사용한다.
- `HOWMUCH_REQUIRE_SIGNING`: Windows 전용 Environment Variable. 미설정이면 기존처럼 서명 필수. 사용자가 미서명 진행에 동의해 두 환경에 `false`를 등록했다.
- `WINDOWS_CSC_LINK`, `WINDOWS_CSC_KEY_PASSWORD`: 해당 Environment의 기존 서명 Secret 이름 재사용.
- 수동 실행은 `target_environment`로 dev/prod를 선택하고 optional `api_origin`으로 주소를 재정의한다. `signed` 입력을 유지하며 현재 미서명 방침에 맞춰 기본값은 false로 둔다. Mac 임시 서명에는 영향이 없다.
- 앱 전용 `certificate-v` 태그만 처리하며 package version과 정확히 일치해야 한다. 사전 버전 태그는 사전 릴리스로 게시한다. 정식 버전은 정식 릴리스로 게시한다.
- 설치본은 `release-config.json`의 주소를 사용한다. 설치 후 임의 환경변수에 의한 주소 교체는 허용하지 않는다.

## 3. 흐름과 실패 정책

1. Release `published` 또는 수동 실행 → 대상/버전 검증.
2. 기존 Windows 테스트, 실제 Electron smoke와 fixture 패키징 검증.
3. dev/prod Environment에서 주소/서명 설정을 읽고 HTTPS 검증 후 패키징.
4. 실제 패키징 앱 startup 검증 후 환경명이 포함된 설치 파일과 SHA-256 생성.
5. Release 이벤트에서만 같은 태그의 Release Assets로 업로드. 수동 실행은 Actions artifact만 제공.

서명 필수인데 키가 없거나 API 주소가 비면 실패한다. electron-builder 자동 게시를 끄고 검증 완료 후에만 업로드한다. 동일 태그 재실행은 해당 환경의 같은 asset을 교체한다. contracts 릴리스와 Java CI에는 영향이 없다.

## 4. 영향과 구현 계획

| 영역 | 영향 |
|---|---|
| 빌드 | configure와 beforePack에서 기존 HTTPS 검증 재사용, 명시적 env 지원 |
| CI | 기존 workflow에 Release 대상 선택·버전 검증·환경별 설정·asset 업로드 추가 |
| 보안 | 공개 API origin만 설치본에 포함, 서명 비밀은 CI에만 주입 |
| 문서 | 루트/앱 README에 릴리스·환경변수 설정과 다운로드 설명 |
| 서버 | 변경 없음 |

예상 커밋 경계 (현재 커밋/푸시/PR 하지 않음):

1. `build(certificate): 환경변수 기반 설치본 설정과 검증 추가`
2. `ci(certificate): 환경별 Release 자동 빌드와 사용 가이드 추가`

## 5. 검증

- Node unit tests: env/명시적 인자 우선순위, 빈 값·HTTP·경로·자격정보 주소 거부, release 버전/채널 선택, 서명 필수/미서명 분기.
- 실제 Electron smoke 및 로컬 설치본 패키징. Windows 네이티브 실행은 Windows Actions에서 확인한다.
- Actions workflow 정적 검증, 문서 상대 링크, `git diff --check`, 최종 status.
- Java/DB/Kafka는 변경하지 않아 Gradle 검증은 N/A. PR 게시도 이번 범위 밖이다.

## 6. 결과 및 인계

| 검증 | 결과 |
|---|---|
| `npm test --prefix apps/windows-certificate` | 32개 통과, 실패/건너뜀 0 |
| `npm run test:smoke --prefix apps/windows-certificate` | 실제 Electron에서 USB/QR/오류/예약/완료/창 제어 통과 (합성 API) |
| `npm audit --omit=dev --audit-level=high --prefix apps/windows-certificate` | 취약점 0 |
| `actionlint .github/workflows/certificate-windows.yml` | 통과 |
| Windows NSIS cross-build | 성공, 114,430,210 bytes. `dist/verify-prod/verification.json`에 SHA-256 기록. prod **fixture** origin이 ASAR에 포함됨을 검증 |
| macOS 검증용 패키지 | dev **fixture** origin 포함 검증. 서명 자동 탐색을 끈 최초 패키지는 실행 실패했고, 로컬 ad-hoc 재서명 후 실제 packaged startup smoke 통과 |
| 변경 문서 상대 링크 / `git diff --check` | 통과 |

검증 명령은 앱 폴더에서 `HOWMUCH_CERTIFICATE_API_ORIGIN=https://prod.certificate-api.example.test HOWMUCH_REQUIRE_SIGNING=false CSC_IDENTITY_AUTO_DISCOVERY=false npm run dist:win -- --config.directories.output=dist/verify-prod`를 사용했다. macOS 검증은 `--mac dir --arm64 --publish never --config.directories.output=dist/verify-dev`와 `test/packaged-smoke.cjs`를 사용했다. 위 설치본은 실제 API에 연결하지 않는 fixture 전용 미서명 파일이며 사용자 배포용이 아니다. 검증 후 개발 설정은 알려진 dev API 주소로 복원했다.

- 작업 branch: `build/certificate-release-automation` (최신 `origin/develop` 기준).
- 커밋·푸시·PR·Release 생성은 하지 않았다. GitHub 환경의 공개 API 주소 설정만 사용자 응답에 따라 등록했다. 기존 Backend netting 변경에는 손대지 않았다.
- 2026-10-13 사용자 응답으로 prod origin을 `https://api.howmuch.wars.p-e.kr`로 확정했다. GitHub 두 Environment의 `HOWMUCH_CERTIFICATE_API_ORIGIN` 등록·조회 검증 결과는 아래에 기록한다. 서명 Secret 또는 명시적 미서명 설정과 원격 Release 실행은 별도 확인 대상이다.
- 실제 GitHub Actions/Release 게시·Windows 실기기·코드서명은 원격 설정과 게시 이후 확인해야 한다.

### GitHub 환경 등록 결과 (2026-10-13)

| Environment | Variable `HOWMUCH_CERTIFICATE_API_ORIGIN` | 검증 |
|---|---|---|
| `certificate-windows-dev` | `https://dev.howmuch.wars.p-e.kr` | GitHub API 재조회 일치, 기존 HTTPS validator 통과 |
| `certificate-windows-prod` | `https://api.howmuch.wars.p-e.kr` | GitHub API 재조회 일치, 기존 HTTPS validator 통과 |

두 환경에 `WINDOWS_CSC_LINK`, `WINDOWS_CSC_KEY_PASSWORD`가 없음을 이름 목록으로 확인했다. 서명 기본값 true를 유지했으며 자동으로 미서명 빌드로 낮추지 않았다. 이후 사용자의 미서명 진행 승인에 따라 두 환경에 `HOWMUCH_REQUIRE_SIGNING=false`를 등록하고 재조회했다. 서명 Secret 없이도 Windows 릴리스 빌드가 가능하다. Release/커밋/푸시는 실행하지 않았다.

## 7. macOS 임시 서명 배포 확장 (2026-10-13)

사용자가 Developer ID 없이 임시 서명으로 진행하도록 결정했다. 동일 branch에서 기존 Windows 빌드에 macOS Apple Silicon(`arm64`)과 Intel(`x64`) DMG를 추가한다. 서버/API/DB 계약은 변경하지 않으며 migration은 필요 없다.

### 설계와 성공 기준

- [x] `mac.identity: "-"`로 ad-hoc 서명, `notarize: false` 명시. hardened runtime과 기존 sandbox/fuse를 유지하며 Electron에 필요한 builder 기본 entitlement를 사용한다.
- [x] PR에서 native `macos-15` arm64 / `macos-15-intel` x64 runner로 unit/smoke/패키징 서명 및 startup 검증.
- [x] Release/수동 빌드는 기존 dev/prod Environment 주소를 공유한다. Windows 서명 옵션은 Windows에만 적용한다.
- [x] arm64/x64 DMG와 SHA-256을 생성한다. 모든 플랫폼 검증 완료 후 한 게시 job에서 Release Assets를 첨부해 부분 게시를 줄인다.
- [x] README에 설치·OS별 파일 선택·미공증 앱의 열기 절차를 안내한다. Gatekeeper 신뢰 또는 공증 성공으로 표현하지 않는다.

현재 설치된 electron-builder **26.15.3**의 `MacTargetHelper`, 기본 entitlement와 fuse→sign 순서를 확인했다. v27의 `mac.sign` 설정을 복사하지 않고 v26의 `mac.identity` 계약을 사용한다. ad-hoc 빌드에는 Apple 인증정보를 주입하지 않는다. PR에서 builder가 서명을 건너뛰는 것을 막기 위해 테스트 job에만 `CSC_FOR_PULL_REQUEST=true`를 설정하며 해당 job에는 Secret/Environment를 연결하지 않는다.

GitHub Runner의 CPU 변경에 영향을 받지 않도록 [공식 runner 목록](https://github.com/actions/runner-images)의 native OS/CPU label을 고정한다. [electron-builder v26 서명 안내](https://www.electron.build/v26/docs/features/code-signing/code-signing-mac/)와 [Apple 실행 안내](https://support.apple.com/en-us/102445)를 따른다.

예상 추가 커밋: `ci(certificate): macOS 임시 서명 DMG와 플랫폼별 검증 추가`. 실제 commit/push/PR/Release 게시 없이 로컬 구현·검증한다. 사용자가 앞서 Windows 미서명 진행에도 동의했으므로 두 GitHub Environment에 `HOWMUCH_REQUIRE_SIGNING=false`를 등록한다.

### 검증 계획

실제 arm64 DMG 생성→codesign deep strict 및 ad-hoc 확인→패키징 startup→DMG mount·포함 앱 검증을 수행한다. x64는 cross-build와 서명 확인을 수행하며 실제 Intel 네이티브 실행은 Actions에서 확인한다. 설치 파일 이름/환경 구분/체크섬과 누락 파일·잘못된 채널을 Node 테스트로 검증한다. 원격 Actions와 실제 인터넷 다운로드의 quarantine/Gatekeeper 동작은 로컬 테스트로 대신하지 않는다.

### macOS 구현·검증 결과

- `npm run dist:mac`: dev API를 포함한 arm64/x64 DMG 모두 생성 성공. `identity: "-"`, `notarize: false`, `hardenedRuntime: true`를 명시했다.
- 두 패키지와 DMG를 실제 mount해 내부 앱의 `codesign --verify --deep --strict` 및 `Signature=adhoc`를 확인했다. Applications shortcut도 확인했다.
- arm64 packaged startup 통과. x64는 최초 Rosetta 실행에서 기존 공유 20초 budget으로 debugger/탐색 완료 검증이 실패했다. 검증 도구의 시작(60초)/renderer 준비(30초) budget을 분리하고 전체 watchdog도 두 budget의 합+10초로 맞춘 뒤 x64 packaged startup 통과. 검사 조건을 생략하거나 완화하지 않았다. 실제 Intel native 실행은 원격 Actions에서 추가 확인해야 한다.
- `verify-mac.cjs`가 최종 코드 서명·hardened runtime·ASAR 내 API origin·실제 패키징 앱 실행을 검증한다.
- `npm test`: 35개 통과(실패/건너뜀 0). 설치 파일의 CPU/환경 구분, SHA-256 일치, 잘못된 좌표/누락 파일 거부를 포함한다.
- `npm run test:smoke`: 실제 Electron 합성 시나리오 통과. `npm audit --omit=dev --audit-level=high`: 취약점 0.
- `actionlint`, 변경 문서 상대 링크, `git diff --check`: 통과. Java/DB/Kafka 코드 변경이 없어 Gradle은 실행하지 않았다.
- 두 GitHub Environment에 `HOWMUCH_REQUIRE_SIGNING=false`를 등록·재조회했다. 수동 실행의 Windows `signed` 기본값도 false다. Mac에는 서명 Secret이나 Apple 인증정보를 주입하지 않는다.
- Windows/Mac artifact 준비와 SHA-256 생성을 Node 스크립트로 공통화했다. 배포 권한은 모든 플랫폼 성공 후 실행하는 `publish` job에만 부여했다.
- 로컬 결과물: `apps/windows-certificate/dist/release/얼마-인증서-가져오기-0.1.5-dev-arm64.dmg`, `...-dev-x64.dmg` 및 각 `.sha256`. dev 서버용이며 운영 배포본이 아니다.
- 커밋·푸시·PR·Release 게시·원격 Actions 실행은 하지 않았다. Apple 공증, 인터넷 다운로드 후 Gatekeeper 동작, 실제 Intel native 실행은 미검증이다.

## 8. 원격 게시 작업 (2026-10-13)

사용자가 커밋·푸시 및 Release 게시를 요청했다. 최초 게시 버전은 `0.1.6-rc.1`, 태그는 `certificate-v0.1.6-rc.1`로 정하고 dev API를 사용하는 사전 릴리스로 게시한다. PR은 저장소 규약에 따라 `develop`을 대상으로 열고, Release는 해당 feature branch의 정확한 commit을 태그로 고정한다. PR을 임의로 머지하지 않는다.

이번 게시의 실제 커밋 경계:
1. `build(certificate): 환경별 설정과 임시 서명 설치본 빌드 추가` — 앱 빌드/검증 스크립트·테스트 및 package/lock 버전.
2. `ci(certificate): 환경별 데스크톱 Release 자동 배포 구성` — workflow·README·Feature Plan.

- Node 35개 테스트와 actionlint 재검증 통과.
- `./gradlew clean build --no-daemon` 통과: 25 tests, failures/errors/skipped 0. 별도 PostgreSQL 컨테이너(35432)와 test profile을 사용했다. 기존 25432/15432 컨테이너는 변경하지 않았다.
- 원격 Actions 결과와 Assets 3개 + SHA-256 3개를 확인한 뒤 기록한다.

### 첫 원격 실행과 rc.2 수정

- `certificate-v0.1.6-rc.1`의 Release run `38367486066`에서 Windows 테스트/패키징 실행은 통과했지만 Mac arm64 smoke가 실패했다. `certificate:init`이 합성 API listen 이전에 실행되는 초기화 경쟁을 로그로 확인했다. 같은 commit의 PR arm64 smoke는 통과해 타이밍 의존임을 확인했다.
- 테스트 파일/USB fixture 생성과 HTTP bind를 동기적으로 먼저 수행하고, 첫 await 전에 실제 앱을 import해 Electron privileged scheme 등록의 before-ready 계약도 유지하도록 수정했다. 제품 앱 동작은 변경하지 않았다.
- 기존 태그를 이동하지 않고 `0.1.6-rc.2` 새 버전/태그로 재게시한다. 설치 파일이 없는 rc.1은 실패 이력을 남기고 rc.2를 안내한다.
- 원격 Connector CI `38367456226` 성공, 로컬 Gradle 총 25개 및 Node 35개 통과.
