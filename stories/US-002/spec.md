# Spec: US-002 — Verify a new user's email

**Source:** `stories/US-002.md`
**Generated:** 2026-09-05
**Updated:** 2026-09-05 — open questions 1–4 resolved with the product owner; see Decisions.
**Updated:** 2026-09-05 — Decision 5 added: token delivery corrected from logging to a response
field after catching a conflict with `AGENTS.md`'s "never log secrets or PII" rule during
implementation.
**Readiness:** Ready

All acceptance criteria are now buildable: the three architectural open questions (token storage
at rest, success response body, re-registration behavior) are decided (see Decisions below), and
the remaining minor open question (empty/missing token) is resolved with a reasonable engineering
default stated inline.

## Decisions

1. **Token storage (Open Question 1)** — Tokens are hashed at rest (SHA-256), mirroring the
   password-hashing pattern already in the codebase. The raw token exists only in memory at
   issuance (for the response returned to the caller, per Decision 5) and in the caller's
   request at verification time; the stored row never holds a value usable on its own from a DB
   read.
2. **Success response body (Open Question 2)** — `200 OK` with the same `{id, email}` shape as
   US-001's `RegisteredUser`, for consistency with the registration endpoint.
3. **Re-registration of a still-pending email (Open Question 3)** — Unchanged from US-001:
   `existsByEmail` already rejects it as "That email is already registered" regardless of
   status. No new logic needed; AC-1 doesn't change registration's existing duplicate-email
   behavior.
4. **Empty/missing token (Open Question 4)** — Folds into AC-3's `404`/"Invalid or expired
   verification link" path rather than a separate `400`. Treating a malformed token identically
   to an unrecognized one avoids leaking whether a supplied value was merely absent versus wrong,
   which is the same non-distinguishing posture AC-3/AC-5/AC-6 already take toward each other.
5. **Token delivery (not one of the original open questions — caught during implementation)** —
   `AGENTS.md`'s "never log secrets or PII" rule is unconditional; a verification token is a
   bearer credential, so logging it (as the story originally specified) would violate that rule.
   Corrected: the raw token/link is returned directly in the registration response body instead
   — a temporary, documented stand-in for real email delivery, visible only to the caller who
   made the registration request, never written to a log line at any level.

## Story

As a new visitor who just registered
I want to confirm my email address via a verification link
So that my account becomes fully active and the app knows my email is real

## Acceptance Criteria

1. **AC-1** — Registration creates a `pending_verification` account, generates a single-use
   token, returns the token/link in the response body (Decision 5), and sets no session cookie.
2. **AC-2** — Verifying with a valid, unexpired, unused token activates the account (`200 OK`)
   and the token becomes unusable afterward.
3. **AC-3** — An unrecognized token fails with *"Invalid or expired verification link"*, `404`.
4. **AC-4** — An expired token fails the same way as AC-3, and the account stays
   `pending_verification`.
5. **AC-5** — An already-used token fails the same way as AC-3.
6. **AC-6** — Any token tied to an already-`active` account fails the same way as AC-3 (no
   distinct "already verified" message, since a successful prior verification already consumed
   the token — this is really AC-5 from a different angle).

## Traceability Matrix

| AC ID | Requirement | Status | Notes |
|-------|-------------|--------|-------|
| AC-1  | Register → pending account + returned token, no cookie | Covered | Per Decision 1: token is generated cryptographically random and stored SHA-256-hashed. Per Decision 5: the raw token/link is returned in the response body, never logged. Per Decision 3: re-registering a still-pending email keeps US-001's existing "already registered" rejection unchanged. |
| AC-2  | Valid token → active, 200, single-use | Covered | Per Decision 2: response body is `{id, email}`, matching `RegisteredUser`. Atomic single-use consumption under a race (two requests, same valid token, simultaneously) is a build-time concern, not a spec gap — see Corner Cases, addressed the same way US-001 handled the email-uniqueness race (constraint-backed, not a pre-check alone). |
| AC-3  | Unknown token → 404 | Covered | Concrete message and status. |
| AC-4  | Expired token → 404, account untouched | Covered | 24h expiry window is explicit in the story's Context. |
| AC-5  | Reused token → 404 | Covered | Concrete message and status; depends on AC-2's atomicity for correctness under race. |
| AC-6  | Token on already-active account → 404 | Covered | Explicitly folded into AC-5's mechanism by the story itself — no new behavior to build. |

## Corner Cases

- **Boundary & invalid input** — empty or missing `token` query parameter. Per Decision 4, this
  folds into AC-3's `404`/"Invalid or expired verification link" path rather than a separate
  `400`.
- **Concurrency & ordering** — two verification calls with the same valid token arriving at
  once (double-click, retried request). Without an atomic "consume" step (mirroring US-001's
  `saveAndFlush` + unique-constraint pattern for email registration), both could read the token
  as still valid before either marks it used, activating twice or racing on which one "wins" —
  matters because AC-2 promises the token becomes unusable, and AC-5 promises the second attempt
  fails cleanly rather than erroring.
- **Repeat & idempotency** — registering the same email again while the first registration is
  still `pending_verification` (token unused, not expired). Per Decision 3, US-001's existing
  `existsByEmail` check keeps rejecting this as "already registered" unchanged.
- **Data exposure & privacy** — per Decision 5, the raw token is returned in the registration
  response body (never logged), a temporary stand-in for real email delivery. That response is
  the only place the token is exposed; nothing else in this story's flow writes it anywhere
  else. Worth confirming this doesn't silently outlive the "no real email transport" condition
  it's scoped to — DoD already flags revisit-when-a-mailer-exists.
- **State transitions** — `pending_verification → active` is the only forward transition this
  story defines; there's no path back (e.g. a stale pending account) and none is implied as
  needed, so no gap here.

## Open Questions

None remaining. Questions 1–3 (token storage at rest, success response body, re-registration
behavior for a still-pending email) were resolved with the product owner — see Decisions above.
Question 4 (empty/missing token) was resolved with a stated engineering default, in Decision 4
above.
