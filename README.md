# howmuch-finance-connector

Kafka commands are accepted here, external financial providers are called here, and normalized results are returned as Kafka events. The application backend remains the owner of users, transactions, settlements, and user-facing sync state.

## Local checks

```bash
./gradlew clean build --no-daemon
```

The service requires PostgreSQL. Kafka and CODEF adapters are added in subsequent feature issues.
Kafka 기반 금융 데이터 연결 전용 서버
