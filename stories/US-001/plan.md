# Implementation Plan: US-001 — Register a new user

**Spec:** `stories/US-001/spec.md`
**Validation:** `stories/US-001/validation.md` — PASS
**Generated:** 2026-08-30

## Existing Code Impact

The codebase is a fresh Spring Boot skeleton (`de6ac78 Initial commit`) with no domain code:
only `UserManagementApplication`, `common.config.LocalProfileStartupWarning`, config YAML, and
placeholder tests exist. There is no `User` entity, no `common.exception` package, no
`common.web` advice class, no OpenAPI spec directory contents, and no Liquibase changelog yet.
Everything this story needs is net new.

Two changes already applied ahead of this plan, per the product-owner decisions in the spec:
- `AGENTS.md` stack line now includes Spring Security.
- `pom.xml` now has `spring-boot-starter-security` (main) and `spring-security-test` (test).

## Component Plan

| AC ID | Layer | Component | New / Modify | Notes |
|-------|-------|-----------|---------------|-------|
| AC-1,2,3,4,5,6 | Domain model | `org.mike.usermanagement.user.domain.User` | New | Anemic `@Entity`: `id`, `email` (unique), `passwordHash`, `status`, `createdAt`. Lombok getters/setters/constructors only. |
| AC-1,4 | Persistence | `org.mike.usermanagement.user.persistence.UserRepository` | New | `JpaRepository<User, UUID>` + `findByEmail`/`existsByEmail`. |
| AC-1..7 | Domain logic | `org.mike.usermanagement.user.domain.RegisterUserUseCase` | New | `@Service`, `@Transactional`. Validates, normalizes email, checks uniqueness, hashes password via `PasswordEncoder`, saves `User`, returns a result record. Throws domain exceptions for AC4–AC6. |
| AC-2 | Common/config | `org.mike.usermanagement.common.config.SecurityConfig` | New | Configures `PasswordEncoder` bean (`BCryptPasswordEncoder`, strength ≥ 12) and the HTTP security filter chain (session management, CSRF stance for a stateless-except-registration JSON API, permit `/api/users` POST). |
| AC-4 | Common | `org.mike.usermanagement.common.exception.ConflictException` | New | Per `AGENTS.md`: base unchecked exception category, no web/HTTP concerns. |
| AC-5,6 | Common | `org.mike.usermanagement.common.exception.ValidationException` | New | Same category pattern. |
| AC-4 | Domain | `org.mike.usermanagement.user.domain.EmailAlreadyRegisteredException extends ConflictException` | New | Thrown by the use case; carries message *"That email is already registered"*. |
| AC-5,6 | Domain | `org.mike.usermanagement.user.domain.PasswordMismatchException extends ValidationException` | New | *"Passwords do not match"*. |
| AC-5,6 | Domain | `org.mike.usermanagement.user.domain.InvalidRegistrationException extends ValidationException` | New | Carries the specific AC6 field-level message (empty email, invalid email, empty password, too short, missing letter/number). |
| AC-1,3,7 | Domain | `RegisterUserCommand` (record) | New | `email`, `password`, `passwordConfirmation` — input to the use case. |
| AC-1 | Domain | `RegisteredUser` (record) | New | `id`, `email` — result of a successful registration; never carries the password/hash. |
| AC-8 | Persistence | `org.mike.usermanagement.ratelimit.persistence.RegistrationAttempt` (`@Entity`) | New | Per-IP counter row: `ipAddress`, `windowStart`, `attemptCount`. Backs the DB-persisted rate limiter (Decision 3 in the spec). |
| AC-8 | Persistence | `org.mike.usermanagement.ratelimit.persistence.RegistrationAttemptRepository` | New | `JpaRepository` + a lookup by `ipAddress`. |
| AC-8 | Domain logic | `org.mike.usermanagement.ratelimit.domain.RegistrationRateLimiterUseCase` | New | `@Service`, `@Transactional`. Fixed 1-minute window per IP (per spec AC-8 note): increments/reads the counter, throws when the 6th attempt lands inside the current window. |
| AC-8 | Common | `org.mike.usermanagement.common.exception.TooManyAttemptsException` (or reuse `ConflictException`) | New | Needs a 429 mapping — `AGENTS.md`'s three categories map to 404/409/400 only, so this is a genuine fourth case not covered by an existing category; flagged under Open Risks. |
| AC-1,4,5,6,8 | Presentation | `src/main/resources/openapi/register-user.yaml` | New | OpenAPI spec: `POST /api/users`, request body (email/password/passwordConfirmation), 201 response body (`id`/`email`), 400/409/429 error responses. |
| AC-1,4,5,6,8 | Presentation | `org.mike.usermanagement.user.web.RegisterUserController` | New | Implements the generated interface; delegates to `RegistrationRateLimiterUseCase` then `RegisterUserUseCase`; sets the session cookie via Spring Security's session on success. |
| all | Common | `org.mike.usermanagement.common.web.RestExceptionHandler` | New | One `@RestControllerAdvice` mapping `NotFoundException`→404, `ConflictException`→409, `ValidationException`→400, plus the new 429 case. |
| all | Persistence/DB | Liquibase changesets | New | `users` table with a unique index on `email`; `registration_attempt` table for the rate limiter. |
| AC-1..8 | Architecture | ArchUnit rules | Modify (if not already generic) | Confirm existing/base rules from `AGENTS.md` (web→domain→persistence, `UseCase` naming, entities anemic) apply automatically to the new packages; add a rule only if a gap is found. |

## Build Sequence

1. **Liquibase changesets** (`users`, `registration_attempt`) — nothing above them compiles
   against a real schema without this, and the `user-story-liquibase` skill itself needs the
   `@Entity` classes to exist first, so this actually happens *after* step 2 in practice; listed
   here for schema-dependency clarity, executed in the order below.
