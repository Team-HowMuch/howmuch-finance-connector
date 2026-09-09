# howmuch-finance-connector

HowMuch Backend V2와 금융 제공자 사이에서 계좌 거래를 수집하는 별도 서버입니다.

## 기술 스택

- Java 21
- Spring Boot 4.1
- PostgreSQL 18, Flyway
- Gradle 9.5.1

## 로컬 검증

```bash
docker compose -f compose.test.yml up -d
SPRING_PROFILES_ACTIVE=test ./gradlew clean build --no-daemon
docker compose -f compose.test.yml down -v
```

기본 포트는 `8081`이며 인증 없이 공개되는 endpoint는 `GET /actuator/health`뿐입니다.
