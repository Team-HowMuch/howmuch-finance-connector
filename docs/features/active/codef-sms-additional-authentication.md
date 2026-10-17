# Feature Plan: 인증서 SMS 추가 인증 대기 표시

## 2026-10-17 중앙 모달 연동
- PC에는 모바일 입력/확인 중/재입력 상태만 표시한다. SMS 타이머 표시만 제거하며 세션 만료/취소는 유지한다.
- 안전한 AUTH_SMS_RETRY_REQUIRED 상태일 때만 재입력 안내를 표시한다. 인증번호/continuation/개인키는 IPC에 추가하지 않는다.
- 기존 pairing polling과 최종 승인 경로를 유지한다. unit/smoke로 대기→재입력→확인 중→승인 및 취소를 검증한다. DB 변경은 없다.

- 상태: 로컬 구현 완료, 사용자 승인에 따른 PR 및 dev/prod 릴리즈 발행 준비.
- 서버는 기존 인증서를 REGISTERING으로 유지하면서 additionalAuthentication으로 SMS 입력 대기를 제공한다.
- PC는 인증번호를 입력받지 않고 휴대폰 입력 안내만 표시한다. 모달 열기/닫기와 인증번호 제출은 모바일 API의 책임이다.
- 인증번호/재개 토큰/개인키는 renderer로 전달하지 않는다. Client에서 안전한 상태·만료시각만 전달한다.
- 테스트: 상태 전달, 민감 필드 제외, 기존 취소/재시도·패키징 회귀.
- DB 변경 없음. 별도 워크트리에서 src/client.cjs, src/renderer.js와 관련 테스트만 변경한다.
- 커밋 경계: PC SMS 대기 표시·테스트·문서, dev 프리릴리즈 버전, prod 정식 버전으로 구분한다.

## 2026-10-18 발행 계획
- develop 대상 기능 PR과 별도로 certificate-v0.1.13-rc.1(dev), certificate-v0.1.13(prod)을 게시한다.
- 각 태그의 package.json/package-lock.json 버전을 일치시키고 기존 release workflow와 Environment 변수를 사용한다.
- Windows x64, macOS arm64/x64 설치 파일 및 SHA-256 첨부를 확인한다. macOS는 기존 ad-hoc 서명을 유지한다.
- 0.1.12에 이미 배포됐지만 develop에 병합되지 않은 입력 오류 모달 수정 커밋을 유지한다. 닫힌 PR #47의 변경을 제거해 기존 앱 동작을 되돌리지 않는다.
- 백엔드/모바일 PR은 이번 발행 범위에 포함하지 않는다. 새 SMS 상태를 반환하지 않는 서버에서는 기존 인증 흐름을 유지한다.

## 검증 결과

### 2026-10-18 발행 전 재검증
- Node 테스트 54개 및 실제 Electron smoke 통과.
- 별도 PostgreSQL 18 테스트 DB를 사용한 `SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon` 통과. 기존 로컬 API DB는 사용하지 않았다.
- `git diff --check` 통과. Windows/macOS 설치본은 각 Release workflow 결과로 별도 확인한다.

- Node 테스트 54개 통과. SMS 대기 상태는 상태/만료시각/취소 여부만 IPC로 전달하며 인증번호·재개 정보는 제외한다.
- 실제 Electron 44.7 smoke 통과. SMS 대기 → 확인 중 → 기존 최종 승인, 기존 실패·재시도·취소 동작을 확인했다. 캡처 전에 compositor 갱신을 기다려 실제 대기 화면도 확인했다.
- `npm ci --prefix apps/windows-certificate --ignore-scripts`로 의존성을 설치했고, 기존 로컬 Electron 실행 파일로 이 워크트리의 smoke를 실행했다. Windows 패키지나 release는 빌드하지 않았다.
- 서버 설정 `CODEF_SMS_ADDITIONAL_AUTH_ENABLED=true`가 켜져 있고 신규 서버가 additionalAuthentication을 반환할 때 표시한다. 기존 서버 응답에서는 이전 흐름을 유지한다.
- 모바일 SMS 모달과 실은행 SMS 검증은 별도다. 서버 연동 계약은 backend `codef-sms-additional-authentication-runbook.md`에 기록했다.
