# Feature Plan: <Feature name>

> Copy this file to `docs/features/active/<feature>.md`. Update it whenever implementation reveals new information or decisions, then move it to `docs/features/completed/` when complete.
>
> Keep this system template in English. Write the copied active Feature Plan in Korean, preserving code identifiers and executable tokens.

- Status: Draft
- Last updated: YYYY-MM-DD
- Related issue:
- Related ADR:

## 1. Purpose

### Problem

Describe whose problem this feature solves and why the current behavior is insufficient. Use a concrete situation.

### Why now

Record why the problem should be solved now and what happens if it remains unresolved.

### Success criteria

- [ ] An observable outcome available to the user
- [ ] Guaranteed behavior for relevant failure or boundary conditions

### Out of scope

- Work intentionally excluded from this feature

## 2. Domain analysis

### Domain boundary

- Existing domain to reuse:
- Reason a new domain is required, if applicable:
- Relationships to other domains:

### Ubiquitous language

| Term | Meaning | Code representation |
|---|---|---|
|  |  |  |

### Actors and UseCases

| Actor | Goal | UseCase |
|---|---|---|
|  |  |  |

### State and invariants

- State transitions:
- Rules that must always hold:
- Invalid states:

## 3. Scenarios

### Normal flow

1. Starting condition
2. User or external-system action
3. Application processing
4. Observable result

### Failure flow

- Condition:
- Returned error or state transition:

### Boundary conditions

- Record expiration, duplication, retry, concurrency, empty values, or other cases only when they apply to this feature.

## 4. Contracts

### Input Port and boundary mapping

```text
UseCase:
Command:
Result:
Web Request DTO -> Command:
Result -> Web Response DTO:
```

### Output Ports

| Port | Purpose | Caller | Expected Adapter |
|---|---|---|---|
|  |  |  |  |

Name each Output Port after the cohesive capability the Application requires. `Load<Aggregate>Port` and `Save<Aggregate>Port` are valid when general aggregate retrieval and persistence are the real capabilities, but they are not mandatory pairs. Use a more accurate capability name when appropriate, do not create a broad technology-shaped `<Aggregate>RepositoryPort`, and do not create a Port when no real external conversation exists.

### Application Policy

- Decision owned by the Policy:
- Facts loaded by the Service and passed to the Policy:
- Why the rule is not an Entity-local invariant:
- Future inputs deliberately not implemented yet:

Policies are Application-owned. Do not wire or interpret them in Infra.

### Web API

```text
Method /path
Request:
Response:
Errors:
Authentication:
```

### Persistence and external integrations

- Database Entity, constraint, or query changes:
- Flyway migration required (`Yes` / `No`):
- Planned migration:
- Migration pull request (`Pending` / URL):
- Redis key, TTL, or serialization changes:
- External API or message changes:
- Environment variable or profile changes:

## 5. Design decisions

### Business logic

- Rules and their locations:
- Behavior owned by Domain:
- Decisions owned by Application Policy:
- Flow orchestrated by Application:

### Technology and patterns

- Constraint to solve:
- Selected approach:
- Reason for selection:
- Alternatives considered:
- Trade-offs and limits:

Create an ADR for a decision with durable impact across multiple features and link it under `Related ADR` above.

## 6. Impact surface

| Area | Impact | Verified file or contract |
|---|---|---|
| API |  |  |
| Application |  |  |
| Domain |  |  |
| Persistence |  |  |
| Redis |  |  |
| Security |  |  |
| Configuration |  |  |
| Documentation |  |  |

Existing behavior that must remain unchanged:

- <Behavior that must not be affected>

## 7. Implementation plan

- [ ] Migration-only pull request to `develop` (`Required` / `N/A`)
- [ ] UseCase and Application Command/Result
- [ ] Required Output Ports
- [ ] Domain models and rules
- [ ] Application Policies
- [ ] Application Service
- [ ] Output Adapters
- [ ] Web Request/Response DTOs, Controllers, and Swagger
- [ ] Infra configuration and Bean wiring
- [ ] Documentation and verification results

Do not delete an unnecessary step. Mark it `N/A` and record the reason.

When a Flyway migration is required, create its migration-only pull request before every other implementation item and stop the current task as soon as the pull request is created. Leave all remaining items for a separate follow-up task.

### Expected commit boundaries

Create actual commits only when the user explicitly requests them. Commit descriptions are written in Korean.

```text
docs(<domain>): <Korean description of the design documentation>
build(database): <Korean description of the Flyway migration>
feat(<domain>): <Korean description of an independently understandable change>
```

## 8. Verification

### Commands

```bash
./gradlew compileJava
git diff --check
git status --short --untracked-files=all
```

Add the manual execution procedure and expected results required by the feature. State prerequisites such as database, Redis, and environment variables.

### Completion criteria

- [ ] Verified the user outcome defined under Purpose.
- [ ] Verified normal, failure, and boundary flows.
- [ ] Confirmed documentation and Swagger match the actual contract.
- [ ] Recorded verification that could not be performed and why.

### Results

| Item | Result | Evidence |
|---|---|---|
|  | Pending |  |

## 9. Progress log

| Date | Completed work | Next work | Discovered risk |
|---|---|---|---|
| YYYY-MM-DD | Initial document | Review contracts |  |

## 10. Decision log

| Date | Decision | Reason | Alternative or impact |
|---|---|---|---|
| YYYY-MM-DD |  |  |  |

## 11. Completion record

- Completed on:
- Final behavior:
- Unverified items:
- Remaining risks:
- Follow-up work:
