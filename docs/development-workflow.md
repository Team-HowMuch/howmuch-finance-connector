# Development Workflow

## 작업 시작

```bash
pwd
git status --short --untracked-files=all
git fetch origin main
gh pr list --base main --state open
```

루트와 작업 경로의 `AGENTS.md`, 관련 문서와 Feature Plan을 읽고 최신 `origin/main`에서 branch를 만든다.

## Flyway gate

Persistence schema가 필요하면 다음 순서를 지킨다.

1. 최신 migration 번호와 열린 migration PR을 확인한다.
2. migration 전용 branch에서 단 하나의 versioned SQL만 추가한다.
3. 실제 PostgreSQL에서 migration을 검증한다.
4. commit, push, `main` 대상 PR을 만든다.
5. PR URL을 보고하고 작업을 종료한다.

Feature Plan, Java, 설정, 테스트를 migration-only PR에 섞지 않는다.

## 이슈와 PR

이슈는 `현재 상황 / 확인 근거 / 기대 효과 / 작업 범위 / 완료 조건 / 관련 이슈` 순서로 작성하고 `enhancement` 또는 `bug`, `priority: P1` 라벨을 적용한다.

PR은 다음 형식을 사용한다.

```markdown
## 변경 내용
- 목적과 실제 변경 결과

## 검증
- [x] 실행한 명령과 결과
- [ ] 실행하지 못한 항목과 이유

## 확인 필요
- 카나리·배포·호환성 확인 사항

## 관련 이슈
- Closes #...
- Related: Team-HowMuch/...#...
```

## 최종 검증

```bash
./gradlew clean build --no-daemon
git diff --check
git status --short --untracked-files=all
```

실패하거나 실행하지 않은 항목은 PR과 Feature Plan에 그대로 기록한다.
