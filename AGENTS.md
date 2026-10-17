# AGENTS.md

## 목적

- 이 저장소에서 작업하는 Codex 및 coding agent용 규약이다.
- 현재 코드 구조를 우선하며, 규약과 코드가 다르면 코드를 확인한 뒤 규약을 갱신한다.
- 작고 안전하며 리뷰 가능한 변경을 우선한다.

## 작업 시작 순서

1. `pwd`, `git status --short --untracked-files=all`로 저장소와 사용자 변경을 확인한다.
2. 루트와 작업 경로에 가까운 `AGENTS.md`를 모두 읽는다.
3. `docs/architecture.md`, `docs/coding-guidelines.md`, `docs/development-workflow.md`, 관련 Feature Plan을 읽는다.
4. 변경할 호출 경로와 같은 역할의 기존 구현을 `rg`로 조사한다.
5. 최신 `origin/main`과 열린 migration PR을 확인한다.
6. 사용자 변경은 건드리지 않고 별도 branch에서 작업한다.

## 프로젝트 구조

- Java 21, Spring Boot 4.1, Gradle
- PostgreSQL, Flyway. Kafka는 실제 메시지 구현이 추가될 때 의존성을 도입한다.
- Root package: `com.amateur.howmuchfinanceconnector`
- `domain`: framework-free 규칙과 상태
- `application`: UseCase, Command/Result, Output Port, orchestration
- `adapter.in`: Kafka 및 내부 HTTP 진입점
- `adapter.out`: persistence, messaging, provider adapter
- `infra`: Spring, security, environment wiring

의존 방향과 경계 규칙은 `docs/architecture.md`를 따른다. Provider DTO, JPA Entity, wire contract를 서로 공유하지 않는다.

## 구현 규칙

```text
Web Request DTO
  -> Application Command
  -> UseCase
  -> Application Service
  -> purpose-oriented Output Port
  -> adapter.out
  -> Result
  -> Web Response DTO
```

- Controller에서 Repository, SecurityContext, JPA Entity에 직접 접근하지 않는다.
- Input Port는 기능별 `application.port.in` 아래에 둔다.
- Output Port는 기술이 아니라 Application이 요구하는 목적을 이름으로 표현한다.
- 시간 저장·계산은 UTC `LocalDateTime`을 사용한다.
- 비밀번호, 토큰, 인증서, CODEF 응답 원문, 계좌번호를 코드·로그·Kafka에 남기지 않는다.

## Feature Plan과 Flyway

- endpoint, domain, persistence, 외부 연동 또는 여러 package를 바꾸는 작업은 `docs/templates/feature-plan.md`를 복사해 한국어 Feature Plan을 먼저 작성한다.
- migration이 필요한 최초 작업은 단 하나의 versioned SQL만 포함한 migration-only PR을 먼저 만들고 즉시 종료한다.
- migration 번호는 최신 `origin/main`과 열린 PR을 확인한 뒤 정한다.

## Branch, Issue, PR, Commit

- 최신 `main`에서 branch를 만들고 PR 대상도 `main`으로 한다.
- branch prefix는 `feat/`, `fix/`, `build/`, `test/`, `docs/`를 사용한다.
- 이슈 제목은 `[Enhancement] ...`, `[Bug] ...`, `[Test] ...`를 사용한다.
- 이슈 본문은 `현재 상황 / 확인 근거 / 기대 효과 / 작업 범위 / 완료 조건 / 관련 이슈` 순서로 작성한다.
- PR 본문은 `변경 내용 / 검증 / 확인 필요 / 관련 이슈` 순서로 작성한다.
- commit은 `<type>(<scope>): <한국어 설명>` 형식을 사용한다.
- 한 이슈에 한 논리적 PR을 유지한다.

## 검증

- 동작 변경에는 해당 경계를 실패시킬 수 있는 focused test를 남긴다.
- 실제 PostgreSQL/Kafka 동작을 mock 성공으로 대체하지 않는다.
- PR 전 `./gradlew clean build --no-daemon`, `git diff --check`, `git status --short --untracked-files=all`을 실행한다.
- 수행하지 않았거나 실패한 검증을 성공으로 보고하지 않는다.
