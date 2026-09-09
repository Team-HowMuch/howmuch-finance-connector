# Architecture

## 의존 방향

```text
adapter.in -> application -> domain
adapter.out -> application -> domain
infra -> application contracts
```

- `domain`은 Spring, Web, Kafka, Redis, JPA, `infra`에 의존하지 않는다.
- `application`은 `domain`과 자기 Port에만 의존하고 구체 Adapter를 알지 않는다.
- `adapter.in`은 외부 요청을 Command로, Result를 외부 응답으로 변환한다.
- `adapter.out`은 Application이 요구한 목적 중심 Output Port를 구현한다.
- `infra`는 기술 설정과 Bean wiring만 담당하고 업무 규칙을 소유하지 않는다.

## Package

```text
com.amateur.howmuchfinanceconnector
├── adapter
│   ├── in
│   └── out
├── application
│   ├── port
│   │   ├── in
│   │   └── out
│   └── service
├── domain
└── infra
```

Package는 실제 기능이 생길 때만 만든다. 빈 계층이나 사용되지 않는 interface를 미리 추가하지 않는다.

## 경계 계약

- HTTP/Kafka/provider DTO는 해당 Adapter 내부에 둔다.
- `finance-contracts`는 JSON Schema와 wire example만 소유하며 domain model이나 JPA Entity를 공유하지 않는다.
- 외부 CODEF 호출은 DB transaction 밖에서 수행한다.
- credential과 provider 원문은 Application message와 log에 전달하지 않는다.
