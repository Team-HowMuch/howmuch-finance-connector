# Feature Plan: 앱 실행 초기 QR 연결

- Status: 구현·검증 완료 / 릴리스 CI 게시 진행 중
- Issue: #31 / Team-HowMuch/howmuch-backend-v2#517

## 목표·계약
초기 QR v2 HOWMUCH_CERTIFICATE_PAIRING으로 모바일 사용자를 연결한 후 PC 입력 화면을 연다. PC 인증 성공 후 두 번째 QR 없이 모바일 최종 Yes/No를 기다린다. 인증 오류를 PC에서 표시하며 BOUND는 완료 체크로 표시한다. 기존 USB·인증서 탐색, context isolation/sandbox, main 프로세스의 capability·파일 처리와 오류 allowlist를 유지한다.

초기 pairing과 certificate registration capability를 구분한다. PC capability와 raw 인증자료는 renderer에 반환하지 않고 QR 이미지만 반환한다. 초기 QR 만료/취소 후에만 새 pairing을 만들며 금융사 UNKNOWN은 자동 재등록하지 않는다. 모바일 화면은 별도 저장소에서 README의 QR v2 및 대기/approval API 계약으로 구현한다.

## 영향·검증
Java connector 스키마·메시지 계약은 변경하지 않는다. 앱 client/main/preload/renderer/HTML/CSS와 테스트·사용 문서를 변경한다. Node focused test와 실제 Electron smoke에서 초기 잠금, 만료/재발급, 모바일 연결 후 입력 허용, USB 합성 인증서, 인증 후 최종 승인 대기/6자리 코드/완료, 인증서 비밀번호 오류를 확인한다. Connector clean build와 Windows/macOS 패키지 startup CI를 검증한다.

## 커밋·게시
1. feat(certificate): 앱 시작 QR 연결과 인증 후 최종 승인 화면 적용.
2. docs(certificate): 초기 QR 모바일 연동과 서버 배포 조건 기록.
3. build(certificate): 0.1.8-rc.1 dev / 0.1.8 prod release 버전 게시.

서버 SQL/기능 PR을 자동 머지하거나 배포하지 않는다. 새 앱은 V50 및 Backend #517 API가 먼저 배포되어야 사용 가능하다. dev pre-release는 https://dev.howmuch.wars.p-e.kr, prod release는 https://api.howmuch.wars.p-e.kr를 사용한다. 사용자 요청대로 두 release를 게시하고 실제 Windows/macOS 빌드 첨부 여부까지 확인한다.

## 검증 결과
- Node 40개, 실제 Electron 초기 연결/만료·재발급/입력 잠금/USB fixture/PC 인증/최종 승인·6자리 코드/BOUND/비밀번호 오류 smoke 통과.
- Connector `./gradlew clean build --no-daemon`: Java 25개 성공. 기본 DB 미기동 및 도중 Docker 종료 이후 일회용 DB를 복구해 재검증했다. Backend 전체 Java 1,763개·JS 35개 통과.
- 첫 0.1.7 CI는 새 h1·초기 탐색 대기를 반영하지 못한 packaged smoke와 Intel 가상 러너 GPU 문제로 실패했다. 새 화면/입력 잠금/bridge를 검증하고 테스트 프로세스만 software rendering으로 실행하도록 수정했다. product macOS 그래픽 기본값은 유지한다.
- macOS arm64 실제 ASAR 패키지·ad-hoc 서명·공개 API origin·startup 검증 성공. 수정본 0.1.8-rc.1(dev) / 0.1.8(prod)을 게시했으며 0.1.7은 빌드 중단으로 표시했다. Windows/macOS CI Assets 첨부 결과는 아래에 기록한다.
- 모바일 UI는 이 저장소의 Electron 화면과 다르다. 모바일 저장소의 QR v2 대기/승인 연동과 서버 V50/#519 선행 배포가 필요하며 실제 모바일/실은행 성공을 주장하지 않는다.

## 최종 게시 결과
- dev pre-release `certificate-v0.1.8-rc.1` (af319bd), prod release `certificate-v0.1.8` (fab3887) 모두 release CI 성공. Windows x64 EXE, macOS arm64/x64 DMG 및 각각 SHA-256, 환경별 총 6개 Assets 첨부 확인.
- 체크섬 파일 3개씩을 다운로드해 형식·대상 파일명·GitHub 업로드 digest를 대조했다. 각 설치 파일의 실제 packaged startup과 macOS 서명/API origin 검증은 CI가 수행했다.
- App PR #32의 Windows/macOS packaged test 및 Connector build 통과. Backend #518 SQL CI 통과, #519는 최종 문서/토큰 redaction 커밋 후 CI 재검증 중이다.
- 현재 설치본을 사용하려면 V50/#519 서버 배포 및 모바일 QR v2 연동이 먼저 필요하다. 이번 요청대로 서버는 PR만 게시하고 merge/deploy하지 않았다.
