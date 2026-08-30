# Spec: US-001 — Register a new user

**Source:** `stories/US-001.md`
**Generated:** 2026-08-30
**Updated:** 2026-08-30 — open questions 1–4 resolved with the product owner; see Decisions.
**Readiness:** Ready

All acceptance criteria are now buildable: the previously open architectural questions
(session mechanism, success response shape, rate-limit persistence, AC7's applicability) have
been decided (see Decisions below), and the remaining minor open questions (password letter
case, IP extraction, rate-limit window shape) are resolved with reasonable engineering defaults
stated inline in their AC notes.

## Decisions

1. **Session mechanism** — Spring Security is added to the approved stack (`AGENTS.md` updated
   accordingly). Session issuance and the HttpOnly/SameSite=Lax cookie are implemented via
   Spring Security's session management, backed by the app's existing session store
   (HTTP session; no separate custom token table).
2. **Success response (AC1)** — The endpoint returns `201 Created` with the created user's
   `id`/`email` in the body (never the password/hash) and sets the session cookie via a
   response header. "Redirected to the dashboard" is a frontend concern, out of scope for this
   backend story.
3. **Rate limiting (AC8)** — DB-backed: a Postgres-persisted counter (IP + window) via Liquibase
   migration, so the limit is correct across multiple app instances and survives restarts.
4. **AC7 (form state)** — Dropped as not applicable to this backend. There is no server-rendered
   form in this stack; the only backend obligation is what AC2 already requires (never persist
   or log the raw password) plus never echoing the submitted password back in a validation-error
   response body. No dedicated test scenario beyond that.

## Story

As a new visitor
I want to create an account with my email and password
So that I can sign in and use the app

## Acceptance Criteria

1. **AC-1** — Successful registration: valid email + matching password creates an `active`
   account, sets a session cookie, and redirects to the dashboard.
2. **AC-2** — The stored password is a bcrypt hash, never plain text, and never appears in logs.
3. **AC-3** — Email is trimmed and lowercased before storage.
4. **AC-4** — Registering an already-used email fails with *"That email is already registered"*,
   leaves exactly one user with that email, and sets no session cookie.
5. **AC-5** — Mismatched password confirmation fails with *"Passwords do not match"* and creates
   no account.
6. **AC-6** — Invalid input (empty/malformed email, empty/short/weak password) fails with the
   matching message from the table and creates no account.
7. **AC-7** — After a failed validation attempt, the re-rendered form keeps the typed email and
   clears both password fields.
8. **AC-8** — More than 5 registration attempts from one IP within a minute fail with *"Too many
   attempts, please try again later"* and HTTP `429`.

## Traceability Matrix

| AC ID | Requirement | Status | Notes |
|-------|-------------|--------|-------|
| AC-1  | Register → active account, session cookie, redirect | Covered | Per Decision 1/2: Spring Security issues the session and HttpOnly/SameSite=Lax cookie; the endpoint returns `201 Created` with the user body. "Redirect to the dashboard" is explicitly out of scope for the backend. |
| AC-2  | Bcrypt hash, never logged | Covered | Concrete and testable: hash the stored value, assert it differs from the plaintext, assert no log line contains the plaintext. DoD adds the cost-factor (≥12) / argon2id constraint. |
| AC-3  | Trim + lowercase email | Covered | Fully specified input/output pair. |
| AC-4  | Duplicate email rejected | Covered | Message is explicit; per `AGENTS.md` a duplicate maps to `ConflictException` → HTTP 409, so the status code is inferable from project convention even though this AC doesn't state one. |
| AC-5  | Password confirmation mismatch | Covered | Message is explicit; maps to `ValidationException` → HTTP 400 per project convention. |
| AC-6  | Field-level validation table | Covered | Empty/format/length rules are concrete. "Must contain a letter and a number" (row 5) is read literally: at least one letter of any case and at least one digit — no separate upper/lower-case requirement invented. No max length is stated, so none is enforced beyond a generous sanity bound (documented in the plan). |
| AC-7  | Form retains email, clears passwords after failed attempt | Covered | Per Decision 4: dropped as a UI-only concern. The only backend obligation — never echo the submitted password back in a validation-error response — is already implied by AC2 and enforced by the response DTO shape (no password field). |
| AC-8  | Rate limit 5/min/IP → 429 | Covered | Per Decision 3: DB-backed fixed 1-minute-window counter keyed by IP, added via a Liquibase migration. IP is read from `HttpServletRequest.getRemoteAddr()` (no `X-Forwarded-For` trust configured in this story — no proxy/load balancer is part of this stack yet). No `Retry-After` header, since the AC doesn't require one. |

## Corner Cases

- **Boundary & invalid input** — Very long email/password values (no max length stated for
  either); passwords with leading/trailing whitespace (should whitespace be trimmed like email,
  or treated as significant since it changes the password value?); non-ASCII/Unicode characters
  in email or password.
- **Concurrency & ordering** — Two requests for the same email submitted at the same instant
  (double-click, or a client retry after a timed-out first request): the DB's unique index (per
  the Definition of Done) is what ultimately prevents two accounts, so the use case must handle
  the resulting constraint-violation as a duplicate-email failure rather than a raw 500. Also
  relevant at the rate-limit boundary: two requests arriving as the 5th and 6th attempt
  essentially simultaneously.
- **Data exposure & privacy** — AC2 already requires the password never appears in logs; the
  same must hold for the raw request body in any access/error logging, and for the API error
  response itself (a validation error must not echo the submitted password back).
- **Failure & partial failure** — If session issuance is a separate step from account creation
  (e.g., a session-token write to a different table/store), a failure between the two steps
  must not leave an account created with no way to sign in silently, or must be designed so the
  user can just log in normally afterward. Needs the session mechanism decided first (Open
  Question 1) before this can be designed concretely.
- **Repeat & idempotency** — A client that retries a registration call after a network timeout
  (not knowing whether the first attempt succeeded) will look identical to AC4's "duplicate
  email" case from the server's point of view; confirm that's the intended behavior (fails with
  "already registered" on the retry) rather than something more idempotent.
- **State transitions** — The *Out of scope* section notes a future email-verification story
  will introduce a `pending_verification` initial status; this story should avoid hard-coding
  assumptions that `active` is the only possible initial status (e.g., a status enum/column
  rather than an implicit boolean), even though no such column exists yet.

## Open Questions

None remaining. Questions 1–4 (session mechanism, success response, rate-limit persistence,
AC7 applicability) were resolved with the product owner — see Decisions above. Questions 5–7
(password letter-case reading, IP extraction, rate-limit window shape) were resolved with
stated engineering defaults, inline in the AC-6 and AC-8 traceability notes above.
