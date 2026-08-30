---
name: user-story-business-logic
description: >
  Implement the persistence and domain layers (entities, repositories, use-case classes with
  real business logic, command/result records, domain exceptions for failure paths) for a story
  from its implementation plan at
  ./stories/<ID>/plan.md — following this project's AGENTS.md layering rules. Use this
  whenever the user asks to implement a use case, "build the business logic for" a story, write
  the actual logic behind a plan, implement an entity/repository/use case, or turn a plan into
  real code — phrasing like "implement US-123", "build RegisterUserUseCase", "write the domain
  logic for this plan", or "let's actually build this now". This skill requires a plan to exist
  first (run "user-story-spec-plan" if one doesn't); it only builds persistence and domain-layer
  components from the plan's Component Plan table, never the web/presentation layer (that's
  "user-story-rest-api"'s job, and it depends on the use case this skill produces already
  existing), and it does not write tests — this project's AGENTS.md still requires unit and
  integration tests before a feature counts as done, so always say so plainly when reporting
  results rather than implying the work is complete. Use the IntelliJ IDEA MCP tools
  (list_directory_tree, read_file, search_symbol, search_text, get_symbol_info, create_new_file,
  apply_patch, build_project, get_file_problems) wherever they fit, rather than shell search/edit
  commands, for surveying existing code, writing/modifying classes, and verifying compilation.
---

# User Story Business Logic

The plan already decided *what* to build and in *what order*; this skill is where that becomes
real code. The one thing worth being careful about: a plan is a snapshot of intent, and this
skill's job is to implement the actual behavior correctly — which means the moment a real
decision is still open (a threshold, a rule for an edge case), writing code around a guess would
produce something that merely compiles, not something that's actually right. Stop and ask
rather than build on a guess.

## Step 1 — Load the plan and spec

Each story lives under its own `./stories/<ID>/` directory. If the user names a story ID, use
`./stories/<ID>/` directly; otherwise use `mcp__idea__list_directory_tree` on `./stories` to see
which story directories exist — if there's exactly one, use it, if there's more than one, list
them and ask which story this is for.

Require that directory's `plan.md` to exist — read it with `mcp__idea__read_file`. If it
doesn't exist, stop and tell the user to run `user-story-spec-plan` first; this skill implements
a plan, it doesn't invent one. Also read `spec.md` alongside it — the plan's Component Plan table names
*what* to build, but the spec's acceptance criteria, corner cases, and open questions carry the
actual behavioral detail (what should happen on the edge cases, what the story's Traceability
Matrix flagged as Ambiguous) that the code needs to get right.

## Step 2 — Scope to persistence and domain

From the plan's Component Plan table, take only the rows whose Layer is persistence or
domain (however this project's own architecture doc names those layers — check `AGENTS.md`
fresh rather than assume, since it may have changed). Leave presentation/web-layer rows alone
entirely — those belong to `user-story-rest-api`, and it depends on the classes this skill
produces already existing, not the other way around.

## Step 3 — Check what's already there

Plans get implemented incrementally, sometimes across more than one run of this skill. For each
in-scope component, use `mcp__idea__search_symbol` to check whether it already exists:

- Already exists and matches the plan → skip it, note it as already done in your final report.
- Plan says "Modify" → use `mcp__idea__get_symbol_info` to see its current shape before
  touching it, so the change is additive to what's actually there, not a blind rewrite.
- Plan says "New" but it already exists → treat it like Modify — something built it since the
  plan was written, so verify it matches rather than assuming the plan is stale in your favor.

"Matches the plan" is about the plan's *shape* (what class, what responsibility) — it is not a
free pass on coding conventions. A component that matches the plan but was built before an
`AGENTS.md` convention existed or tightened (e.g. constructor style) still needs refactoring to
comply; skipping it as "already done" would silently leave the codebase non-conformant on a rerun
whose whole purpose is to bring it into compliance.

This is what keeps repeated runs of this skill safe rather than destructive.

## Step 4 — Surface every open decision before writing logic

Before implementing anything, go through the in-scope components against the spec's Open
Questions, any AC marked Ambiguous or Missing in its Traceability Matrix, and the plan's own
Open Risks section. For each one that would actually change how an in-scope component behaves
(a validation rule, a threshold, what happens on a specific edge case), that's a real decision,
not a detail to assume.

