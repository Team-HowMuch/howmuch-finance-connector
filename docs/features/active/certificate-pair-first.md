# Feature Plan: 앱 실행 초기 QR 연결

- Status: 구현·검증 중
- Issue: #31 / Team-HowMuch/howmuch-backend-v2#517

## 목표·계약
초기 QR v2 HOWMUCH_CERTIFICATE_PAIRING으로 모바일 사용자를 연결한 후 PC 입력 화면을 연다. PC 인증 성공 후 두 번째 QR 없이 모바일 최종 Yes/No를 기다린다. 인증 오류를 PC에서 표시하며 BOUND는 완료 체크로 표시한다. 기존 USB·인증서 탐색, context isolation/sandbox, main 프로세스의 capability·파일 처리와 오류 allowlist를 유지한다.

초기 pairing과 certificate registration capability를 구분한다. PC capability와 raw 인증자료는 renderer에 반환하지 않고 QR 이미지만 반환한다. 초기 QR 만료/취소 후에만 새 pairing을 만들며 금융사 UNKNOWN은 자동 재등록하지 않는다. 모바일 화면은 별도 저장소에서 README의 QR v2 및 대기/approval API 계약으로 구현한다.

## 영향·검증
Java connector 스키마·메시지 계약은 변경하지 않는다. 앱 client/main/preload/renderer/HTML/CSS와 테스트·사용 문서를 변경한다. Node focused test와 실제 Electron smoke에서 초기 잠금, 만료/재발급, 모바일 연결 후 입력 허용, USB 합성 인증서, 인증 후 최종 승인 대기/6자리 코드/완료, 인증서 비밀번호 오류를 확인한다. Connector clean build와 Windows/macOS 패키지 startup CI를 검증한다.

## 커밋·게시
1. feat(certificate): 앱 시작 QR 연결과 인증 후 최종 승인 화면 적용.
2. docs(certificate): 초기 QR 모바일 연동과 서버 배포 조건 기록.
3. build(certificate): 0.1.7-rc.1 dev / 0.1.7 prod release 버전 게시.

서버 SQL/기능 PR을 자동 머지하거나 배포하지 않는다. 새 앱은 V50 및 Backend #517 API가 먼저 배포되어야 사용 가능하다. dev pre-release는 https://dev.howmuch.wars.p-e.kr, prod release는 https://api.howmuch.wars.p-e.kr를 사용한다. 사용자 요청대로 두 release를 게시하고 실제 Windows/macOS 빌드 첨부 여부까지 확인한다.
