# Electron 공동인증서 앱 저장소 분리

## 목적과 범위

Backend 작업 트리에서 개발한 독립 Electron 앱을 finance-connector 저장소의 `apps/windows-certificate`에 게시한다. 인증자료 접수·CODEF 실행·사용자 연결 저장·Kafka Inbox/Outbox는 현재 API에 유지한다. 기존 Java connector 거래 수집 코드는 바꾸지 않는다. 새 DB 구조나 Flyway migration은 필요하지 않다.

## 계약과 보안

Electron main에서 PC/USB 인증서 탐색과 HTTPS 업로드를 수행하고 sandbox renderer에는 공개 메타데이터와 제한된 IPC만 제공한다. 모바일 QR 예약과 Yes/No 뒤 CODEF 등록을 시작하며 BOUND일 때 완료한다. 비밀번호 오류는 공통 코드의 정적 문구로 표시한다. UNKNOWN 등록을 자동 재시도하지 않는다.

## 커밋 경계

1. `feat(certificate): Electron 인증서 탐색과 QR 연결 앱 추가` — main/preload/renderer, USB, 오류/완료, assets 및 package manifest.
2. `test(certificate): USB와 승인 완료 화면 회귀 검증 추가` — 합성 단위·실제 Electron smoke와 공개 테스트 인증서.
3. `ci(certificate): Windows 패키징과 설치 파일 빌드 구성` — lockfile, 빌드 origin 검증, signing gate 및 Windows workflow.
4. `docs(certificate): 앱 빌드와 USB·QR 사용 절차 정리` — root/app README와 계획/검증 결과.

각 커밋은 실행 가능한 프로젝트 구성을 유지한다. 패키징 스크립트와 lockfile은 첫 앱 커밋에 포함하고 CI 커밋은 workflow만 추가한다.

## 검증과 남은 사항

검증 예정: Node 단위 테스트, 실제 macOS Electron smoke, production dependency audit, connector `clean build`, 상대 링크 및 diff 검사. Windows CI에서 native startup/packaged renderer를 확인한다. 코드서명 설정, Windows 실제 USB/설치/SmartScreen, 모바일 화면 구현과 dev/prod 인증서 API 활성화는 별도 확인 대상이다. 커밋에 인증서 개인키·실제 계좌정보·인증 토큰·release-config·설치 파일을 넣지 않는다.

## 게시 전 검증 결과 — 2026-10-11

- Node.js 단위 테스트 26개 통과.
- 실제 macOS Electron smoke 통과: USB, 오류, QR 수신, BOUND 완료, 모바일 승인 경쟁 상태와 다시 가져오기.
- `npm audit --omit=dev --audit-level=high`: production 의존성 취약점 0개. `npm ci`가 보고한 moderate 8개는 개발/패키징 의존성에 있으며 이번 이동에서 강제 버전 변경하지 않았다.
- `SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon`: Java connector 및 contracts 14 tasks 성공. 기존 실행 DB와 별개의 임시 PostgreSQL DB에서 검증했다.
- JavaScript 9개 소스/빌드 파일 문법 검사 성공.
- 실제 Windows 실행/설치 파일 acceptance는 macOS에서 수행하지 않았으며 PR Windows CI와 #492 후속 확인으로 남긴다. 이번 게시에는 기존 dist 산출물을 포함하지 않는다.

## Windows CI 수정

첫 Windows CI에서 `defaultRoots("darwin", ...)`의 경로가 호스트의 Windows separator를 사용해 실패했다. Windows 분기는 기존 `path.win32`를 유지하고 macOS/Linux 분기는 `path.posix`를 사용하도록 수정했다. 이는 지정 platform에 따른 탐색 경로 계약을 운영체제와 무관하게 유지한다. 기존 경로 단위 테스트를 다시 실행하며 별도 `fix(certificate)` 커밋으로 게시한다.
