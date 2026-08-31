# AGENTS.md

Rules below are mandatory. Do not introduce libraries, layers, or patterns not listed here.

## Stack (strict — nothing else)

Java 25 · Spring Boot 4.1.1 · Spring Web · Spring Security · Hibernate/JPA · PostgreSQL · Liquibase · MapStruct · Lombok · JUnit · Mockito · AssertJ · TestContainers · ArchUnit · Spotless · Actuator

Build-time tooling only, not runtime/test dependencies:
- **OpenAPI Generator** (Maven plugin, `interfaceOnly: true`) — generates the REST controller interface and model DTOs from each use case's OpenAPI spec under `src/main/resources/openapi/`; the interface is never hand-written.
- **Maven Failsafe** — runs `*IT`-suffixed integration tests in `mvn verify`, kept out of the fast `mvn test` unit-test run (Surefire's default patterns already exclude them).

Lombok is allowed **only** for getters, setters and constructors. Everything else that would need boilerplate must be a `record`.

Use Java 25 LTS language features where they simplify code, rather than pre-25 idioms out of habit — e.g. `public` is redundant on `main` under Java 25's relaxed launch protocol; the same applies to pattern matching, records, sealed types, virtual threads, etc. wherever they fit.

## Layers

`web → domain → persistence`. Dependencies point one way only.

| Layer | Rule |
|---|---|
| Presentation | `@RestController`, **one class per use case**, implementing an OpenAPI-generated controller interface (contract-first — the spec is the source of truth, never a hand-written interface). No broad controllers (`UserController` ✗, `RegisterUserController` ✓). |
| Domain model | Hibernate `@Entity`, **anemic** — fields + accessors only, no business logic. |
| Domain logic | `@Service` named after the use case: `RegisterUserUseCase` ✓, `UserService` ✗. Annotate with `@Transactional` whenever it calls a repository method — `readOnly = true` if every call is a read, the default (read-write) if it persists anything. The use case owns the transaction boundary; never the repository or controller. |
| Orchestration | Facade only when several use cases must be combined. The facade carries the `@Transactional` boundary for the combined operation; the use cases it calls join that transaction (Spring's default `REQUIRED` propagation) rather than each opening their own. |
| Persistence | `JpaRepository`; `PagingAndSortingRepository` variant only when paging is required. |
| Shared | `common` package — usable by any layer, but must not itself depend on web, domain, or persistence (enforced by the architecture tests). Holds genuinely cross-cutting code only, e.g. the exception categories below. |

Add an interface for a use case **only** when polymorphism is genuinely needed (e.g. strategy pattern). Otherwise the class stands alone — no `XxxImpl`.

## DTOs & mapping

- All request/response/command/result types are `record`s. No Lombok there.
- Every cross-layer conversion goes through MapStruct: web ↔ domain ↔ persistence, both directions.
- No hand-written mappers. Entities never cross into the web layer.

## Error handling

- Three unchecked failure categories live in `common.exception`: `NotFoundException`,
  `ConflictException`, `ValidationException`. They carry no HTTP or web-layer concerns, only
  what the failure *is* — that's what makes them safe for the domain layer to depend on.
- A concrete domain failure is an unchecked exception in the domain layer that extends one of
  those categories (e.g. `UserNotFoundException extends NotFoundException`), thrown by the use
  case. Never a bare `RuntimeException`, and never a new category invented per use case — reuse
  an existing one unless a failure genuinely doesn't fit any of the three.
- One shared `@RestControllerAdvice` in `common.web` maps each category to its HTTP status
  (`NotFoundException` → 404, `ConflictException` → 409, `ValidationException` → 400) and a
  small generic error body. One class project-wide, not one per use case — a new concrete
  exception under an existing category needs no change here.
- Each OpenAPI spec declares the realistic non-2xx responses its use case's exceptions produce,
  not just the success response.

## Testing — definition of done

A feature is implemented **if and only if** the code *and* all of its tests exist and pass.

- **Unit tests**: all cases — happy path, edge cases, failures. Mock collaborators with Mockito; assertions may use AssertJ or JUnit's own `Assertions`.
- **Integration tests**: happy path only, real HTTP via the JDK's `java.net.http.HttpClient` against a running embedded server, PostgreSQL via TestContainers (other infra: a TestContainers module if one exists, a mock otherwise). Run via Maven Failsafe (`*IT` suffix, `mvn verify`), separate from the unit-test run. Every integration test class shares one base class so the Spring context (and container) loads once per suite, not once per class.
- **ArchUnit tests**: encode the rules above, at minimum:
    - web must not access persistence or entities directly
    - persistence must not depend on web
    - `@Entity` classes contain no business logic
    - classes ending in `Service` are forbidden; use-case classes end in `UseCase`
    - `@RestController` classes expose one use case each
    - mappers are MapStruct `@Mapper` interfaces

## Database migrations

- All schema changes go through Liquibase YAML changesets — never manual DDL, never Hibernate
  auto-generated schema. `spring.jpa.hibernate.ddl-auto` must stay `none`/`validate`; Liquibase
  changesets are the only source of DDL.
- One changeset per file, timestamp-prefixed (`YYYYMMDDHHmmss_description.yaml`), under
  `src/main/resources/db/changelog/changes/`.
- Master changelog at `src/main/resources/db/changelog/db.changelog-master.yaml` uses
  `includeAll` against that directory, so new changeset files are picked up automatically —
  no per-file registration needed.
- Every `changeSet` includes an explicit `rollback` block, even for change types Liquibase can
  auto-rollback — explicit is more reliable across change types than relying on inference.

## Configuration profiles

- `application.yml` is the base config: every environment-dependent value uses a `${VARIABLE}`
  placeholder (`spring.datasource.url`, `spring.datasource.username`,
  `spring.datasource.password`) — for real deployments where those env vars are actually set.
- `application-local.yml` activates under the `local` Spring profile and holds **plaintext**
  overrides for local development only — `spring.datasource.url` pointing at
  `localhost:5432` (a locally running PostgreSQL, normally via Docker), plus matching
  username/password. It contains only the keys that actually differ from `application.yml`;
  Spring Boot merges the two, so shared settings (JPA, Liquibase) aren't duplicated.
- A `@Profile("local")` startup component (`common.config.LocalProfileStartupWarning`) logs a
  warning reminding the developer that PostgreSQL must already be running (e.g. via Docker)
  before the app will boot successfully, since the local profile points at `localhost` rather
  than a container-orchestrated hostname.

## Ops & tooling

- SLF4J logging: `info` for business events, `debug` for diagnostics. Never log secrets or PII.
- Actuator enabled, health endpoint exposed.
- Spotless: run `spotlessApply` before every commit; the build fails on formatting violations.

