# Feature Plan: V2 기반 금융 연동 서버 초기화

- Status: Completed
- Last updated: 2026-09-09
- Related issue: https://github.com/Team-HowMuch/howmuch-finance-connector/issues/13
- Related ADR: N/A

## 1. 목적

### 문제

`develop`에 실행 가능한 애플리케이션이 없어 RC2 계약과 금융 수집 기능을 V2 규약에 따라 구현하거나 CI에서 검증할 수 없다.

### 지금 필요한 이유

Backend V2의 동기화 저장 스키마가 병합되어 connector 후속 작업이 시작될 수 있으므로, 먼저 동일한 Java 21·Spring Boot 4.1 기반을 고정해야 한다.

### 성공 기준

- [x] 실제 PostgreSQL을 사용한 `./gradlew clean build --no-daemon`이 성공한다.
- [x] `/actuator/health`는 공개되고 그 외 요청은 차단된다.
- [x] `develop` 대상 pull request에서 동일한 빌드를 실행한다.

### 제외 범위

- RC2 메시지 계약과 `finance-contracts` 모듈
- 금융 연결·수집 실행 업무 테이블과 Flyway migration
- Kafka listener, Outbox/Inbox, CODEF 호출
- 배포와 branch protection 설정

## 2. 도메인 분석

### 도메인 경계

- 재사용할 기존 도메인: 없음
- 신규 도메인이 필요한 이유: 이 작업은 실행 기반만 구성하므로 도메인을 만들지 않는다.
- 다른 도메인과의 관계: 후속 금융 연결·거래 수집 도메인이 이 기반을 사용한다.

### 보편 언어

| 용어 | 의미 | 코드 표현 |
|---|---|---|
| Connector | 금융 제공자와 Backend 사이의 별도 서비스 | `HowmuchFinanceConnectorApplication` |
| Health endpoint | 인증 없이 배포 상태를 확인하는 유일한 공개 경로 | `/actuator/health` |

### 액터와 UseCase

| 액터 | 목표 | UseCase |
|---|---|---|
| 배포 시스템 | 서비스 기동 여부 확인 | N/A - Actuator 기본 endpoint |

### 상태와 불변식

- 상태 전이: N/A
- 항상 지켜야 할 규칙: health 외 요청은 인증 구현 전까지 허용하지 않는다.
- 잘못된 상태: DB 연결이나 Flyway 검증이 실패했는데 애플리케이션이 정상 기동하는 상태

## 3. 시나리오

### 정상 흐름

1. PostgreSQL이 준비된다.
2. 애플리케이션을 기동한다.
3. Flyway가 빈 schema를 확인하고 Spring context가 생성된다.
4. `/actuator/health`가 200을 반환한다.

### 실패 흐름

- 조건: PostgreSQL 연결 정보가 잘못되거나 DB를 사용할 수 없다.
- 반환 오류 또는 상태 전이: Spring context 기동이 실패한다.

### 경계 조건

- `/actuator/health` 이외의 경로는 익명 요청을 허용하지 않는다.

## 4. 계약

### Input Port와 경계 매핑

```text
UseCase: N/A
Command: N/A
Result: N/A
Web Request DTO -> Command: N/A
Result -> Web Response DTO: N/A
```

### Output Ports

| Port | 목적 | 호출자 | 예상 Adapter |
|---|---|---|---|
| N/A | 이번 작업에는 Application 외부 대화가 없다. | N/A | N/A |

### Application Policy

- Policy가 소유할 결정: N/A
- Service가 조회해 전달할 사실: N/A
- Entity-local invariant가 아닌 이유: N/A
- 의도적으로 미구현한 향후 입력: 금융 연결 등록과 Kafka 수집 명령

### Web API

```text
GET /actuator/health
Request: 없음
Response: Spring Boot Actuator health response
Errors: 애플리케이션 또는 의존성 비정상 상태
Authentication: 없음
```

### 영속성과 외부 연동

- Database Entity, constraint, or query changes: 없음
- Flyway migration required (`Yes` / `No`): No
- Planned migration: N/A
- Migration pull request (`Pending` / URL): N/A
- Redis key, TTL, or serialization changes: 없음
- External API or message changes: 없음
- Environment variable or profile changes: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `SERVER_PORT`

## 5. 설계 결정

### 비즈니스 로직

- 규칙과 위치: health 공개와 나머지 요청 차단은 `infra.security.config.SecurityConfig`에 둔다.
- Domain이 소유할 동작: 없음
- Application Policy가 소유할 결정: 없음
- Application이 조율할 흐름: 없음

### 기술과 패턴

- 해결할 제약: 후속 기능을 실제 PostgreSQL·Flyway·Spring Security 환경에서 검증할 최소 기반이 필요하다.
- 선택한 접근: Backend V2와 같은 Spring Boot 4.1.0 및 Gradle 9.5.1을 사용하고, CI에 PostgreSQL service를 둔다.
- 선택 이유: 버전 차이를 제거하고 mock DB가 가리는 PostgreSQL 차이를 방지한다.
- 검토한 대안: H2 기반 context test
- 트레이드오프와 한계: 로컬 전체 빌드에는 PostgreSQL이 필요하다. Kafka 의존성은 실제 메시지 구현이 시작되는 후속 작업에서 추가한다.

