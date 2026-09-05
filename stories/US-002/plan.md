# Implementation Plan: US-002 — Verify a new user's email

**Spec:** `stories/US-002/spec.md`
**Validation:** `stories/US-002/validation.md` — PASS
**Generated:** 2026-09-05
**Updated:** 2026-09-05 — Decision 5 (spec) corrected token delivery from logging to returning it
in the registration response, after catching a conflict with `AGENTS.md`'s "never log secrets or
PII" rule mid-implementation. `IssueVerificationTokenUseCase`, `RegisterUserFacade`, and a new
`RegistrationResult` record are updated below to carry the raw token instead of logging it.

## Existing Code Impact

Reused as-is:
- `User` (`user.domain`) / `UserRepository` (`user.persistence`) — verification activates an
  existing `User` row; no new fields needed beyond the status change already modeled by
  `UserStatus`.
- `RegisteredUser` (`user.domain`) — Decision 2 reuses this `{id, email}` record as
  `VerifyEmailUseCase`'s return shape. Registration's own response now wraps it in the new
  `RegistrationResult` below rather than extending it directly, so `VerifyEmailUseCase` keeps
  returning the plain, unchanged `RegisteredUser`.
- `common.exception.NotFoundException` — an invalid/expired/used token is "the thing looked up
  doesn't (usefully) exist," which is exactly this category; no new exception category per
  AGENTS.md's "reuse an existing one unless a failure genuinely doesn't fit."
- `common.web.RestExceptionHandler` — already maps `NotFoundException` → 404; no change needed
  for a new concrete exception under that category.
- `RegisterUserFacade` (`user.domain`) — already the `@Transactional` orchestration point for
  registration; extended (not replaced) to also issue a verification token in the same
  transaction, per AGENTS.md's Orchestration rule (use cases it calls join its transaction).

Modified:
- `UserStatus` — add `PENDING_VERIFICATION` alongside the existing `ACTIVE`.
- `RegisterUserUseCase.register` — sets the new user's status to `PENDING_VERIFICATION` instead
  of `ACTIVE`.
- `RegisterUserFacade.register` — after `RegisterUserUseCase` succeeds, calls the new
  `IssueVerificationTokenUseCase` in the same transaction, and now returns a new
  `RegistrationResult(RegisteredUser user, String verificationToken)` record instead of a bare
  `RegisteredUser`, so the raw token reaches the controller without ever being logged
  (per corrected Decision 5).
- `RegisterUserController.registerUser` — must stop calling `signIn(...)` unconditionally now
  that registration no longer always yields an immediately-usable account (AC-1: no session
  cookie for a pending account). Simplest correct rule: never sign in from this endpoint anymore
  — a `pending_verification` account has nothing to sign into yet, and US-001's AC1 (session on
  success) is superseded by this story for the registration path. The response model
  (`RegisteredUserResponse`, via `user-story-rest-api`) needs a new field for the
  verification token/link, sourced from `RegistrationResult.verificationToken()`.

Net new — package `verification`, mirroring the existing `user`/`ratelimit` feature-package
split (`domain` for use cases/exceptions, `persistence` for the entity + repository, `web` for
the controller):
- `verification.persistence.VerificationToken` (`@Entity`) and
  `verification.persistence.VerificationTokenRepository`.
- `verification.domain.IssueVerificationTokenUseCase`,
  `verification.domain.VerifyEmailUseCase`,
  `verification.domain.InvalidOrExpiredVerificationTokenException`.
- `verification.web.VerifyEmailController` (+ its MapStruct web mapper if the generated model
  needs mapping beyond a plain string).

**Design call, stated explicitly:** the codebase currently has two different conventions for
where an `@Entity` lives — `User` sits in `user.domain`, but `RegistrationAttempt` sits in
`ratelimit.persistence`. `VerificationToken` is a technical, single-purpose persistence record
backing one use case's state machine (issued/consumed/expired) rather than a core business
object referenced across the codebase — closer in spirit to `RegistrationAttempt` than to
`User`. This plan follows the `RegistrationAttempt` precedent and places it in
`verification.persistence`. Flagging this so it isn't read as an oversight.

