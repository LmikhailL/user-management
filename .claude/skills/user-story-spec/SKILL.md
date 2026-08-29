---
name: user-story-spec
description: >
  Turn a raw user story file in ./stories into a detailed, self-contained specification
  document that surfaces missing questions, uncovered corner cases, and an acceptance-criteria
  traceability matrix. Use this skill whenever the user asks to review, analyze, spec out,
  flesh out, or "check" a user story, US, or backlog item for completeness or readiness —
  including phrasing like "is this story ready for dev", "what are we missing here", "find
  the gaps/edge cases in this story", "make a traceability matrix for US-123", or "generate a
  spec for this story" — even if they don't explicitly say "user-story-spec". Always look in
  the ./stories directory (relative to the project root) for the input file(s) unless the user
  gives an explicit path. When the project is open in IntelliJ IDEA, use its MCP tools
  (list_directory_tree, read_file, search_file, create_new_file) to find, read, and write files
  rather than shell search commands. Do not use this for writing brand-new user stories from
  scratch, or for general code review — it is specifically for auditing and enriching existing
  story text.
---

# User Story Spec

Turns a terse user story into a specification a developer can actually start from: every
acceptance criterion accounted for, the corner cases nobody wrote down yet, and the questions
that need an answer before the story is safe to estimate.

The point isn't to rewrite the story — it's to pressure-test it. A story that reads fine in
isolation ("As a user, I want to reset my password...") almost always hides unstated decisions:
what happens on the third failed attempt, does the reset link expire, can two reset requests
race each other. This skill's job is to drag those decisions into the open before they turn
into a mid-sprint surprise, and to leave a paper trail (the traceability matrix) showing exactly
which requirement each part of the analysis maps back to.

## Step 1 — Locate the story

Input stories live in `./stories` (relative to the project root), one story per file
(`.md` or `.txt`).

When this project is open in IntelliJ IDEA, use its MCP tools for finding and reading files
instead of shell search commands — `mcp__idea__list_directory_tree` to see what's in
`./stories`, `mcp__idea__search_file` if you need to glob-match a name or ID, and
`mcp__idea__read_file` to read the chosen story. They give exact, structured results without
the ambiguity of text-matching a directory listing, and the IDE already has the project indexed.
Fall back to your regular file tools only if the IDEA tools aren't available.

- If the user names a file, an ID, or pastes enough of the title to match uniquely, use that
  file directly.
- If the user says "all stories" or gives no story-specific detail while multiple files exist
  in `./stories`, list what's there and ask which one(s) to process rather than guessing —
  running the full analysis on the wrong story wastes the user's time reviewing it.
- If `./stories` has exactly one file and the user's request is clearly about "the story" /
  "this story", just use it.

**Story ID**: derive it from the filename, stripped of extension (`US-123.md` → `US-123`,
`password-reset.md` → `password-reset`). This ID drives the output filename and every row ID
in the traceability matrix, so keep it exactly as it appears in the filename — don't
reformat it.

## Step 2 — Read and normalize the story

Stories are expected in the standard template:

```
As a <actor>
I want <capability>
So that <benefit>

Acceptance Criteria:
- ...
- ...
```

Real files won't all be this clean. Handle it gracefully:

- If an Acceptance Criteria section exists, pull it out and number each item `AC-1`, `AC-2`, …
  in the order it appears — this numbering is what the traceability matrix keys off, so keep it
  stable and don't silently drop or merge criteria the author separated.
- If there's no AC section at all, or the story is just a paragraph of prose, extract the
  implicit acceptance criteria yourself from whatever concrete, testable statements are in the
  text, number them the same way, and say explicitly in the spec that these were inferred, not
  authored — that distinction matters to whoever reads it next.
- If the story doesn't follow the As a/I want/So that shape at all (e.g. it's a bug report or a
  loose feature request), don't force it into that template. Restate it faithfully in whatever
  shape it naturally has, and note the missing role/goal/benefit framing as an open question if
  it genuinely obscures who the story is for.

## Step 3 — Assess each acceptance criterion

For every AC, decide one of three states:

- **Covered** — the criterion is specific and testable as written. A developer could turn it
  into a test case without guessing.
- **Ambiguous** — the criterion exists but leaves a material decision unmade (a threshold, a
  format, a behavior on conflict). State exactly what's ambiguous, not just that it is.
- **Missing** — implied by the story's goal but never written down at all (e.g. the story
  promises "notify the user" but never says by what channel, or promises validation but never
  states the rule).

Judge against what a developer would actually need to build and test the feature — not
against an abstract completeness checklist. A criterion that's short but genuinely
unambiguous ("the endpoint returns 404 if the user does not exist") is Covered; don't flag it
just for being terse.

## Step 4 — Surface corner cases

