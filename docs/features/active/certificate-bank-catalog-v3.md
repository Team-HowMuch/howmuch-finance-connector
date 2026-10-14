# 인증서 앱 v3 은행 카탈로그 연동

- 이슈: #42
- 목적: 은행별 추가 입력을 Backend 공통 코드의 인증 수준과 일치시킨다.
- 스키마 변경: 없음. Backend의 V53 및 v3 카탈로그 배포가 선행되어야 한다.

## 구현 범위

- 공개 `GET /api/v3/banks`에서 활성 은행과 `authenticationLevel`을 조회한다.
- 수준 1·2에서는 계좌 입력을 숨기고, 수준 3에서는 계좌번호·계좌 비밀번호를 표시하고 필수로 받는다.
- 인증서 앱은 인증서 인증을 수행한다. ID/PW 연결 지원 여부는 모바일의 연결 방식 선택에 사용한다.
- 실패 안내창 재선택 버튼이 재시도 흐름을 직접 이어가며 비밀번호는 지운다.
- pairing·인증서 등록·모바일 승인 API는 기존 v2를 사용한다.
- 기존 수동 테스트 페이지의 dev 서버 변경은 이번 PR에서 제외한다.

## 커밋 경계

1. `feat(certificate)`: v3 카탈로그 연동과 입력 조건, 오류 재선택 보완, 관련 테스트·문서.
2. `build(certificate)`: 0.1.12-rc.2 dev 프리릴리즈 버전과 로컬 설치 파일 검증 결과.

## 검증

- Node 테스트 42개 및 실제 Electron 스모크 통과 (합성 API/인증서).
- Windows x64 NSIS, macOS arm64/x64 DMG 로컬 빌드 예정.
- 실제 CODEF·Windows PC·Intel native·Gatekeeper 다운로드 검증은 별도 필요.
- 새 앱은 Backend v3 카탈로그 배포 전 사용할 수 없다. v2로 조용히 전환하지 않는다.

## 2026-10-14 최종 로컬 배포 검증

- `npm test`: 42개 통과, `npm run test:smoke` 통과, `npm audit --omit=dev --audit-level=high`: 0 vulnerabilities.
- Windows x64 NSIS 및 macOS arm64/x64 DMG 빌드 통과. dev API 및 0.1.12-rc.2 버전으로 패키징했다.
- `verify-mac.cjs arm64`, `verify-mac.cjs x64`: deep strict ad-hoc 서명, hardened runtime, ASAR 내 dev origin, 실제 패키징 앱 startup 통과. x64 최초 Rosetta debugger 시작 시간 초과 후 빌드 부하가 끝난 상태의 재검증에서 통과했다.
- Windows ASAR의 버전·dev API·v3 호출과 PE header를 확인했다. Windows native 실행은 미검증이다.
- 설치 파일 3종 및 각각 SHA-256을 `dist/release/HowMuch-Certificate-0.1.12-rc.2-dev-*`로 준비했다.
- Connector `./gradlew clean build --no-daemon`: 21개 중 DB 의존 테스트 1개 실패. 기본 25432 PostgreSQL 부재이며 대체 테스트 DB 생성도 Docker read-only filesystem 오류로 실패했다. Java 변경은 없다.
- 원격 Actions는 조직 결제/사용 한도 문제로 job 시작 전에 차단됐다. 로컬 성공과 원격 상태를 구분해 기록한다.
- Backend PR: Team-HowMuch/howmuch-backend-v2#543. v3 API와 V53 배포 후 새 앱을 사용한다.
