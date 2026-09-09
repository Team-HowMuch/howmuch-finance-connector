# Feature Plan: CODEF 은행 거래 수집과 결과 발행

- Status: Completed
- Last updated: 2026-09-09
- Related issue: https://github.com/Team-HowMuch/howmuch-finance-connector/issues/21
- Related ADR: N/A

## 1. 목적

### 문제

RC2 command가 `QUEUED` execution과 `SyncAccepted` outbox로 저장되지만 실제 CODEF 조회, 결과 확정, Kafka 발행 경로가 없다.

### 지금 필요한 이유

Backend V2의 비동기 접수 전에 Connector가 수집 결과를 RC2로 반환하는 경계를 완성해야 한다. 다만 검증된 CODEF 응답에는 거래별 안정 ID가 없으므로 실제 비어 있지 않은 응답의 카나리는 차단한다.

### 성공 기준

- [x] worker가 execution을 선점한 뒤 DB transaction 밖에서 CODEF를 호출한다.
- [x] 빈 결과는 `TransactionsCollectionCompleted(totalChunks=0,totalItems=0)`로 성공한다.
- [x] 안정 ID가 있는 fixture는 `SHA-256(accountRef + providerTransactionId)` key로 최대 100건/256KiB chunk를 만든다.
- [x] 안정 ID가 없는 거래가 하나라도 있으면 chunk 없이 `AMBIGUOUS`로 실패한다.
- [x] outbox relay가 Kafka key=`accountRef`로 RC2 event를 발행한다.

### 제외 범위

- 실제 계좌 카나리와 안정 provider ID 필드 확정
- pagination cursor (검증된 응답에 없음)
- retry/backoff, lease 만료 복구, DLT와 보존 정리(C5)

## 2. 도메인 분석

### 도메인 경계

- 기존 도메인 재사용: C3 `collection_executions`, 연결 암호화, RC2 schema/outbox
- 신규 도메인: 수집 작업과 표준 은행 거래
- 다른 도메인과의 관계: Backend V2는 후속 B2에서 chunk/completed/failed를 소비한다.

### 보편 언어

| 용어 | 의미 | 코드 표현 |
|---|---|---|
| Collection Task | 선점된 execution의 공개 참조와 조회 범위 | `FinanceCollectionTask` |
| Collected Transaction | provider adapter가 반환하는 표준 거래 | `CollectedBankTransaction` |
| Ambiguous | 안정적인 거래 ID가 없어 중복 판정을 보장할 수 없음 | `AMBIGUOUS` |
| Event Relay | outbox payload를 Kafka에 전달하고 완료 표시 | `PublishFinanceEventsUseCase` |

### 액터와 UseCase

| 액터 | 목표 | UseCase |
|---|---|---|
| scheduled worker | 다음 execution 한 건을 수집·확정 | `CollectNextFinanceExecutionUseCase` |
| scheduled relay | 다음 outbox event 한 건을 Kafka 발행 | `PublishNextFinanceEventUseCase` |

### 상태와 불변식

- `QUEUED -> COLLECTING -> SUCCEEDED/REAUTH_REQUIRED/FAILED`만 C4에서 수행한다.
- 외부 호출 중 DB transaction을 유지하지 않는다.
- 결과 event와 terminal execution은 한 transaction에서 저장한다.
- 한 거래라도 안정 ID가 없으면 부분 chunk를 저장하지 않는다.

## 3. 시나리오

### 정상 흐름

1. worker가 `FOR UPDATE SKIP LOCKED`로 실행 한 건을 `COLLECTING`으로 바꾼다.
2. CODEF adapter가 별도 DB 조회로 연결 암호문을 얻고 복호화해 거래 API를 호출한다.
3. Application이 거래 ID와 금액을 검증한다.
4. persistence adapter가 chunk와 completed outbox, execution 성공을 원자 저장한다.
5. relay가 outbox를 선점해 Kafka에 발행하고 `PUBLISHED`로 표시한다.

### 실패 흐름

- 연결 해제/구버전은 `CONNECTION_REVOKED`로 실패한다.
- CODEF 추가 인증은 `REAUTH_REQUIRED`, 통신 장애는 `UPSTREAM_UNAVAILABLE`로 실패한다.
- provider ID 부재는 `AMBIGUOUS`로 실패한다.
- Kafka 발행 후 상태 저장 전 종료 시 재발행될 수 있으며 Backend inbox가 중복을 제거한다.

