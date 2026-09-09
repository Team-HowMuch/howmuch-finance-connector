# AGENTS.md

## Project

- Spring Boot 3.5, Java 21, Gradle
- PostgreSQL, Flyway, Kafka
- Root package: `com.amateur.howmuchfinanceconnector`

## Architecture

- `domain`: framework-free rules and state
- `application`: use cases and ports
- `adapter.in`: Kafka and internal HTTP entry points
- `adapter.out`: persistence, messaging, and provider adapters
- `infra`: Spring and environment configuration

Keep provider DTOs inside their provider adapter. Do not share JPA entities or domain models through `finance-contracts`; JSON Schema and examples are the wire contract.

## Workflow

- Branch from `develop` with `feature/`, `fix/`, or `chore/`.
- Target pull requests to `develop`.
- Use Conventional Commit subjects.
- Add focused tests for behavior changes and run `./gradlew clean build --no-daemon` before a pull request.
- Never commit credentials, CODEF response bodies, account numbers, or tokens.