## 6. 영향 범위

| 영역 | 영향 | 확인 파일 또는 계약 |
|---|---|---|
| API | health endpoint만 공개 | `SecurityConfig` |
| Application | 없음 | N/A |
| Domain | 없음 | N/A |
| Persistence | PostgreSQL·Flyway 연결 기반만 구성 | `application.yaml` |
| Redis | 없음 | N/A |
| Security | stateless, health 외 요청 차단 | `SecurityConfig` |
| Configuration | Java 21, Spring Boot 4.1, Gradle, CI | `build.gradle`, `ci.yml` |
| Documentation | 규약과 개발 절차 추가 | `AGENTS.md`, `docs/**` |

유지해야 하는 기존 동작:

- README의 저장소 목적을 유지한다.
- 기존 RC1 이슈·브랜치·PR은 수정하지 않는다.

## 7. 구현 계획

- [x] Migration-only pull request to `develop` (`N/A` - 업무 schema 변경 없음)
- [x] UseCase and Application Command/Result (`N/A` - endpoint 없음)
- [x] Required Output Ports (`N/A` - 외부 대화 없음)
- [x] Domain models and rules (`N/A` - 실행 기반 작업)
- [x] Application Policies (`N/A` - 정책 없음)
- [x] Application Service (`N/A` - orchestration 없음)
- [x] Output Adapters (`N/A` - adapter 없음)
- [x] Web Request/Response DTOs, Controllers, and Swagger (`N/A` - Actuator 기본 endpoint만 사용)
- [x] Infra configuration and Bean wiring
- [x] Documentation and verification results

### 예상 커밋 경계

```text
docs(connector): 금융 연동 서버 초기화 계획 기록
build(connector): V2 기반 금융 연동 서버 초기화
test(connector): 애플리케이션 기동과 보안 경계 검증
docs(connector): 초기화 검증 결과 기록
```

## 8. 검증

### 명령

```bash
docker compose -f compose.test.yml up -d
DB_URL=jdbc:postgresql://127.0.0.1:25432/howmuch_finance_connector_test \
DB_USERNAME=howmuch_connector_test DB_PASSWORD=connector_test \
SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon
git diff --check
git status --short --untracked-files=all
```

### 완료 조건

- [x] 목적의 실행 결과를 검증했다.
- [x] 정상·실패·경계 흐름을 검증했다.
- [x] 문서가 실제 구성과 일치함을 확인했다.
- [x] 실행하지 못한 검증과 이유를 기록했다.

### 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 실제 PostgreSQL context | Pass | PostgreSQL 18에서 Flyway history 생성 및 Spring context 기동 |
| health 공개 | Pass | 실제 HTTP `GET /actuator/health` 200 |
| 기타 요청 차단 | Pass | 실제 HTTP `GET /actuator/info` 403 |
| DB 장애 기동 차단 | Pass | 잘못된 DB URL에서 `FlywaySqlUnableToConnectToDbException`으로 context 실패 |
| 전체 build | Pass | 로컬 `BUILD SUCCESSFUL`, GitHub Actions build 1m36s 통과 |

## 9. 진행 기록

| 날짜 | 완료 작업 | 다음 작업 | 발견 위험 |
|---|---|---|---|
| 2026-09-09 | C0 범위와 제외 사항 기록 | 실행 기반 구현 | 기존 RC1 PR과 충돌하지 않도록 새 branch 사용 |
| 2026-09-09 | 실행 기반·보안 경계·CI 구현 및 검증 | C0 PR 리뷰와 병합 | 업무 migration 없이 Flyway history만 생성됨 |

## 10. 결정 기록

| 날짜 | 결정 | 이유 | 대안 또는 영향 |
|---|---|---|---|
| 2026-09-09 | C0에는 Kafka dependency와 빈 계약 모듈을 추가하지 않는다. | 아직 사용하는 코드가 없고 후속 C1/C3 범위다. | 실제 계약·listener 구현 시 필요한 의존성만 추가한다. |
| 2026-09-09 | 빈 bootstrap migration을 만들지 않는다. | 업무 schema 없이 이력용 테이블만 만드는 것은 의미가 없다. | C2에서 첫 업무 migration을 migration-only PR로 추가한다. |

## 11. 완료 기록

- 완료일: 2026-09-09
- 최종 동작: Spring Boot 4.1 connector가 PostgreSQL/Flyway와 함께 기동하며 health만 공개한다.
- 미검증 항목: 없음
- 남은 위험: 인증 및 Kafka 메시지 경계는 후속 이슈에서 구현·검증해야 한다.
- 후속 작업: C1 RC2 계약 확정
