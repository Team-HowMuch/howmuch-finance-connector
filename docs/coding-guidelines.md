# Coding Guidelines

## Java

- Java 21 문법을 사용하고 Command/Result는 불변 `record`를 우선한다.
- 이름으로 책임을 드러낸다: `UseCase`, `Command`, `Result`, `Controller`, `Request`, `Response`, `JpaEntity`, `RepositoryAdapter`, `Config`, `Properties`.
- 단일 구현을 위한 interface나 범용 `Manager`, `Util`을 만들지 않는다.
- 시간은 UTC `LocalDateTime`으로 저장·계산한다.

## Spring과 영속성

- Controller와 configuration에 업무 로직을 두지 않는다.
- shared default는 `application.yaml`, 환경별 값은 profile 파일과 환경 변수에 둔다.
- JPA `open-in-view`는 끄고 schema 변경은 Flyway만 사용한다.
- 운영 credential이나 환경 고정 endpoint를 커밋하지 않는다.

## Test

- Domain/Application 규칙은 unit test, Web/Security/Config는 slice 또는 integration test로 검증한다.
- PostgreSQL/Kafka 고유 동작은 실제 service 또는 Testcontainers로 확인한다.
- 테스트 fixture는 실제 계좌번호, 토큰, CODEF 응답을 사용하지 않는다.
