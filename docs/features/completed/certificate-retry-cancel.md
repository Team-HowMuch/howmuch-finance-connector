# Feature Plan: 인증 실패 재선택과 종료 취소
- Status: 구현 및 로컬 검증 완료
- Updated: 2026-10-14

확정 FAILED이면 비밀번호·계좌 입력을 지우고 같은 휴대폰 연결을 유지하며 인증서를 다시 탐색/선택한다. UNKNOWN/통신 결과 불명은 자동 재시도하지 않는다. Backend의 FAILED 등록 재생성 지원이 선행되어야 한다. migration: backend V51(SQL-only PR #523) 병합 완료.

PC 정상 종료는 진행 중 업로드/탐색 종료 후 취소 요청을 기다린다. 취소 결과 불명이면 창을 유지하고 재시도를 안내한다. 완료된 금융 연결은 지우지 않는다. PAIRED 이후에도 PC는 pairing 취소/만료를 감시한다.

모바일 소스는 사용자가 추후 제공하므로 모바일 구현은 제외한다. Backend `DELETE /api/v2/certificate-pairings/{id}` JWT 소유자 API를 모바일 화면 이탈/정상 종료 시 호출하도록 연동 문서를 제공한다. 강제 종료·네트워크 단절 시 즉시 취소를 보장하지 않으며 기존 만료를 사용한다. heartbeat는 후속 설계다.

검증: Node retry 상태 경계, 실제 Electron 실패→재선택→재시도 및 close→취소 응답 이전 창 유지/이후 종료. backend HTTP 테스트 별도. 사용자 후속 요청으로 커밋/푸시/PR 및 로컬 빌드 릴리즈·프리릴리즈 게시를 진행한다.
예상 커밋: `fix(certificate): 인증 실패 재선택과 종료 취소 대기 적용`.

## 후속 구현 및 검증
- FAILED만 기존 pairing에서 재선택한다. 입력 암호와 선택을 비우고 공통 오류 문구를 표시한다. UNKNOWN은 자동 재시도하지 않는다.
- PAIRED 이후에도 취소/만료 상태를 감시하며, 오래된 비동기 결과는 화면을 덮어쓰지 않는다.
- 정상 창 닫기/앱 종료는 진행 중 요청과 취소 응답을 기다린다. 취소 실패 시 창을 유지한다. renderer 비정상 종료에서도 capability를 먼저 지우지 않고 종료 취소를 시도한다.
- Node 테스트 41개와 실제 Electron smoke에서 실패 복귀/암호 안내/동일 QR 재인증/모바일 취소/닫기 응답 대기를 검증한다. 실제 CODEF와 실제 모바일 lifecycle 테스트는 이번 검증에 포함하지 않는다.
- backend V51 및 모바일 DELETE API를 각 서버에 먼저 배포해야 한다. 0.1.11-rc.1(dev)과 0.1.11(prod)의 Windows x64, Mac arm64/x64 설치 파일을 로컬 빌드한다.

## 게시 결과
관련 이슈 #37. 커밋은 동작 수정, 검증/문서, dev 사전 릴리즈 버전, prod 정식 버전으로 나눈다. 서버 후속 PR은 Team-HowMuch/howmuch-backend-v2#525를 연결한다. GitHub Actions 대신 로컬 빌드/검증 후 release asset을 업로드한다.


## 0.1.11 로컬 빌드 및 원격 게시 검증
- dev: certificate-v0.1.11-rc.1 / https://dev.howmuch.wars.p-e.kr / tag source 9da1a87.
- prod: certificate-v0.1.11 / https://api.howmuch.wars.p-e.kr / tag source 780f088.
- 양쪽 Windows x64 NSIS, macOS arm64/x64 DMG와 SHA-256을 로컬에서 빌드했다.
- 양쪽 Mac codesign --verify --deep --strict, ad-hoc/hardened runtime, ASAR API origin 및 실제 packaged startup smoke 통과. Intel은 Rosetta 실행이며 native Intel 검증은 아니다.
- hdiutil verify로 각 DMG 체크섬을 검증했다. Windows ASAR 버전/API 주소는 확인했고 실제 Windows 설치·실행은 미검증이다.
- Node 41개·Electron smoke 통과, runtime npm audit 취약점 0건.
- 앱 저장소 ./gradlew clean build --no-daemon 통과: 21개 테스트 실패/건너뜀 0건. 첫 실행의 기본 테스트 DB 부재를 전용 PostgreSQL18 컨테이너로 해결하고 재실행했다. 작업용 컨테이너는 제거했다.
- PR #38 → develop. 이슈 #37. backend PR Team-HowMuch/howmuch-backend-v2#526은 develop에 병합되었다.
- 앱 PR Actions는 account payments/spending limit으로 runner 시작 전 차단되었다. 로컬 빌드 결과와 CI 성공을 구분한다.
- 서버 환경의 새 API 배포와 모바일 lifecycle 실제 연동은 릴리즈 설치 파일 게시와 별개이다.