### 경계 조건

- 한 항목이 256KiB를 넘으면 `INVALID_REQUEST`로 실패한다.
- 완료 event는 모든 chunk 뒤에 outbox created order로 저장한다.
- 이미 terminal execution은 worker가 다시 선점하지 않는다.

## 4. 계약

### Input Port와 경계 매핑

```text
Scheduler -> CollectNextFinanceExecutionUseCase
FinanceCollectionExecutionStore.claimNextExecution -> FinanceCollectionTask
BankTransactionCollector.collect -> CollectedBankTransaction[]
Scheduler -> PublishNextFinanceEventUseCase
FinanceEventOutboxStore.claimNextEvent -> PendingFinanceEvent
```

### Output Ports

| Port | 목적 | 호출자 | 예상 Adapter |
|---|---|---|---|
| `FinanceCollectionExecutionStore` | execution 선점과 terminal 결과 원자 저장 | collection service | JDBC |
| `FinanceCollectionCredentialStore` | active/version 일치 암호문 조회 | CODEF adapter | JDBC |
| `BankTransactionCollector` | CODEF 거래 조회와 표준화 | collection service | CODEF HTTP |
| `FinanceEventOutboxStore` | event 선점·발행 완료 | relay service | JDBC |
| `FinanceEventPublisher` | accountRef key로 Kafka 발행 | relay service | KafkaTemplate |

### Application Policy

- 별도 Policy 없음. 수집 service가 안정 ID 전체성, KRW 정수 금액, terminal 결과를 조율한다.

### Web API

```text
N/A — 신규 HTTP endpoint 없음
```

### 영속성과 외부 연동

- Database 변경: V3 테이블 재사용
- Flyway migration required (`Yes` / `No`): No
- Planned migration: N/A
- Migration pull request: https://github.com/Team-HowMuch/howmuch-finance-connector/pull/18 (Merged)
- Redis 변경: 없음
- External API/message: CODEF OAuth, `/v1/kr/bank/p/account/transaction-list`, `finance.sync.event.v1`
- Environment: `CODEF_BASE_URL`, `CODEF_OAUTH_URL`, `CODEF_CLIENT_ID`, `CODEF_CLIENT_SECRET`, worker/relay enable flags

## 5. 설계 결정

### 비즈니스 로직

- Application은 provider ID 전체성 확인 후에만 성공 저장을 요청한다.
- CODEF adapter는 검증되지 않은 필드를 ID로 추정하지 않고 현재 응답에는 `null` ID를 반환한다.
- Persistence가 RC2 wire JSON, chunk 크기, outbox 순서를 책임진다.

### 기술과 패턴

- 선점·외부 호출·결과 저장을 세 구간으로 나눠 HTTP 동안 DB transaction을 열지 않는다.
- C4는 최초 시도만 수행하고 retry/lease 복구는 C5에 남긴다.
- 새 library 없이 Spring `RestClient`, `KafkaTemplate`, JDK crypto/hash를 재사용한다.

## 6. 영향 범위

| 영역 | 영향 | 확인 파일 또는 계약 |
|---|---|---|
| API | 없음 | N/A |
| Application | collection/relay UseCase와 Service | `application.sync` |
| Domain | 표준 거래와 failure code | `domain.sync` |
| Persistence | execution/outbox 상태 변경 | V3 schema |
| Security | 연결 secret 복호화 | 기존 AES-GCM key |
| Configuration | CODEF clients, scheduler flags | `application.yaml` |
| Documentation | 본 Feature Plan | 이 문서 |

유지해야 하는 기존 동작:

- C3 command listener와 내부 연결 API 동작을 변경하지 않는다.
- 안정 ID 확인 전 실제 비어 있지 않은 응답은 성공하지 않는다.

## 7. 구현 계획

- [x] Migration-only pull request to `develop` (`N/A` - V3 재사용)
- [x] UseCase and Application Command/Result
- [x] Required Output Ports
- [x] Domain models and rules
- [x] Application Policies (`N/A`)
- [x] Application Service
- [x] Output Adapters
- [x] Web Request/Response DTOs, Controllers, and Swagger (`N/A`)
- [x] Infra configuration and Bean wiring
- [x] Documentation and verification results

