# Feature Plan: 금융 거래 동기화 RC2 계약

- Status: Completed
- Last updated: 2026-09-09
- Related issue: https://github.com/Team-HowMuch/howmuch-finance-connector/issues/15
- Related ADR: N/A

## 1. 목적

### 문제

Backend V2와 connector가 공유할 엄격한 메시지 계약이 없어, 필드·버전·enum이 달라도 Kafka 처리 시점까지 알 수 없다. 기존 RC1은 Jackson 2 기반이며 거래별 안정 ID가 확인되지 않은 상태에서 파생 식별 품질을 허용한다.

### 지금 필요한 이유

C2 저장 schema와 C3 command intake가 같은 wire contract에 의존하므로 구현 전에 RC2 artifact를 고정해야 한다.

### 성공 기준

- [x] 5개 message type과 정상·빈 결과·최종 실패 example이 하나의 Draft 2020-12 schema로 검증된다.
- [x] 필수 필드, version, enum, 거래 키, chunk 개수 위반을 자동 테스트가 거부한다.
- [x] `com.amateur:howmuch-finance-contracts:1.0.0-rc.2` resource JAR을 GitHub Packages에 발행했다.
- [x] 안정적인 provider transaction ID가 확인되지 않은 현재 상태를 `AMBIGUOUS` canary gate로 기록했다.

### 제외 범위

- Kafka producer/consumer와 topic 생성
- Java DTO/domain model 공유
- CODEF client와 거래 mapping 구현
- 금융 연결·수집 실행 persistence
- 실제 카나리 허용

## 2. 도메인 분석

### 도메인 경계

- 재사용할 기존 도메인: 없음. 이 module은 wire resource만 제공한다.
- 신규 도메인이 필요한 이유: N/A
- 다른 도메인과의 관계: Backend account sync와 Connector collection이 같은 schema를 소비한다.

### 보편 언어

| 용어 | 의미 | 코드 표현 |
|---|---|---|
| Sync Job | Backend가 접수한 계좌 거래 동기화 작업 | `syncJobId` |
| Account Reference | 계좌번호를 노출하지 않는 임의 UUID | `accountRef` |
| Connection Version | 연결 전체 교체를 구분하는 증가 값 | `connectionVersion` |
| Transaction Key | 안정 provider ID가 있을 때 계산한 64자리 SHA-256 | `transactionKey` |
| Ambiguous | 안정 거래 ID가 없어 안전하게 반영할 수 없음 | `failureCode=AMBIGUOUS` |

### 액터와 UseCase

| 액터 | 목표 | UseCase |
|---|---|---|
| Backend | 수집을 요청하고 결과를 안전하게 검증 | schema resource 소비 |
| Connector | 수집 접수·결과 message를 같은 계약으로 발행 | schema resource 소비 |

### 상태와 불변식

- 상태 전이: command -> accepted -> zero or more chunks -> completed, 또는 failed
- 항상 지켜야 할 규칙: `accountRef`는 UUID이고 transaction chunk에는 검증된 64자리 key만 포함한다.
- 잘못된 상태: 미지원 version/type, 101건 이상 chunk, stable provider ID 없이 importable chunk 발행

## 3. 시나리오

### 정상 흐름

1. Backend가 `SyncAccountTransactions`를 발행한다.
2. Connector가 `SyncAccepted`를 발행한다.
3. 안정 거래 ID가 확인되면 100건 이하 `TransactionsChunkCollected`를 발행한다.
4. Connector가 chunk·item 총계를 담은 `TransactionsCollectionCompleted`를 발행한다.

### 실패 흐름

- 조건: 안정적인 provider transaction ID를 확인할 수 없다.
- 반환 오류 또는 상태 전이: chunk를 발행하지 않고 `TransactionsSyncFailed`의 `failureCode=AMBIGUOUS`를 발행한다.

### 경계 조건

- 거래가 0건이면 chunk 없이 `totalChunks=0`, `totalItems=0` 완료 event를 발행한다.
- serialized chunk 256KiB 제한은 publisher 구현에서 적용하며 schema는 `maxItems=100`만 표현한다.

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
| N/A | resource module에는 실행 코드가 없다. | N/A | N/A |

### Application Policy

- Policy가 소유할 결정: N/A
- Service가 조회해 전달할 사실: N/A
- Entity-local invariant가 아닌 이유: N/A
- 의도적으로 미구현한 향후 입력: provider별 pagination과 거래 ID mapping

### Web API

```text
N/A
```

### 영속성과 외부 연동

- Database Entity, constraint, or query changes: 없음
- Flyway migration required (`Yes` / `No`): No
- Planned migration: N/A
- Migration pull request (`Pending` / URL): N/A
- Redis key, TTL, or serialization changes: 없음
- External API or message changes: `finance.sync.command.v1`, `finance.sync.event.v1`, 각 `.dlt`
- Environment variable or profile changes: publish용 `GITHUB_ACTOR`, `GITHUB_TOKEN`

## 5. 설계 결정

### 비즈니스 로직

- 규칙과 위치: JSON Schema가 metadata, message별 data, enum, item limit을 정의한다.
- Domain이 소유할 동작: 없음
- Application Policy가 소유할 결정: 없음
- Application이 조율할 흐름: 후속 작업

### 기술과 패턴