## Component Plan

| AC ID | Layer | Component | New / Modify | Notes |
|-------|-------|-----------|---------------|-------|
| AC-1 | Domain model | `UserStatus` | Modify | Add `PENDING_VERIFICATION`. |
| AC-1 | Domain logic | `RegisterUserUseCase` | Modify | Sets status to `PENDING_VERIFICATION`. |
| AC-1 | Persistence | `VerificationToken` (`@Entity`) | New | Fields: `id` (UUID), `userId` (UUID, FK to `users.id`), `tokenHash` (String, unique — SHA-256 hex of the raw token, per Decision 1), `expiresAt` (Instant, `createdAt + 24h`), `consumedAt` (Instant, nullable), `createdAt` (Instant). |
| AC-1 | Persistence | `VerificationTokenRepository` | New | `findByTokenHash(String)`; a `@Modifying` `consumeIfUnused(UUID id, Instant now)` update for AC-2/AC-5's atomic single-use guard (see below). |
| AC-1 | Domain logic | `IssueVerificationTokenUseCase` | New | Generates a cryptographically random raw token (`SecureRandom`, base64url), hashes it (SHA-256) for storage, persists the `VerificationToken` row, and **returns the raw token to its caller** (never logs it — corrected Decision 5). Called by `RegisterUserFacade`, not the controller — it must run in the same transaction as user creation so a token is never issued for a user row that didn't actually commit. |
| AC-1 | Domain model | `RegistrationResult` (record, `user.domain`) | New | `RegistrationResult(RegisteredUser user, String verificationToken)` — the facade's combined return shape, carrying the raw token up to the controller without it ever touching a log line. |
| AC-1 | Orchestration | `RegisterUserFacade` | Modify | Adds the `IssueVerificationTokenUseCase` call after `RegisterUserUseCase.register(...)` succeeds, same `@Transactional` boundary; returns `RegistrationResult` instead of a bare `RegisteredUser`. |
| AC-1 | Presentation | `RegisterUserController` | Modify | Removes the unconditional `signIn(...)` call (no session cookie for a pending account). |
| AC-2, AC-5, AC-6 | Domain logic | `VerifyEmailUseCase` | New | Hashes the incoming raw token, looks it up via `VerificationTokenRepository.findByTokenHash`. Not found or expired (`expiresAt` before now) → `InvalidOrExpiredVerificationTokenException`. Otherwise attempts the atomic `consumeIfUnused` update; 0 rows affected (already consumed — covers both AC-5's direct reuse and AC-6's "token behind an already-active account," since that account only got active by consuming this same token once already) → same exception. On success, loads the `User` by `userId`, sets status `ACTIVE`, returns `RegisteredUser(id, email)` per Decision 2. |
| AC-3, AC-4 | Domain logic | `InvalidOrExpiredVerificationTokenException extends NotFoundException` | New | One exception for "not found," "expired," and "already used" — the spec's Decision 4 and AC-3/5/6 all deliberately collapse these into one indistinguishable outcome, so one exception type is correct, not a gap. |
| AC-2, AC-3, AC-4, AC-5, AC-6 | Presentation | `VerifyEmailController` implementing an OpenAPI-generated interface | New | `GET /api/users/verify?token=...`; missing/blank `token` is treated as a malformed value that fails the same lookup (Decision 4) rather than a separate validation branch — no special-casing needed if the use case just tries the hash-and-lookup for whatever string it's given, empty included. |
| — | Config | `SecurityConfig` | Modify | Add `GET /api/users/verify` to the `permitAll()` matcher — an unauthenticated visitor must be able to hit it. |

## Build Sequence

1. `UserStatus` — add `PENDING_VERIFICATION` first; every other component's compilation depends
   on this enum value existing.
2. `VerificationToken` entity + `VerificationTokenRepository` — the persistence layer other new
   domain classes call into; nothing above it can be written against a repository that doesn't
   exist yet.
