# Feature Plan: 인증서 앱 클라이언트 버전 표시
- Status: Completed — 로컬 구현 및 ARM 패키징 검증 완료
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
- 현재 작업은 로컬 수정이며 커밋·푸시·Release 갱신은 별도 요청 시 수행한다.

## 검증 결과
- Node 40개 테스트 통과.
- 실제 Electron smoke: 초기 QR/만료/인증/최종 승인/암호 오류 및 버전 표시 검증 통과. 테스트 엔트리로 실행하는 Electron fixture에서는 app.getVersion() 값을 기대하고, 실제 설치 패키지에서는 package.json 값을 기대한다.
- 실제 Mac arm64 패키징 및 `verify-mac.cjs arm64` 통과. 설치 패키지의 헤더 `Client ver. 0.1.9`가 package.json과 일치함을 확인. ad-hoc 서명 및 sandbox 유지.
- 헤더 스크린샷: apps/windows-certificate/dist/client-version-header.png.
- git diff --check 통과. Java 변경 없음으로 Gradle N/A. native Windows/Intel 검증은 미수행.
- 커밋·푸시·Release 변경 없음. 기존 공개 0.1.9 설치본에는 아직 반영되지 않았다.

## 게시 및 병합 계획 (2026-10-14)
사용자가 프리릴리스·정식 릴리스 게시, 불필요 PR 정리 및 main 병합을 승인했다. 새 버전은 0.1.10-rc.1(dev)과 0.1.10(prod). #32의 검증된 초기 QR 흐름을 develop에 먼저 병합하고 디자인·버전 표시를 별도 PR으로 반영한 뒤 develop→main PR을 병합한다. #30은 후속 버전에 포함되므로 닫는다. V1/Spring Boot 3.5 계약의 #7~#11은 현재 V2/RC2 구현으로 대체되어 닫되 원격 branch와 이력은 보존한다. #12의 독립 배포 구성은 유지한다. migration 및 서버 배포는 없다.