- 해결할 제약: Spring Boot 4.1/Jackson 3 소비자가 동일 schema를 검증해야 한다.
- 선택한 접근: Java model 없는 resource JAR과 `json-schema-validator:3.0.6` 계약 테스트
- 선택 이유: 런타임 domain 결합 없이 schema와 example을 한 버전으로 배포한다.
- 검토한 대안: Java DTO artifact, Schema Registry
- 트레이드오프와 한계: 256KiB byte 제한과 날짜 순서 같은 교차 필드 규칙은 runtime 구현에서 별도 검증한다.

## 6. 영향 범위

| 영역 | 영향 | 확인 파일 또는 계약 |
|---|---|---|
| API | 없음 | N/A |
| Application | 없음 | N/A |
| Domain | 없음 | N/A |
| Persistence | 없음 | N/A |
| Redis | 없음 | N/A |
| Security | package publish token은 Actions secret만 사용 | publish workflow |
| Configuration | Gradle multi-module, Maven publication | `settings.gradle`, module build |
| Documentation | CODEF mapping gate와 example | `CODEF_MAPPING.md`, `examples/**` |

유지해야 하는 기존 동작:

- connector application build와 health/security test가 계속 통과해야 한다.
- 기존 RC1 package, issue, branch, PR은 수정하지 않는다.

## 7. 구현 계획

- [x] Migration-only pull request to `develop` (`N/A` - persistence 변경 없음)
- [x] UseCase and Application Command/Result (`N/A` - resource module)
- [x] Required Output Ports (`N/A` - 실행 코드 없음)
- [x] Domain models and rules (`N/A` - Java model 공유 안 함)
- [x] Application Policies (`N/A`)
- [x] Application Service (`N/A`)
- [x] Output Adapters (`N/A`)
- [x] Web Request/Response DTOs, Controllers, and Swagger (`N/A`)
- [x] Infra configuration and Bean wiring (`N/A` - Gradle publication만 구성)
- [x] Documentation and verification results

### 예상 커밋 경계

```text
docs(contract): RC2 계약 설계와 CODEF 식별자 게이트 기록
feat(contract): 금융 거래 동기화 RC2 schema 추가
test(contract): RC2 example과 거부 계약 검증
ci(contract): GitHub Packages 발행 구성
docs(contract): RC2 계약 검증 결과 기록
```

## 8. 검증

### 명령

```bash
./gradlew :finance-contracts:clean :finance-contracts:test :finance-contracts:jar --no-daemon
jar tf finance-contracts/build/libs/howmuch-finance-contracts-1.0.0-rc.2.jar
SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon
git diff --check
git status --short --untracked-files=all
```

### 완료 조건

- [x] 목적의 실행 결과를 검증했다.
- [x] 정상·실패·경계 흐름을 검증했다.
- [x] schema와 example 문서가 실제 artifact와 일치함을 확인했다.
- [x] 실행하지 못한 검증과 이유를 기록했다.

### 결과

| 항목 | 결과 | 근거 |
|---|---|---|
| 정상·빈·실패 example | Pass | `:finance-contracts:test` 4개 테스트 통과 |
| 거부 계약 | Pass | 필수 필드·version·enum·거래 키·chunk 상한 거부 확인 |
| resource JAR | Pass | schema, mapping, 6개 example만 포함됨을 `jar tf`로 확인 |
| 전체 build | Pass | PostgreSQL 18에서 `SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon` 통과 |
| GitHub Packages publish | Pass | tag `finance-contracts-v1.0.0-rc.2`, Actions run 34347372548 성공 |

## 9. 진행 기록

| 날짜 | 완료 작업 | 다음 작업 | 발견 위험 |
|---|---|---|---|
| 2026-09-09 | Backend fixture와 CODEF 공식 sample 조사 | schema와 example 구현 | 개별 거래 안정 ID 및 pagination cursor 근거 없음 |
| 2026-09-09 | RC2 schema·example·검증 테스트와 resource JAR 구현 | Packages 발행 | 256KiB 제한은 publisher runtime 검증 필요 |
| 2026-09-09 | GitHub Packages 발행 및 전체 build·CI 검증 | C2 저장 schema | stable provider transaction ID 확인 전 카나리 차단 유지 |

## 10. 결정 기록

| 날짜 | 결정 | 이유 | 대안 또는 영향 |
|---|---|---|---|
| 2026-09-09 | `schemaVersion`은 wire major인 정수 `1`로 고정한다. | topic v1 및 향후 RC artifact patch와 분리한다. | package version은 `1.0.0-rc.2`로 독립 관리한다. |
| 2026-09-09 | transaction chunk는 `providerTransactionId`와 64자리 `transactionKey`를 모두 요구한다. | 안정 ID가 없으면 chunk 자체를 만들지 못하게 한다. | 현재 CODEF 근거에서는 `AMBIGUOUS` final failure만 가능하다. |
| 2026-09-09 | CODEF 응답의 `result.transactionId`를 거래 ID로 사용하지 않는다. | API 호출 단위 ID이며 개별 `resTrHistoryList` 항목 식별자가 아니다. | 실제 계좌 sample에서 거래별 ID가 추가 확인되면 mapping 문서를 갱신한다. |

## 11. 완료 기록

- 완료일: 2026-09-09
- 최종 동작: RC2 schema와 example을 resource JAR으로 발행하고 Jackson 3 validator로 허용·거부 계약을 자동 검증한다.
- 미검증 항목: 실제 CODEF 계좌 응답의 거래별 안정 ID와 pagination cursor
- 남은 위험: stable provider transaction ID와 transaction-list pagination이 확인되지 않아 실제 카나리가 차단됨
- 후속 작업: C2 금융 연결과 수집 실행 저장 schema
