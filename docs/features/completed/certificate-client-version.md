# Feature Plan: 인증서 앱 클라이언트 버전 표시
- Status: Completed — dev/prod 게시 및 main 병합 완료
- Updated: 2026-10-14

## 목적과 범위
설치한 인증서 앱의 버전을 상단 오른쪽에서 `Client ver. <version>`으로 확인한다. Slack의 About Slack 버전 확인 방식을 참고하되, 단일 창에서 바로 볼 수 있도록 표시한다.
참고: https://slack.com/intl/en-gb/help/articles/360048367814-Update-the-Slack-desktop-app

## 계약과 설계
- 기존 trusted IPC 경계를 재사용해 `certificate:app-version`에서 Electron `app.getVersion()` 문자열만 반환한다.
- sandbox preload에서 메서드 하나만 노출하고 renderer는 textContent로 표시한다. Node 접근을 허용하지 않는다.
- 서버 초기화와 별도로 가져와 서버 오류/QR 상태와 무관하게 표시한다. prerelease suffix를 그대로 보존한다.
- 앱 ID·서버 API·인증 흐름 변경 없음. Flyway: No. Domain/UseCase/Output Port는 N/A.
- 최신 develop에서 생성한 branch에 기존 공개 0.1.9 변경을 fast-forward해 현재 앱 기능을 보존한다.

## 구현 및 검증
- main/preload/renderer 및 상단 HTML/CSS 수정.
- 실제 Electron smoke에서 표시 버전이 package.json과 일치하는지 검사. 패키징 smoke도 같은 계약 확인.
- Node 테스트, 실제 Electron smoke, diff/status 확인. Java/서버 변경이 없어 Gradle은 N/A.
- 커밋 경계(요청 시): `feat(certificate): 앱 상단에 실제 클라이언트 버전 표시`.
- 초기 구현 단계에서는 로컬 수정만 수행했으며, 후속 사용자 요청으로 게시·main 병합을 진행했다. 최종 결과는 아래 게시 기록을 참조한다.

## 검증 결과
- Node 40개 테스트 통과.
- 실제 Electron smoke: 초기 QR/만료/인증/최종 승인/암호 오류 및 버전 표시 검증 통과. 테스트 엔트리로 실행하는 Electron fixture에서는 app.getVersion() 값을 기대하고, 실제 설치 패키지에서는 package.json 값을 기대한다.
- 실제 Mac arm64 패키징 및 `verify-mac.cjs arm64` 통과. 설치 패키지의 헤더 `Client ver. 0.1.9`가 package.json과 일치함을 확인. ad-hoc 서명 및 sandbox 유지.
- 헤더 스크린샷: apps/windows-certificate/dist/client-version-header.png.
- git diff --check 통과. Java 변경 없음으로 Gradle N/A. native Windows/Intel 검증은 미수행.
- 초기 구현 검증 시점에는 커밋·푸시·Release 변경이 없었다. 후속 게시 버전은 0.1.10이며 아래 최종 결과를 참조한다.

## 게시 및 병합 계획 (2026-10-14)
사용자가 프리릴리스·정식 릴리스 게시, 불필요 PR 정리 및 main 병합을 승인했다. 새 버전은 0.1.10-rc.1(dev)과 0.1.10(prod). #32의 검증된 초기 QR 흐름을 develop에 먼저 병합하고 디자인·버전 표시를 별도 PR으로 반영한 뒤 develop→main PR을 병합한다. #30은 후속 버전에 포함되므로 닫는다. V1/Spring Boot 3.5 계약의 #7~#11은 현재 V2/RC2 구현으로 대체되어 닫되 원격 branch와 이력은 보존한다. #12의 독립 배포 구성은 유지한다. migration 및 서버 배포는 없다.

## 2026-10-14 게시 및 main 반영 결과
이전 절의 로컬 구현 상태 기록을 대체하는 최종 게시 결과다.

- dev prerelease: certificate-v0.1.10-rc.1, API https://dev.howmuch.wars.p-e.kr.
- prod Latest: certificate-v0.1.10, API https://api.howmuch.wars.p-e.kr.
- 각 Release에 Windows x64 NSIS, Mac arm64/x64 DMG 및 각각의 SHA-256을 첨부했다. 기존 태그/이전 버전 설치 파일은 변경하지 않았다.
- GitHub Actions run 38485823139 / 38485852541 및 PR CI는 계정 결제 실패 또는 spending limit annotation으로 runner 시작 전에 차단됐다. 코드 테스트 실패와 구분하며 CI 성공으로 처리하지 않는다. workflow/check 설정을 끄거나 관리자 우회하지 않았다.
- 사용자 요청을 완료하기 위해 태그의 정확한 source에서 로컬 빌드했다. Node 40개, 실제 Electron smoke, 별도 PostgreSQL의 Gradle clean build 21개(실패/오류/skip 0) 통과.
- Mac ARM 실제 실행 및 패키징 서명/API origin/Client ver. 확인. Mac x64는 ARM Mac의 Rosetta에서 같은 항목 확인; prod 첫 실행은 60초 debugger 대기 초과, 재실행 통과. native Intel OS 검증을 대신하지 않는다.
- Windows는 정상 builder 설정으로 macOS에서 교차 빌드하고 app.asar의 version/API origin/버전 IPC 포함을 확인했다. native Windows 설치·실행과 다운로드 후 Gatekeeper 검증은 미수행이다.
- 각 바이너리의 GitHub SHA-256 digest와 다운로드 checksum 파일의 hash/파일명이 일치하는지 확인했다. 두 태그가 origin/main의 ancestor임을 확인했다.

### PR 정리와 병합
- #7~#11: 구 V1/Spring Boot 3.5 기반 누적 PR. 현재 V2/RC2 구현으로 대체되어 닫음. 변경 이력과 원격 branch 보존.
- #30: 0.1.6 준비 커밋이 후속 초기 QR PR #32에 포함되어 중복 PR을 닫음.
- #32: 검증된 초기 QR 흐름을 develop에 merge.
- #33: 브랜드 DMG와 클라이언트 버전 표시를 develop에 merge.
- #34: develop→main merge 완료. main/develop 파일 내용 일치 확인.
- #12: 독립 배포 구성이 남아 있어 유지. 이 작업에서 merge·서버 배포하지 않음.

Backend V50/#517 및 모바일 QR v2 선행 배포 조건과 dev/prod 동일 앱 ID의 동시 설치 제한은 Release notes에 유지했다. 실제 금융사 연결은 이번 게시 작업에서 재검증하지 않았다.
