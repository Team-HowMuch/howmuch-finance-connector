# Feature Plan: 금융 연결 등록과 수집 명령 접수

- Status: Completed
- Last updated: 2026-09-09
- Related issue: https://github.com/Team-HowMuch/howmuch-finance-connector/issues/19
- Related ADR: N/A

## 1. 목적

### 문제

Connector에는 RC2 계약과 저장 schema만 있어 Backend V2가 CODEF 연결을 등록하거나 Kafka 거래 수집 명령을 안전하게 접수할 수 없다.

### 지금 필요한 이유

C4 수집기는 암호화된 연결 정보와 멱등하게 생성된 `collection_executions`를 입력으로 사용한다. 이 경계가 없으면 외부 호출과 메시지 결과 발행을 구현할 수 없다.

### 성공 기준

- [x] 동일한 연결 payload는 기존 `connectionVersion`을 반환하고, 연결 또는 활성 계좌 집합이 바뀔 때만 version을 증가시킨다.
- [x] 연결 등록 API는 loopback과 service token을 모두 요구한다.
- [x] `connectedId`와 provider account reference는 AES-GCM 암호문으로만 저장된다.
- [x] 동일 command 재전달은 inbox, execution, `SyncAccepted` outbox를 중복 생성하지 않는다.
- [x] RC2 위반, Kafka key 불일치, 비활성 또는 구버전 연결 command를 거부한다.

### 제외 범위

- CODEF 거래 조회와 pagination
- event outbox Kafka relay, 재시도, DLT
- chunk/completed/failed event 생성
- 운영 배포와 카나리 활성화

## 2. 도메인 분석

### 도메인 경계

- 기존 도메인 재사용: RC2 wire resource와 V3 persistence schema
- 신규 도메인: 연결 등록 상태와 수집 command 접수 상태
- 다른 도메인과의 관계: Backend account sync job이 `connectionRef`, `accountRef`, `connectionVersion`을 발급한다.

### 보편 언어

| 용어 | 의미 | 코드 표현 |
|---|---|---|
| Registration Fingerprint | 연결과 활성 계좌 전체의 정규화 SHA-256 | `registrationHash` |
| Connection Version | Connector가 연결 변경마다 증가시키는 값 | `connectionVersion` |
| Command Receipt | Kafka delivery를 message ID와 hash로 식별한 inbox 기록 | `finance_command_inbox` |
| Collection Execution | 하나의 sync job에 대응하는 수집 실행 | `collection_executions` |

### 액터와 UseCase

| 액터 | 목표 | UseCase |
|---|---|---|
| Backend V2 | 연결과 활성 계좌 전체를 등록 또는 해제 | `RegisterFinanceConnectionUseCase`, `RevokeFinanceConnectionUseCase` |
| Kafka consumer | RC2 command를 한 번만 접수 | `AcceptFinanceSyncCommandUseCase` |

### 상태와 불변식

- 연결은 최초 version 1이며 fingerprint 또는 활성 상태가 바뀔 때만 1 증가한다.
- 같은 `accountRef`는 한 연결에만 속한다.
- command는 active connection/account와 정확한 `connectionVersion`을 참조해야 한다.
- 같은 `messageId`의 다른 payload와 같은 `syncJobId`의 다른 command는 충돌이다.
- execution, processed inbox, accepted outbox는 하나의 DB transaction에서 저장된다.

## 3. 시나리오

### 정상 흐름

1. Backend가 loopback에서 service token과 함께 연결 전체를 PUT한다.
2. Connector가 fingerprint를 비교하고 version을 유지하거나 증가시킨다.
3. Kafka listener가 RC2 schema와 accountRef key를 검증한다.
4. Application이 inbox를 선점하고 active connection/account를 확인한다.
5. `QUEUED` execution, processed inbox, `SyncAccepted` outbox를 원자적으로 저장한다.

### 실패 흐름

- service token 누락 또는 불일치는 `401`, non-loopback은 `403`을 반환한다.
- 다른 연결 소유 `accountRef`는 `409 FINANCE_CONNECTION_ACCOUNT_CONFLICT`를 반환한다.
- 같은 message/job 식별자의 다른 payload는 command conflict로 거부한다.
- 잘못된 schema, Kafka key, 비활성·구버전 연결은 listener 예외로 처리하며 DLT는 C5에서 연결한다.

### 경계 조건