If you find any, stop before writing code and ask the user for exactly those decisions — phrase
each as a concrete, answerable question tied to the specific component and behavior it affects
(e.g. "RequestPasswordResetUseCase: how long should a reset token stay valid before it
expires?"), not a vague "please clarify the spec." Once you have real answers, proceed — don't
guess at something a person needs to decide, since the entire point of implementing this
carefully is that the logic is actually correct, not merely plausible.

## Step 5 — Implement, in build-sequence order

Read `AGENTS.md` (or this project's equivalent architecture doc) fresh before writing anything —
don't rely on remembered rules, they may have changed since the plan was written. Follow the
plan's Build Sequence order for the in-scope components (normally persistence before domain,
since domain logic calls into persistence).

For each component:
- **Entities** are anemic — fields and accessors only, per this project's rules. Any actual
  business logic belongs in the use-case class, not the entity.
- **Repositories** are interfaces extending the project's standard repository base, scoped to
  exactly the queries the use case needs — don't add speculative methods nothing calls yet.
- **Use-case classes** hold the real logic: the validation rules, the branching for each corner
  case the spec raised, the actual behavior for each acceptance criterion. This is the part that
  has to be genuinely correct, not just structurally present — reread the specific AC and corner
  case text for each piece of logic you write, don't work from the plan's one-line summary alone.
  When a corner case or AC implies a failure path (not found, a conflict, a broken validation
  rule), throw a specific exception for it — never a bare `RuntimeException`. If the use case
  calls a repository method, annotate the class `@Transactional` per `AGENTS.md`'s rule —
  `readOnly = true` if every call is a read, the default otherwise if anything gets persisted. A
  use case with no repository calls at all needs no `@Transactional`. If this component is a
  facade combining several use cases (per the plan), the `@Transactional` boundary goes on the
  facade instead, and the individual use cases it calls should not also carry their own for that
  combined operation — they join the facade's transaction under Spring's default propagation.
- **Domain exceptions** for each failure path, per `AGENTS.md`'s error-handling rule: extend one
  of `common.exception`'s three categories (`NotFoundException`, `ConflictException`,
  `ValidationException`). Check `common.exception` and the rest of the codebase first via
  `mcp__idea__search_symbol` — reuse an existing concrete exception if one already fits this
  failure, don't create a near-duplicate. Only introduce a genuinely new category if none of the
  three actually describe the failure — that's rare, and worth calling out clearly in your
  report, since `user-story-rest-api`'s shared exception handler would need a new case for it.
- **Command/result records** are the use case's public input/output shape — plain records, no
  Lombok, matching what Step 4's answers (and the spec) actually require.
- **Constructors** for use-case classes, facades, and any other component whose only fields are
  `private final` injected dependencies: never hand-write the constructor — annotate the class
  `@RequiredArgsConstructor` (Lombok) instead, per `AGENTS.md`'s "Lombok is allowed for
  constructors" rule. Hand-write a constructor only when it does something a generated
  all-args constructor cannot (validation, a derived field, a non-dependency parameter).

Write new files with `mcp__idea__create_new_file`; modify existing ones with
`mcp__idea__apply_patch` so the change is a clean diff against what Step 3 found, not a full
rewrite of a file you don't own outright.

## Step 6 — Verify it compiles

Run `mcp__idea__build_project` (or `mcp__idea__get_file_problems` on each new/changed file) and
fix anything that doesn't compile before calling a component done.

## Step 7 — Report back

For each in-scope component: built, modified, or already-present-and-skipped. Then two things
that must not get lost in the summary:

- **No tests were written.** This project's `AGENTS.md` treats a feature as done only when its
  tests exist and pass too — say plainly that this step still remains, don't let "the code
  compiles" read as "this is finished."
- Any decisions Step 4 surfaced and how they were answered, so there's a record of what was
  assumed vs. explicitly decided.
- Every new domain exception introduced and which `common.exception` category it extends — and
  flag clearly, separately, if any of them needed a genuinely new category, since that's the one
  case `user-story-rest-api`'s shared exception handler won't already cover.

If everything built cleanly, close by noting two independent next steps that both depend only
on what this skill just produced, not on each other: `user-story-unit-tests` to cover the logic,
and `user-story-rest-api` to expose it over HTTP.
