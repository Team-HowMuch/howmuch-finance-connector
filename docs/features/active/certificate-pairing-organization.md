# Feature Plan: 모바일 선택 금융기관 고정

- Status: 로컬 구현·검증 완료, 원격 배포 전
- Version: 0.1.12-rc.3

## 목적·계약
모바일에서 선택한 은행을 PC 앱에서 다시 고르게 하지 않는다. 모바일 QR claim 요청은 `{qrToken, organization}`을 필수로 보내고 서버는 사용자와 은행을 함께 저장한다. 서버 PC polling은 등록 전에도 organization을 반환한다. PC dropdown은 해당 은행으로 선택되고 항상 disabled이다. level 3에만 계좌 입력을 표시하며 기존 생년월일 조건은 유지한다. 다른 은행 등록은 클라이언트와 서버 모두 차단하고 실패 후 재시도에도 유지한다.

## 범위와 저장
Backend SQL-only PR #550 및 후속 구현에 의존한다. 앱 저장 구조는 변경하지 않는다. 실제 모바일 저장소는 제공되지 않아 수동 테스트 HTML에서 모바일 은행 선택·전송을 구현한다. 기존 manual.html/manual-server.cjs 사용자 변경을 보존하며 필요한 부분만 수정했다.

## 예상 커밋
- fix(certificate): 모바일 금융기관 선택을 PC 인증에 고정
- docs(certificate): 모바일 은행 선택 계약과 검증 결과 기록

## 검증
- Node 단위 테스트 46개 통과.
- 실제 Electron smoke 통과: 모바일 은행 1/2/3 각 단계 고정·disabled 확인, level 3 계좌 필드 표시, PC 변경 시도 복구, 인증/실패 재시도/최종 승인/취소 기존 흐름.
- 실제 금융사 호출 및 dev 배포는 수행하지 않았다. 로컬 화면 검증은 synthetic API/provider 응답을 사용했다.
