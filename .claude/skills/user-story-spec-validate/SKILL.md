---
name: user-story-spec-validate
description: >
  Validate a story spec file in ./stories/<ID>/spec.md — the document produced by the
  "user-story-spec" skill — for internal consistency and structural completeness, and write a
  validation report. Use this whenever the user asks to validate, check, verify, lint, or
  sanity-check a story spec, traceability matrix, or "the spec we just generated" — including
  phrasing like "does this spec check out", "validate the traceability matrix", "is this spec
  consistent", or "run validation on the spec". Each story has its own directory under
  ./stories/<ID>/ — locate the right one and read its spec.md with the IntelliJ IDEA MCP tools
  (list_directory_tree, read_file, search_file), not shell search commands, since this project
  is already open in the IDE and those tools give exact, reliable results. Do not use this skill
  to generate a new spec
  from a raw user story (that's the "user-story-spec" skill) or to check the implementation
  code against the spec — this skill only checks the spec document itself.
---

# User Story Spec Validate

The `user-story-spec` skill produces a document that's only useful if it's actually
self-consistent — a traceability matrix that references an AC nobody wrote, or a "Ready"
verdict sitting on top of a Missing criterion, defeats the entire point of writing the spec in
the first place: catching problems before they reach a developer. This skill is the second
pass — it doesn't re-derive the analysis, it checks that the analysis holds together.

Scope is deliberately narrow: this validates the spec **document itself** — its structure and
internal cross-references — not whether the code implementing the story exists or matches. That
keeps the check fast and mechanical rather than another round of judgment calls.

## Step 1 — Locate and read the spec

Each story lives under its own `./stories/<ID>/` directory. Use the IntelliJ IDEA MCP tools to
find and read it rather than shell commands — the project is already open in the IDE, and these
tools give exact results without the ambiguity of grep-style text matching:

- If the user names a story ID, use `./stories/<ID>/spec.md` directly.
- Otherwise, use `mcp__idea__list_directory_tree` on `./stories` to see which story directories
  exist. If there's exactly one, use it. If there's more than one, list them and ask which
  story this is for rather than guessing — validating the wrong one wastes the user's time.
- `mcp__idea__read_file` to read the chosen `spec.md`.

If the resolved directory has no `spec.md`, stop and tell the user what you found instead of
guessing which file to validate.

## Step 2 — Run the checks

Each check below has a severity. A **Fail** means the document contradicts itself or is missing
something the template requires — the spec cannot be trusted as-is. A **Warning** means
something looks off but could be a legitimate judgment call by whoever (or whatever) generated
the spec — worth a second look, not necessarily wrong.

**Structural completeness (Fail if missing):**
1. The header carries `Source`, `Generated`, and `Readiness` fields.
2. All five sections are present: Story, Acceptance Criteria, Traceability Matrix, Corner
   Cases, Open Questions.
3. The Story section actually contains story content (not an empty stub under the heading).

**Acceptance criteria numbering (Fail if violated):**
4. AC IDs are sequential starting at `AC-1` with no gaps or duplicates (`AC-1, AC-2, AC-3`, not
   `AC-1, AC-3` or `AC-1, AC-2, AC-2`).

**Traceability matrix cross-reference (Fail if violated):**
5. Every AC ID from the Acceptance Criteria section has exactly one row in the Traceability
   Matrix, and the matrix has no row for an AC ID that doesn't exist in that list — the matrix
   and the AC list must describe the same set.
6. Every matrix row's Status is one of `Covered`, `Ambiguous`, or `Missing` — not a free-text
   substitute or a blank cell.

**Readiness verdict consistency (Fail if violated):**
7. The Readiness value is exactly one of `Ready`, `Needs Clarification`, or `Blocked`.
8. If any AC's Status is `Missing`, the verdict cannot be `Ready` — a criterion with nothing
   decided can't simultaneously be called ready to build. This is the most important check:
   it's the exact contradiction that makes a spec actively misleading rather than just
   incomplete.

**Softer signals (Warning, not Fail):**
9. Readiness is `Ready` but the Open Questions section is non-empty — the spec's own Step 5
   logic says that section should only hold genuine open decisions, so a "Ready" verdict next to
   real open questions is worth a second look even though an Ambiguous-but-cosmetic AC can
   legitimately coexist with Ready.
10. The Corner Cases section is empty — rare but not impossible for a very narrow story; flag
    it so a human confirms that's deliberate rather than an oversight.
11. The `Source` field names a story file that doesn't exist under `./stories` (check with
    `mcp__idea__search_file`) — the spec may have been generated against a file that's since
    been renamed or moved.

Don't invent additional checks beyond this list without telling the user — the point of this
skill is a predictable, mechanical pass, not a fresh round of critique (that's what
`user-story-spec` already did).

## Step 3 — Write the validation report

Output path: `./stories/<ID>/validation.md`, next to the `spec.md` it validated. Overwrite if
one already exists — like the spec itself, this should always reflect the current document, not
accumulate history. Create the file with `mcp__idea__create_new_file` (`overwrite: true`).

Use this structure:

```markdown
# Validation: <ID>

**Spec:** `stories/<ID>/spec.md`
**Validated:** <date>
**Result:** <PASS | PASS WITH WARNINGS | FAIL>

## Checks

| # | Check | Result | Details |
|---|-------|--------|---------|
| 1 | <check name> | Pass / Fail / Warning | <what was found, quoting the specific AC ID or section when relevant> |

## Issues

<Only Fail-level items, each with enough detail to fix it — which AC, which section, what's
wrong. Omit this section if there are none.>

## Warnings

<Only Warning-level items. Omit this section if there are none.>
```

`Result` is `FAIL` if any check failed, `PASS WITH WARNINGS` if none failed but at least one
warning fired, and `PASS` otherwise.

List every check from Step 2 in the table, in order, even the ones that passed — a validation
report that only lists problems can't be trusted as "I checked and it's silent because there's
nothing to say" versus "I only ever list failures." Showing the full checklist is what makes a
clean PASS credible.

After writing the file, tell the user the overall result and, if not a clean PASS, a one-line
summary of what needs fixing — don't make them open the report to learn the spec failed. Only
suggest running `user-story-spec-plan` next if the Result is `PASS` or `PASS WITH WARNINGS` —
on `FAIL`, the next step is fixing the spec (or the story it was generated from) and rerunning
this skill, not moving forward with a plan built on a contradiction.