Read the story's intent, then check it against the corner-case categories below. Only include
categories that are plausibly relevant to what this story actually does — a read-only listing
endpoint doesn't need a concurrency write-conflict analysis, and forcing every category onto
every story is how these documents become noise nobody reads.

- **Boundary & invalid input** — empty/null/blank values, max-length, wrong type, malformed
  format (email, dates, IDs).
- **Empty & missing state** — zero results, resource not found, first-time-use with no prior
  data.
- **Concurrency & ordering** — two actions racing (double-submit, two devices, retried
  requests), out-of-order events.
- **Authorization & identity** — who besides the intended actor could hit this path, and what
  should happen to them (another user's data, an unauthenticated caller, a revoked session).
- **Data exposure & privacy** — anywhere the story moves data outside its normal boundary
  (an export, an API response, a log line, an error message, a notification to a third party),
  check what fields actually travel with it — a story that says "export the user list" or
  "send a notification" easily carries along a password hash, an internal ID, or another
  user's personal data without anyone deciding that should happen.
- **Compliance & retention** — if the story touches personal data or an action a user might
  later need undone (consent, deletion, data export/portability, an audit trail, how long the
  data is kept), flag it — these carry legal weight (e.g. GDPR/CCPA-style rights) that a missed
  requirement can't just be patched in later without real cost.
- **Failure & partial failure** — a downstream dependency (DB, external service, email/SMS
  provider) times out or errors mid-operation; is the operation atomic or can it half-complete.
- **Repeat & idempotency** — the same action performed twice (intentionally or via retry) —
  does it double-charge, double-send, or error cleanly the second time.
- **State transitions** — for anything with a lifecycle (pending/active/expired,
  draft/published), what's a valid vs. invalid transition, and what happens on an invalid one.

For each corner case you raise, say briefly why it matters for *this* story, not just its
category label — "concurrency: two reset requests" is less useful than "concurrency: if the
user requests two reset links back to back, does the first one get invalidated, or do both
work."

## Step 5 — Write the open questions

Pull together the ambiguities and missing pieces from Steps 3–4 into a single numbered list
addressed to whoever owns the story (PO/stakeholder). Each question should be answerable in a
sentence and should make clear what happens differently depending on the answer — a question
nobody can act on isn't worth asking. Skip anything you've already resolved reasonably as
Covered; this list is only for genuine open decisions.

## Step 6 — Form the readiness verdict

Pick one, based on what Steps 3–5 turned up:

- **Ready** — no Missing criteria, no open questions that block implementation. Ambiguous items
  are cosmetic or safely assumable.
- **Needs Clarification** — some Missing/Ambiguous items exist, but the core of the story is
  buildable; list the specific blockers.
- **Blocked** — the story can't be reasonably estimated or started as written (e.g. the actor,
  goal, or a core mechanic is entirely unclear).

Give one or two sentences of justification, naming the specific AC or question driving the
verdict — a bare label with no reasoning forces the reader to redo the analysis themselves.

## Step 7 — Write the spec file

Output path: `./stories/<ID>/spec.md`, creating the `./stories/<ID>/` directory if it doesn't
exist — this keeps every artifact for one story (spec, validation report, plan) together, and
lets multiple stories coexist under `./stories` without colliding. If a spec already exists for
this ID, overwrite it — the spec should always reflect the current story text, not accumulate
stale history. When IntelliJ IDEA's MCP tools are available,
write the file with `mcp__idea__create_new_file` (`overwrite: true`) rather than a plain file
write — it creates any missing parent directories automatically and keeps the write visible to
the IDE's project index immediately. Fall back to your regular file tools if it isn't available.

Use this exact structure:

```markdown
# Spec: <ID> — <short title>

**Source:** `stories/<original filename>`
**Generated:** <date>
**Readiness:** <Ready | Needs Clarification | Blocked>

<1-2 sentence justification for the readiness verdict, naming the specific blockers if any>

## Story

As a <actor>
I want <capability>
So that <benefit>

## Acceptance Criteria

1. **AC-1** — <criterion text, as written or as inferred (state which)>
2. **AC-2** — ...

## Traceability Matrix

| AC ID | Requirement | Status | Notes |
|-------|-------------|--------|-------|
| AC-1  | <short paraphrase> | Covered / Ambiguous / Missing | <reasoning, and for Missing/Ambiguous, what's undecided> |

## Corner Cases

- **<category>** — <specific corner case for this story, and why it matters>

## Open Questions

1. <question, phrased so the answer changes behavior>

```

Keep the matrix's "Requirement" column short (a few words) — the full text is already in the
Acceptance Criteria section above it; the matrix's job is to be scannable at a glance, not to
repeat the story.

After writing the file, tell the user the verdict and a one-line summary of what needs
attention — don't make them open the file just to learn the story is Blocked. Then point them
at `user-story-spec-validate` as the next step — that's true regardless of the readiness
verdict, since validation checks the spec's internal consistency, not whether the story is
ready to build.
