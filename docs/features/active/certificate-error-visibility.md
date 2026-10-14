# 인증 실패 후 재선택 화면 및 스크롤 정리
- Status: 로컬 수정, 이번 화면 변경 검증 대기
- Updated: 2026-10-14
- Scope: PC 인증서 앱의 실패 재선택 UX와 스크롤 영역. DB/외부 계약 변경 없음, migration 불필요.
- 확인 근거: 0.1.11의 FAILED 재선택 smoke에서 message rect가 innerHeight 밖에 위치한다. 최근 dev CF-12805/CF-13020은 기존 공통코드로 변환되지만 안내가 입력 폼 아래에 있다.
- 수정: FAILED 내용을 모달에서 한 번 안내하고, 확인 후 빨간 인라인 경고 없이 재선택 폼으로 돌아간다. 스크롤은 메인 영역 한 곳에서만 발생하도록 인증서 목록 내부 스크롤을 없앤다. 재선택 시 선택 금융사·저장 위치 유지와 민감 입력 폐기 규칙을 유지한다.
- 확인 필요: 현재 pairing 모바일 요청은 QR 토큰만 보내며, 은행 선택은 PC가 등록 API에 전달한다. 휴대폰에서 선택한 은행을 PC에 고정하려면 모바일과 백엔드가 금융사 코드를 전달·보관하도록 연동해야 한다.
- 검증: 기존 버전은 실제 Electron에서 모달/재시도와 취소 시나리오를 확인했다. 이번 UI 조정 이후 Electron 재검증은 아직 하지 않았다.
- 예상 커밋 경계: fix(certificate): 인증 실패 재선택 화면 정리. 이번 요청에서는 로컬 수정만 수행한다.

## 결과
- 수정 전 실제 Electron 테스트: Authentication failure must be visible without scrolling 단언 실패(rect가 viewport 밖).
- 수정 후 동일 테스트와 alert/focus 단언 통과, 기존 재인증/모바일 취소/종료 smoke 전체 통과. Node 41개 통과, git diff --check 통과.
- dev 로그에서는 CF-12805 이후 CF-13020 두 건을 확인했다. CODEF 원문/인증정보는 문서에 기록하지 않는다.
- 설치된 0.1.11/0.1.11-rc.1에는 아직 이번 UI 수정이 반영되지 않았다. 신규 설치 파일 게시와 실제 금융사 재시도는 미수행이다.

## 사용자 영상 확인 후 수정
- 영상에서는 하단 안내가 보이지만, 실패 안내창 없이 재선택으로 바로 복귀했다. 앞선 offscreen 설명만으로는 이 영상의 문제를 설명하지 못한다.
- FAILED는 공통코드 오류를 modal dialog로 먼저 표시하고, 다시 선택/Escape 확인 후 복귀한다.
- discover("auto")가 내 컴퓨터 선택을 USB로 바꾼다는 코드상 원인을 확인했다. 제출 시 금융사·저장 위치만 snapshot하고 재시도 복귀에서 유지한다. 암호·계좌 등 민감 입력은 보관하지 않는다.
- 영상의 금융사 변경은 관찰됐으나 정확한 변경 시점 원인은 미확정이다. 제출 값을 hidden 전 읽고 복귀 시 동일 값을 유지하도록 보강했다. 서버 로그/금융사 선택 값의 일치 여부는 별도 실제 재시도에서 확인해야 한다.
- 실패 안내창 확인 전 입력 폼 hidden 유지, 확인 후 같은 금융사·저장 위치/오류 상단 노출을 smoke로 검증한다.

## 영상 대응 검증 결과
- 수정 전 Failure requires an explicit acknowledgement 단언 실패. 수정 후 dialog 표시/확인 전 폼 숨김/확인 후 복귀·상단 안내·동일 금융사/내 컴퓨터 저장 위치 유지 통과.
- USB가 연결된 상태에서 내 컴퓨터로 선택 후 재시도하는 실제 Electron 시나리오 통과. Node 41개, diff --check 통과.
- 로컬 개발용 0.1.12-dev.1을 별도 dist/local-error-fix에 빌드했다. API dev, ad-hoc codesign deep strict 검증, 실제 packaged startup smoke 및 버전 확인 통과 후 새 창으로 실행했다.
- 기존 설치 앱은 덮어쓰거나 종료하지 않았다. 원격 commit/release 게시 및 실제 인증정보 재제출은 수행하지 않았다.

## 화면 피드백 반영
- 실패 사유는 모달에서만 보여주고, 확인 후 재선택 화면의 중복 빨간 경고를 지운다.
- 앱 바깥과 인증서 목록 안에 각각 스크롤이 생기지 않도록 메인 콘텐츠만 스크롤되게 하고 목록은 내용 높이만큼 표시한다.
- 휴대폰 금융사 선택은 현재 코드에 없다. 모바일 저장소에서 인증서 pairing 흐름도 찾지 못했고, 백엔드 claim은 QR 토큰만 받는다. 선택 은행 고정은 해당 데이터 전달을 연결한 뒤 구현한다.

## 로컬 모바일 테스트 클라이언트
- 기존 HTML을 실제 로그인·QR 연결·상태 조회·승인 요청·최종 YES/NO·연결 목록·계좌 후보 조회 클라이언트로 확장했다. 스키마 변경은 없다.
- loopback 프록시는 허용된 경로/메서드와 계좌 조회 organization 파라미터만 전달한다. 로그인 requestToken은 제거하고 accessToken만 메모리에 보관한다.
- 로컬 가상 계정과 CODEF mock 가상 인증서를 준비했다. 설치본과 구분하도록 개발 버전은 0.1.12-dev.3이다.
- QR 이미지는 로컬 jsQR fallback으로 해독해 BarcodeDetector 미지원 브라우저에서도 사용할 수 있다.
- 검증: 실제 로컬 API+Kafka+CODEF mock으로 로그인부터 BOUND와 계좌 후보 1개 조회까지 통과, 앱 Node 41개 및 mock 3개 통과. 브라우저/네이티브 전체 사용자 클릭 흐름은 별도 수동 확인 대상이다.
- 예상 커밋 경계: test(certificate): 로컬 모바일 연결 테스트 페이지 추가. 이번 요청은 로컬 실행이며 commit/push하지 않는다.

## PR 및 프리릴리즈 게시 계획
- 사용자 요청으로 이슈 #39와 `fix/certificate-error-visibility` 브랜치에서 게시한다.
- 커밋 경계: 실패 모달·재선택·스크롤 및 smoke / 로컬 모바일 테스트 HTML·프록시·문서 / 0.1.12-rc.1 버전과 릴리즈 검증 기록.
- 설치본은 dev HTTPS API를 사용하며 local mock/test HTML은 설치 패키지에 포함하지 않는다.
- Node 테스트 41개와 실제 Electron smoke 통과. Mac arm64 실제 packaged startup 및 ad-hoc signature 검증 통과.