- 동일 PUT은 암호문을 다시 만들지 않고 기존 version을 반환한다.
- DELETE는 존재하지 않는 연결에도 멱등하게 `204`를 반환한다.
- 누락된 기존 계좌는 삭제하지 않고 `active=false`로 보존한다.
- deadline이 이미 지난 command는 실행을 만들지 않는다.

## 4. 계약

### Input Port와 경계 매핑

```text
PUT Request -> RegisterFinanceConnectionCommand -> RegisterFinanceConnectionUseCase
FinanceConnectionRegistrationResult -> ConnectionRegistrationResponse
DELETE path -> RevokeFinanceConnectionUseCase
Kafka JSON/key -> AcceptFinanceSyncCommand -> AcceptFinanceSyncCommandUseCase
```

### Output Ports

| Port | 목적 | 호출자 | 예상 Adapter |
|---|---|---|---|
| `FinanceConnectionStore` | 연결 잠금·조회·전체 교체·해제 | 연결 Application Service, command service | JDBC persistence adapter |
| `ConnectionSecretCipher` | 민감 연결 값을 AES-GCM 암호화 | 연결 Application Service | JDK crypto adapter |
| `FinanceCommandIntakeStore` | inbox 선점과 execution/outbox 원자 저장 | command Application Service | JDBC persistence adapter |

### Application Policy

- Policy가 소유할 결정: 별도 Policy 없음. version 증가는 연결 aggregate 하나의 현재 상태와 입력 fingerprint만 사용한다.
- Service가 조회해 전달할 사실: 현재 version/hash/active, account 소유 연결, command 연결 활성 상태
- Entity-local invariant가 아닌 이유: N/A
- 의도적으로 미구현한 향후 입력: provider별 credential 형식, pagination checkpoint

### Web API

```text
PUT /internal/v1/connections/{connectionRef}
Request: connectedId, organizationCode, accounts[accountRef, providerAccountRef, accountType, productCode, currency]
Response: connectionRef, connectionVersion, changed
Errors: 400 validation, 401 invalid token, 403 non-loopback, 409 account conflict
Authentication: loopback + Authorization: Bearer <service token>

DELETE /internal/v1/connections/{connectionRef}
Response: 204 No Content
Authentication: loopback + service token
```

### 영속성과 외부 연동

- Database 변경: V3의 5개 테이블 재사용
- Flyway migration required (`Yes` / `No`): No
- Planned migration: N/A
- Migration pull request: https://github.com/Team-HowMuch/howmuch-finance-connector/pull/18 (Merged)
- Redis 변경: 없음
- External message: `finance.sync.command.v1` consume, `SyncAccepted` outbox 저장
- Environment: `CONNECTOR_INTERNAL_SERVICE_TOKEN`, `CONNECTOR_DATA_KEY`, `KAFKA_BOOTSTRAP_SERVERS`

## 5. 설계 결정

### 비즈니스 로직

- Application Service가 canonical fingerprint, version 증가, 전체 계좌 교체를 조율한다.
- DB unique/partial index가 account ownership과 execution 중복을 최종 방어한다.
- Inbox는 `INSERT ... ON CONFLICT DO NOTHING`으로 먼저 선점해 동시 재전달을 직렬화한다.

### 기술과 패턴

- 선택: purpose-oriented Port 뒤에서 `JdbcClient`를 사용한다.
- 이유: V3 schema에 필요한 원자 SQL이 단순하며 사용하지 않는 JPA aggregate/entity boilerplate를 만들지 않는다.
- 암호화: JDK `AES/GCM/NoPadding`, 256-bit 환경 key, 매번 96-bit random nonce
- 계약 검증: `finance-contracts` resource와 `json-schema-validator:3.0.6`
- 한계: DLT와 outbox relay는 C5/C4까지 동작하지 않는다.

## 6. 영향 범위

| 영역 | 영향 | 확인 파일 또는 계약 |
|---|---|---|
| API | 내부 PUT/DELETE 추가 | `adapter.in.web.connection` |
| Application | 연결과 command UseCase/Service | `application.connection`, `application.sync` |
| Domain | 연결 version 상태와 account type | `domain.connection` |
| Persistence | V3 테이블 read/write | `adapter.out.persistence` |
| Redis | 없음 | N/A |
| Security | loopback + service token | `infra.security` |
| Configuration | Kafka consumer, crypto key | `application*.yaml`, Gradle |
| Documentation | 본 Feature Plan | 이 문서 |

유지해야 하는 기존 동작:

