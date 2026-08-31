---
name: user-story-spec-plan
description: >
  Turn a validated story spec in ./stories/<ID>/spec.md into a concrete implementation plan —
  which classes to add or change in which layer, in what order, and which tests each acceptance
  criterion requires. Use this whenever the user asks to plan, break down, scope, or "figure out
  how to build" a story that already has a spec — phrasing like "plan the implementation for
  US-123", "how should we build this", "break this spec down into tasks", "what needs to
  change for this story", or "give me a build plan". This skill only runs against a spec that
  has already passed validation (./stories/<ID>/validation.md) — if there's no validation
  report, or it says FAIL, point the user at the "user-story-spec-validate" skill first instead
  of guessing at a plan for a spec that might be self-contradictory. Use the IntelliJ IDEA MCP
  tools (list_directory_tree, search_symbol, search_text, get_symbol_info, read_file,
  create_new_file) wherever they fit — surveying the existing codebase before proposing new
  classes, and reading/writing the spec, validation, and plan files — rather than shell search
  commands. Do not use this for generating the spec itself (that's "user-story-spec") or for
  validating spec consistency (that's "user-story-spec-validate") — this skill only plans the
  build once a spec already exists and checks out. This skill never starts implementation
  itself and never tells another skill to: after writing plan.md it stops and waits for the
  user's explicit go-ahead in this same conversation before any implementation skill
  (user-story-business-logic, user-story-liquibase, user-story-rest-api, the test-writing
  skills) runs against this plan — a plan is a proposal for the user to approve, edit, or
  reject, not an automatic trigger to start building.
---

# User Story Spec Plan

A validated spec tells you *what* needs to be true when the story is done. This skill turns
that into *how to get there* in this specific codebase: which classes already exist and can be
reused, which are net new, what order to build them in given this project's layering rules, and
which tests each acceptance criterion is on the hook for. The output is something a developer
can start executing from, not another round of analysis — Steps 1–3 gather facts, Step 4 turns
those facts into a plan.

## Step 1 — Locate spec and validation report, check the gate

Each story lives under its own `./stories/<ID>/` directory. Use IntelliJ IDEA's MCP tools rather
than shell commands to find and read them:

- If the user names a story ID, use `./stories/<ID>/` directly.
- Otherwise, use `mcp__idea__list_directory_tree` on `./stories` to see which story directories
  exist. If there's exactly one, use it. If there's more than one, list them and ask which
  story this plan is for rather than guessing.
- `mcp__idea__read_file` to read that directory's `spec.md` and `validation.md`.

The validation report is a gate, not a formality:

- If `validation.md` doesn't exist, stop and tell the user to run the
  `user-story-spec-validate` skill first — planning against an unvalidated spec means you might
  be building a detailed plan around a contradiction nobody's caught yet.
- If its Result is `FAIL`, stop for the same reason and name the specific failing check(s) so
  the user knows what to fix.
- If its Result is `PASS WITH WARNINGS`, proceed, but carry the warnings forward — note them in
  the plan's header so whoever executes it knows there's an unresolved loose end (e.g. a
  `Source` file that couldn't be found), even though it didn't block planning.
- If `PASS`, proceed cleanly.

## Step 2 — Survey what already exists

Before naming a single new class, find out what's actually in the codebase — this project may
already have a related entity, a similar use case, or an endpoint that overlaps with this
story, and proposing a duplicate wastes the time this plan is supposed to save.

- Read this project's architecture rules fresh — usually `AGENTS.md` or `CLAUDE.md` at the
  project root — via `mcp__idea__read_file`. Don't rely on memory of what those rules said last
  time; they can change, and the plan's whole value is being concretely correct for *this*
  codebase right now.
- Use `mcp__idea__search_symbol` to look for existing classes matching the story's domain
  concepts (the actor, the entity being acted on, similar verbs — e.g. for a password-reset
  story, search for `User`, `Reset`, `Password`, `Token`). Use `mcp__idea__search_text` for
  concepts that won't resolve as a symbol name (a route path, a config key, a specific string).
- For anything promising you find, use `mcp__idea__get_symbol_info` to see its actual shape
  before assuming what it does — a class named `User` might be a full entity or a thin DTO, and
  the plan needs to know which.
- If you're about to plan a change to an existing class, `mcp__idea__analyze_calls`
  (`INCOMING_CALLS`) on its key methods tells you who else depends on it — that's the difference
  between "modify this method" and "modify this method, which also means updating its 3 other
  callers."

Record, per acceptance criterion, whether the pieces it needs already exist, partially exist, or
are net new. This is what makes the plan grounded instead of speculative.

## Step 3 — Map each AC to concrete components

For each AC in the spec, name the specific classes it needs, in this project's actual layers.
Follow whatever layering/naming rules Step 2 found in the project's architecture doc — don't
invent your own structure. If the project has no such document, use conventional
controller/service/repository layering and say so explicitly in the plan rather than silently
picking a structure the user never agreed to.

For each component, state:
- **Layer** (e.g. presentation / domain / persistence, or whatever this project's own layering
  calls them).
- **Class name**, following the project's naming rules exactly (if the project bans generic
  names like `UserService` in favor of use-case names like `RegisterUserUseCase`, follow that
  here too).
- **New or Modify** — and if Modify, what specifically changes.
- Which AC(s) it serves — a shared component (e.g. one entity backing several ACs) should say so
  once, not be repeated as if it were separate work.

## Step 4 — Order the build

Turn the component list into a sequence a developer would actually follow — normally
bottom-up through the dependency chain the architecture doc describes (the layer with no
dependencies first, the layer that depends on everything else last), with each component's
tests immediately after it rather than batched at the end. Explain briefly *why* this order
(e.g. "the use case can't be tested without the repository it calls existing first") — a bare
numbered list without the reasoning is easy to reorder wrong later.

## Step 5 — Test plan per AC

For each AC, list the specific tests it needs, following this project's own testing rules if it
has them (check the same architecture doc from Step 2 — e.g. this project's `AGENTS.md` treats
a feature as done only if code *and* its tests exist and pass, with unit tests covering all
cases and integration tests covering the happy path). Name the test class and what it verifies,
not just "add tests" — a vague test obligation is the first thing that gets skipped under time
pressure.

If the project enforces its layering with automated tests (e.g. ArchUnit), note whether the new
classes are already covered by existing rule tests or whether a new rule is needed — most of the
time existing rules cover new classes automatically since they're written generically, and it's
worth saying so explicitly rather than leaving it as an unstated assumption.

## Step 6 — Write the plan file

Output path: `./stories/<ID>/plan.md`, next to the spec and validation report. Overwrite
if one already exists. Write it with `mcp__idea__create_new_file` (`overwrite: true`) rather
than a plain file write, consistent with how the spec and validation files are produced.

Use this structure:

```markdown
# Implementation Plan: <ID> — <short title>

**Spec:** `stories/<ID>/spec.md`
**Validation:** `stories/<ID>/validation.md` — <Result>
**Generated:** <date>

<If validation was PASS WITH WARNINGS, name the carried-forward warning(s) here in one line.>

## Existing Code Impact

<What already exists in the codebase relevant to this story — reused as-is, needs modification,
or confirmed net new — per the Step 2 survey. This is what justifies every "New" vs "Modify" tag
in the table below.>

## Component Plan

| AC ID | Layer | Component | New / Modify | Notes |
|-------|-------|-----------|---------------|-------|
| AC-1  | <layer> | `<ClassName>` | New | <what it does, and why this name/shape> |

## Build Sequence

1. <component> — <why it comes first>
2. ...

## Test Plan

| AC ID | Test Type | Test Class | Verifies |
|-------|-----------|------------|----------|
| AC-1  | Unit | `<ClassNameTest>` | <what behavior/case> |

## Open Risks

<Anything Step 2 or 3 surfaced that could derail the plan — a missing prerequisite (e.g. no
auth mechanism exists yet and an AC needs one), a naming collision, an architecture-doc rule
that's ambiguous for this case. Omit this section if there's genuinely nothing.>
```

After writing the file, tell the user the build sequence in one or two sentences and name the
single biggest risk or blocker if Step 3/5 found one — don't make them open the plan to learn
the story can't actually start until some prerequisite lands.

## Step 7 — Stop and wait for approval

Do not invoke `user-story-business-logic`, or any other implementation or test-writing skill,
in this same turn — not even to "get started while they review." Ask plainly whether the plan
looks right to build from, and then stop.

- If the user asks for changes (a different component split, a reordered build sequence, a
  component they think is missing or unnecessary), revise `plan.md` and present the updated
  build sequence again for approval — don't start implementing against a version they haven't
  signed off on.
- If the user says no / not yet, stop here. Nothing beyond `plan.md` has been touched.
- Only an explicit affirmative in this conversation (e.g. "yes", "proceed", "looks good", "go
  ahead", "build it") counts as approval to move on to `user-story-business-logic` — the first
  implementation skill in the chain, since both `user-story-rest-api` and the test-writing
  skills depend on the components it produces existing first. Silence, a topic change, or the
  user asking an unrelated question is not approval.