### 예상 커밋 경계

```text
docs(connector): CODEF 거래 수집 설계 기록
feat(connector): CODEF 은행 거래 수집 구현
feat(connector): 수집 결과와 outbox 발행 구현
test(connector): CODEF 수집과 결과 발행 검증
docs(connector): CODEF 수집 검증 결과 기록
```

## 8. 검증

### 명령

```bash
./gradlew :test --tests '*Codef*' --tests '*FinanceCollection*' --no-daemon
./gradlew clean build --no-daemon
git diff --check
git status --short --untracked-files=all
```

### 완료 조건

- [x] 목적의 실행 결과를 검증했다.
- [x] 정상·실패·경계 흐름을 검증했다.
- [x] RC2 payload와 transaction key를 검증했다.
- [x] 실행하지 못한 검증과 이유를 기록했다.

### 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| CODEF request/error mapping | Pass | 요청 body, KST→UTC, 입출금·잔액·설명, 재인증·연결 해제·비정상 응답 거부 단위 테스트 |
| empty/ambiguous/stable fixture | Pass | 빈 결과 성공, ID 부재 `AMBIGUOUS`, 안정 ID 결과 검증 |
| chunk/complete/fail persistence | Pass | PostgreSQL 18 Testcontainers에서 101건 분할, RC2 schema·key·terminal 상태와 연결 해제 후 완료 rollback 검증 |
| Kafka outbox relay | Pass | Kafka 4.2.1 Testcontainers에서 4개 event의 `accountRef` key와 실제 wire 256KiB 이하 검증 |
| 전체 build | Pass | `./gradlew clean build --no-daemon` (14 tasks) |

## 9. 진행 기록

| 날짜 | 완료 작업 | 다음 작업 | 발견 위험 |
|---|---|---|---|
| 2026-09-09 | C3 PR #20 merge, Backend V2 CODEF 경로 조사 | collection ports와 CODEF client | 검증된 응답에 stable transaction ID와 pagination cursor 없음 |
| 2026-09-09 | CODEF adapter, collection worker, RC2 outbox·Kafka relay와 통합 검증 완료 | C5 재시도·복구 | 실제 비어 있지 않은 계좌 카나리는 stable ID 확인 전 차단 |
| 2026-09-09 | 독립 감사 P1 4건 보완: 완료 시 연결 재검증, 호출 후 deadline, strict 응답, wire byte 제한 | PR CI·merge | C5 복구 전 PROCESSING outbox 자동 회수 없음 |

## 10. 결정 기록

| 날짜 | 결정 | 이유 | 대안 또는 영향 |
|---|---|---|---|
| 2026-09-09 | CODEF 응답 필드를 안정 ID로 추정하지 않음 | fingerprint 중복 병합 위험을 제거하는 RC2 gate | 실제 응답 증거 후 adapter mapping 추가 |
| 2026-09-09 | C4는 최초 시도만 수행 | retry/backoff/lease/DLT는 C5의 독립 범위 | 실패·중단 복구는 C5 전까지 제한됨 |
| 2026-09-09 | 완료 transaction에서 연결·계좌 row를 다시 잠금 검증 | 외부 호출 중 revoke/version 변경 결과가 성공하는 race 차단 | 불일치는 chunk 없이 `CONNECTION_REVOKED` |
| 2026-09-09 | CODEF 성공 응답은 명시적 배열과 모든 항목 변환을 요구 | malformed/부분 누락을 0건 성공으로 오인하지 않음 | 실제 빈 배열만 0건 성공 |

## 11. 완료 기록

- 완료일: 2026-09-09
- 최종 동작: Connector가 수집 execution을 선점해 CODEF 은행 거래를 조회하고, 검증된 결과만 RC2 chunk/completed 또는 failed outbox로 원자 저장한 뒤 Kafka에 발행한다.
- 미검증 항목: 실제 CODEF 응답의 안정 provider transaction ID와 pagination cursor는 확인되지 않아 비어 있지 않은 실제 계좌 카나리를 수행하지 않았다. `bash scripts/test.sh`는 저장소에 script가 없어 실행하지 않았다.
- 남은 위험: stable provider transaction ID 미확인으로 실제 비어 있지 않은 계좌 카나리 차단
- 후속 작업: C5 금융 수집 재시도와 메시지 복구