3. Liquibase changeset for the new `verification_token` table (via `user-story-liquibase`, once
   the entity's exact columns are settled) — must exist before any integration test can run
   against a real database.
4. `IssueVerificationTokenUseCase` + its unit tests — self-contained (one repository dependency),
   testable in isolation before touching the registration flow.
5. `RegisterUserUseCase` status change, `RegisterUserFacade` wiring, `RegisterUserController`
   signIn removal — modifies existing, tested (US-001's tests) behavior, so done after the new
   token-issuing piece it depends on already works alone.
6. `InvalidOrExpiredVerificationTokenException` + `VerifyEmailUseCase` + its unit tests — the
   core new business logic; depends on step 2's repository and the exception existing.
7. OpenAPI spec + generated interface + `VerifyEmailController` (via `user-story-rest-api`) —
   the web layer, last, since it only delegates to a use case that must already work.
8. `SecurityConfig` update to `permitAll()` the new endpoint — needed before any integration
   test can call it without a 401/403.
9. Integration test (via `user-story-integration-tests`) — happy path only, once the full stack
   (steps 1–8) is wired end to end.

## Test Plan

| AC ID | Test Type | Test Class | Verifies |
|-------|-----------|------------|----------|
| AC-1 | Unit | `RegisterUserUseCaseTest` (extend existing) | New user is persisted with status `PENDING_VERIFICATION`, not `ACTIVE`. |
| AC-1 | Unit | `IssueVerificationTokenUseCaseTest` | The persisted `tokenHash` is not equal to the raw value returned by the method, `expiresAt` is ~24h out, and the raw token actually hashes to the stored value. |
| AC-1 | Unit | `RegisterUserFacadeTest` (extend existing) | `RegistrationResult` carries the same raw token `IssueVerificationTokenUseCase` returned, alongside the `RegisteredUser` from `RegisterUserUseCase`. |
| AC-1 | Unit | `RegisterUserControllerTest` or facade-level test | No `SecurityContext`/session is established for a fresh registration (no more unconditional sign-in). |
| AC-2 | Unit | `VerifyEmailUseCaseTest` | Valid, unexpired, unused token → user status becomes `ACTIVE`; returns `RegisteredUser(id, email)`. |
| AC-3 | Unit | `VerifyEmailUseCaseTest` | Token hash with no matching row → `InvalidOrExpiredVerificationTokenException`. |
| AC-4 | Unit | `VerifyEmailUseCaseTest` | Token found but `expiresAt` in the past → same exception; user status unchanged (still `PENDING_VERIFICATION`). |
| AC-5 | Unit | `VerifyEmailUseCaseTest` | Token already has `consumedAt` set (or `consumeIfUnused` returns 0 rows) → same exception. |
| AC-6 | Unit | `VerifyEmailUseCaseTest` | Re-verifying a token whose one-time use already flipped the account to `active` behaves identically to AC-5 (same code path, no special case) — asserted as a regression case, not new logic. |
| AC-1, AC-2 | Integration (`*IT`, Failsafe) | `VerifyEmailIT` | Real HTTP: register → read the raw token straight from the registration response body (per corrected Decision 5, no fixture workaround needed) → call `GET /api/users/verify?token=...` → `200` with `{id, email}` → user row is `active` in the real Postgres container. |

ArchUnit: the existing layered rules (web-must-not-touch-persistence, `@Entity` no business
logic, `UseCase`/`Controller` naming, MapStruct-only mappers) already apply generically to the
new `verification` package with no rule changes needed — confirm this after the code exists
rather than assuming.

## Open Risks

- **OpenAPI codegen model collision**: `register-user.yaml` and a new `verify-email.yaml` would
  both generate into the same `org.mike.usermanagement.web.generated.model` package (per the
  existing `pom.xml` plugin config, one `<execution>` per spec, shared `modelPackage`). Reusing
  `RegisteredUserResponse` for the verify endpoint's success body (Decision 2) needs the new spec
  to reference the *same* schema rather than redeclare one with the same name — `user-story-rest-api`
  should resolve this via a shared components file or `$ref` across specs rather than duplicating
  the schema, to avoid a generator collision.
- ~~Reading the token in the integration test~~ — resolved by corrected Decision 5: the raw
  token is now returned directly in the registration response body, so the IT reads it from
  there with no fixture workaround needed.