- `/actuator/health/**`는 인증 없이 접근 가능해야 한다.
- health 외 비인가 경로는 계속 차단해야 한다.
- RC2 resource JAR과 publish workflow는 변경하지 않는다.

## 7. 구현 계획

- [x] Migration-only pull request to `develop` (`Required` - PR #18 merged)
- [x] UseCase and Application Command/Result
- [x] Required Output Ports
- [x] Domain models and rules
- [x] Application Policies (`N/A` - 단일 aggregate 규칙)
- [x] Application Service
- [x] Output Adapters
- [x] Web Request/Response DTOs, Controllers, and Swagger (`Swagger N/A` - loopback internal API)
- [x] Infra configuration and Bean wiring
- [x] Documentation and verification results

### 예상 커밋 경계

```text
docs(connector): 연결 등록과 명령 접수 설계 기록
feat(connector): 금융 연결 등록과 내부 인증 구현
feat(connector): Kafka 수집 명령 접수 구현
test(connector): 연결과 명령 접수 통합 검증
docs(connector): 연결과 명령 접수 검증 결과 기록
```

## 8. 검증

### 명령

```bash
./gradlew test --tests '*Connection*' --tests '*FinanceCommand*' --no-daemon
./gradlew clean build --no-daemon
git diff --check
git status --short --untracked-files=all
```

PostgreSQL 18과 Kafka 4.2.1 Testcontainers가 가능한 Docker 환경에서 실행한다.

### 완료 조건

- [x] 목적의 실행 결과를 검증했다.
- [x] 정상·실패·경계 흐름을 검증했다.
- [x] internal API와 RC2 contract가 구현과 일치한다.
- [x] 실행하지 못한 검증과 이유를 기록했다.

### 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 연결 등록·교체·해제 | Pass | 동일 등록, 전체 계좌 교체·비활성화, 타 연결 `accountRef` 충돌, 해제 검증 |
| service token·loopback | Pass | `InternalServiceTokenFilterTest`, 실제 HTTP 401/200/204 |
| command parser·멱등 접수 | Pass | RC2 위반·비-command·key·날짜·version 거부, 동일 payload와 새 messageId 재전달 검증 |
| PostgreSQL/Kafka 통합 | Pass | Testcontainers `postgres:18`, `apache/kafka:4.2.1`; execution 실패·연결 해제 시 inbox rollback 검증 |
| 전체 build | Pass | `./gradlew clean build --no-daemon` (14 tasks) |

## 9. 진행 기록

| 날짜 | 완료 작업 | 다음 작업 | 발견 위험 |
|---|---|---|---|
| 2026-09-09 | C2 V3 migration merge, RC1 호출 경로 조사 | 연결 Port와 내부 API 구현 | legacy RC1은 raw secret·JPA 직접 의존·Instant 사용 |
| 2026-09-09 | 내부 연결 API·AES-GCM·RC2 Kafka intake·통합 검증 완료 | C4 CODEF 수집기 | DLT와 outbox relay는 후속 범위 |

## 10. 결정 기록

| 날짜 | 결정 | 이유 | 대안 또는 영향 |
|---|---|---|---|
| 2026-09-09 | 등록 payload에는 provider/institution을 받지 않고 CODEF/BANK로 고정 | 이번 범위가 CODEF 은행 연결뿐이며 DB constraint와 일치 | 카드·다른 provider는 별도 contract/migration 필요 |
| 2026-09-09 | 동일 payload는 version과 기존 암호문을 유지 | 재등록 때 random nonce로 ciphertext가 바뀌어도 변경으로 오인하지 않음 | plaintext 기반 canonical hash는 로그에 노출하지 않음 |
| 2026-09-09 | JDBC adapter를 사용 | atomic claim/upsert를 짧은 SQL로 표현 | JPA entity는 실제 aggregate mapping 필요 시 도입 |

## 11. 완료 기록

- 완료일: 2026-09-09
- 최종 동작: Backend V2가 loopback 인증 API로 암호화 연결을 멱등 등록하고, Connector가 RC2 Kafka command를 원자적으로 inbox·execution·`SyncAccepted` outbox에 접수한다.
- 미검증 항목: `bash scripts/test.sh`는 저장소에 script가 없어 실행하지 않았다.
- 남은 위험: stable provider transaction ID 미확인으로 카나리 차단 유지
- 후속 작업: C4 CODEF 은행 거래 수집과 결과 발행