2. **Entities** (`User`, `RegistrationAttempt`) — persistence and domain logic both depend on
   these existing first.
3. **Liquibase changesets**, generated from the entities (`user-story-liquibase` skill) — must
   follow the entities so column types/constraints are derived from real JPA mappings, not
   guessed.
4. **Repositories** (`UserRepository`, `RegistrationAttemptRepository`) — thin, depend only on
   the entities.
5. **Common exception categories** (`ConflictException`, `ValidationException`, new 429 case) —
   needed before any domain exception can extend them.
6. **Domain exceptions** (`EmailAlreadyRegisteredException`, `PasswordMismatchException`,
   `InvalidRegistrationException`) — needed before the use case can throw them.
7. **`SecurityConfig`** (`PasswordEncoder` bean + filter chain) — the use case depends on the
   `PasswordEncoder` bean existing.
8. **`RegistrationRateLimiterUseCase`** and **`RegisterUserUseCase`** — the core domain logic;
   depend on everything above.
9. **Unit tests** for both use cases — can run as soon as the use case + its mocked
   collaborators exist, before the web layer is built.
10. **OpenAPI spec + generated controller interface + `RegisterUserController`** — the web layer
    is last per the layering rule (`web → domain → persistence`) and depends on the use cases
    already existing.
11. **`RestExceptionHandler`** — wired once the concrete exceptions exist.
12. **Integration test** — needs the full HTTP path (controller → use cases → DB) wired up.
13. **ArchUnit check / full build** — final verification pass.

## Test Plan

| AC ID | Test Type | Test Class | Verifies |
|-------|-----------|------------|----------|
| AC-1  | Unit | `RegisterUserUseCaseTest` | Valid command → user saved with `active` status, bcrypt hash, correct email; returns `RegisteredUser`. |
| AC-1  | Integration | `RegisterUserIT` | Full HTTP POST happy path → 201, response body has id/email, `Set-Cookie` header present and `HttpOnly`/`SameSite=Lax`, row exists in DB. |
| AC-2  | Unit | `RegisterUserUseCaseTest` | Stored `passwordHash` is a bcrypt hash (`$2` prefix), not equal to the raw password; a test log-capture assertion confirms no log line contains the raw password. |
| AC-3  | Unit | `RegisterUserUseCaseTest` | `  Ada@Example.COM  ` → stored/returned email is `ada@example.com`. |
| AC-4  | Unit | `RegisterUserUseCaseTest` | Existing email → throws `EmailAlreadyRegisteredException`; repository `save` never called; exactly one row remains. |
| AC-5  | Unit | `RegisterUserUseCaseTest` | Mismatched confirmation → throws `PasswordMismatchException`; no save. |
| AC-6  | Unit | `RegisterUserUseCaseTest` | One parameterized/`@ValueSource`-style case per table row (empty email, invalid format, empty password, short password, letter-or-number-missing password) → `InvalidRegistrationException` with the exact message; no save. |
| AC-7  | — | *(none — dropped per Decision 4; covered indirectly by AC-2's log/echo assertions)* | N/A |
| AC-8  | Unit | `RegistrationRateLimiterUseCaseTest` | 5 attempts within the window succeed/pass through; the 6th within the same minute throws the rate-limit exception; a request in a new window resets the count. |
| AC-8  | Integration | `RegisterUserIT` (or a dedicated `RegistrationRateLimitIT`) | 6 rapid POSTs from the same client IP → the 6th returns 429 with the exact message. |
| Web layer | Unit | `RegisterUserControllerTest` (MockMvc, mocked use cases) | Each domain exception maps to the right HTTP status via `RestExceptionHandler` (409/400/429). |
| Architecture | ArchUnit | existing/new rule test | New `web`, `domain`, `persistence`, `ratelimit` packages obey layering; `RegisterUserUseCase`/`RegistrationRateLimiterUseCase` named correctly; entities stay anemic. |

Per `AGENTS.md`, integration tests are happy-path only — AC4–AC6/AC8 failure scenarios are
unit-test obligations, not integration-test ones; the one exception carried into IT is AC8
because rate limiting is inherently a multi-request, real-HTTP behavior that a unit test on the
use case alone can partially but not fully validate (worth having both).

## Open Risks

- **429 doesn't fit the three existing exception categories.** `AGENTS.md` explicitly enumerates
  `NotFoundException`/`ConflictException`/`ValidationException` → 404/409/400 and says "never a
  new category invented per use case — reuse an existing one unless a failure genuinely doesn't
  fit any of the three." Rate limiting genuinely doesn't fit any of them semantically, so this
  plan introduces one new category (`TooManyRequestsException` in `common.exception`) rather
  than force-fitting 429 onto `ConflictException`. Flagging this explicitly since it's a
  deviation from "reuse an existing one" — worth a quick sanity check before or during
  implementation rather than discovering it mid-build.
- **Spring Security default behavior needs deliberate configuration**, not just the dependency
  add: without an explicit `SecurityFilterChain`, Spring Security's autoconfiguration will lock
  down all endpoints behind a login page/basic auth by default, which would break the public
  `POST /api/users` endpoint. `SecurityConfig` must explicitly permit that route.
- **CSRF vs. a JSON registration API**: Spring Security's default CSRF protection is designed for
  browser form submissions. Since this is a JSON API and the session cookie is set as a
  *result* of registration (not used to authenticate the registration request itself), CSRF
  protection can reasonably be disabled for this endpoint specifically rather than project-wide
  — worth confirming during `SecurityConfig` implementation rather than silently disabling it
  everywhere.
