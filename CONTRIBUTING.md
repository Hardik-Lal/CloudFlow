# Contributing to CloudFlow

## Branching strategy

CloudFlow uses **GitHub Flow** with a protected `main` branch.

| Branch | Purpose |
| --- | --- |
| `main` | Always releasable. Protected: no direct pushes, CI must pass, at least one approving review. |
| `feature/<phase>-<short-name>` | New functionality, e.g. `feature/p2-github-oauth` |
| `fix/<short-name>` | Bug fixes |
| `docs/<short-name>` | Documentation-only changes |
| `chore/<short-name>` | Tooling, dependencies, CI |

- Branch from `main`, keep branches short-lived, and open a pull request early.
- Pull requests are **squash-merged**. The squash commit message follows Conventional Commits.
- Releases are tagged `vMAJOR.MINOR.PATCH` on `main`.

## Commit messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <summary in imperative mood>
```

- Types: `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`, `build`, `perf`.
- Scopes: `backend`, `frontend`, `ai`, `infra`, `docs`, or a module name (`auth`, `deployment`, …).

Example: `feat(auth): issue JWT access tokens after GitHub login`

## Coding standards

### General

- Follow SOLID, DRY, and KISS. Each class or module has one responsibility.
- Use meaningful names. No abbreviations beyond well-known ones (`id`, `dto`, `url`).
- Comment *why*, not *what*. Document non-obvious logic.
- No secrets in code, logs, or test fixtures. Configuration comes from environment variables.
- Every behaviour change comes with tests and, where contracts change, a `docs/` update.

### Backend (Java 21, Spring Boot)

- Formatting: google-java-format, enforced by Spotless (`./gradlew spotlessApply`).
- The build fails on compiler warnings (`-Werror`).
- Modular monolith: code lives in its module package (`com.cloudflow.<module>`), split into
  `domain`, `dto`, `repository`, `service`, and `web`. Modules talk to each other only through service
  APIs and events (see `docs/architecture.md` §4).
- Use constructor injection only (no field `@Autowired`).
- Controllers accept and return DTOs (Java `record`s). JPA entities never leave the service layer.
- Transactions are declared on service methods.
- Errors are thrown as domain exceptions from `common.exception` and rendered as RFC 9457 Problem Details.
- Schema changes go in a new Flyway migration. Existing migrations are never edited.
- Tests: JUnit 5 + AssertJ. Use Testcontainers PostgreSQL for repository and integration tests, and
  `MockMvcTester` for web-layer tests.

### Frontend (Next.js, TypeScript)

- Formatting: Prettier (with the Tailwind plugin). Linting: ESLint (`eslint-config-next`). TypeScript `strict`.
- Server state goes through TanStack Query hooks (`src/hooks`). Client state goes in Zustand stores (`src/stores`).
- All HTTP calls go through the typed API client in `src/lib/api`.
- UI is built from shadcn/ui primitives in `src/components/ui`. Feature components live in `src/components/<feature>`.
- Do not use `any`. Model API responses with explicit types.

### AI service (Python 3.12, FastAPI)

- Formatting and linting: Ruff (`uv run ruff format`, `uv run ruff check`).
- Add type hints on all functions. Request and response bodies are Pydantic models.
- Routes stay thin. Logic lives in `app/services` and `app/rag`.
- Tests: pytest, and warnings are treated as errors in CI.

## Local checks before opening a PR

```bash
(cd backend && ./gradlew build)
(cd frontend && npm run format:check && npm run lint && npm run typecheck && npm run build)
(cd ai-service && uv run ruff check . && uv run ruff format --check . && uv run pytest)
```
