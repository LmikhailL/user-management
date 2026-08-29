---
name: architecture-tests
description: >
  Create or update the project's ArchUnit test suite so this project's layered architecture
  (web → domain → persistence, one-way dependencies) and its other structural rules from
  AGENTS.md are enforced by the build, not just by convention — while staying flexible enough
  to allow a shared commons/helper package that any layer may depend on. Use this whenever the
  user asks to add or update architecture tests, enforce layering, check for layering
  violations, set up ArchUnit, or verify that a layer isn't reaching into another it shouldn't
  (e.g. "are we calling persistence from web anywhere", "add ArchUnit rules", "make sure nobody
  bypasses the domain layer", "did this change break the architecture"). This isn't tied to a
  single story — it operates on the whole codebase's actual package structure, which it should
  discover fresh each time rather than assume, since new feature packages get added over time.
  Use the IntelliJ IDEA MCP tools (list_directory_tree, read_file, search_symbol, get_symbol_info,
  create_new_file, apply_patch, execute_terminal_command, build_project, get_file_problems)
  wherever they fit, rather than shell search/edit/run commands, for discovering the real
  package layout, checking the ArchUnit library's actual API before writing rules against it,
  editing pom.xml, and running the suite to confirm it passes for the right reasons.
---

# Architecture Tests

The value of an architecture test is that it fails the moment someone (human or another skill)
writes code that violates a rule everyone agreed to — catching it in the build instead of in
review, or not at all. That only works if the rules are actually encoded and actually
maintained as the codebase's real structure evolves, so this skill reads the current rules and
the current package layout fresh every time rather than trusting what was true last time it ran.

## Step 1 — Read the current rules

Read this project's `AGENTS.md` (or equivalent architecture doc) via `mcp__idea__read_file` —
don't work from memory of what it said before, it may have changed. At minimum, expect
something like: a layered dependency direction (web → domain → persistence, one-way), entities
with no business logic, a naming convention for use-case classes, one `@RestController` per use
case, and mappers as MapStruct interfaces — but encode whatever the document actually currently
says, including anything added since this skill last ran.

## Step 2 — Discover the real package structure

Don't assume a package layout — find it:

- Find the base package by locating the `@SpringBootApplication` class with
  `mcp__idea__search_symbol`.
- Use `mcp__idea__list_directory_tree` under `src/main/java` to see how packages are actually
  organized. This project organizes by feature first (e.g. a story's code lives under
  `<feature>.web`, `<feature>.domain`, `<feature>.persistence`), not one flat `web`/`domain`/
  `persistence` package for the whole app — confirm that's still true rather than assuming it,
  since layering rules need to match on the layer name appearing *anywhere* in the package path
  (e.g. an ArchUnit pattern like `..web..`) to work uniformly across every feature package
  without per-feature configuration.
- Look for any existing shared/helper package (commonly named `common`, `commons`, `shared`,
  `util`, or `support`) that multiple features already depend on. That's the candidate for the
  "usable by any layer" exception the layering rule needs to allow — if none exists yet, design
  the rule to permit one under a sensible name anyway, so adding one later doesn't immediately
  break the architecture test.

## Step 3 — Make sure ArchUnit is actually a dependency

`AGENTS.md` already lists ArchUnit as part of this project's approved stack — unlike a genuinely
new dependency, adding it to `pom.xml` doesn't need a stack-change sign-off, since it's already
agreed to. Check via `mcp__idea__read_file` whether `archunit-junit5` (or equivalent) is
actually present; if it's only mentioned in the doc but never added, add it now with
`mcp__idea__apply_patch`.

## Step 4 — Find or create the test class

Look for an existing ArchUnit test class first (`mcp__idea__search_symbol` for usages of
`@AnalyzeClasses` or `ArchRule`). If one exists, read it and reconcile — add whatever `AGENTS.md`
now requires that isn't encoded yet, without discarding rules someone already tuned (e.g. a
deliberately widened package pattern). If none exists, create one fresh using ArchUnit's JUnit 5
support (`@AnalyzeClasses(packagesOf = ...)` pointing at the discovered application class, with
each rule as its own named `@ArchTest` field) — one rule per concern, so a failure names exactly
which rule broke instead of a single opaque "architecture test failed."

Before writing any ArchUnit DSL, check its actual current API on the classpath
(`mcp__idea__search_symbol` with `include_external: true` against `com.tngtech.archunit`
classes, or read its docs) rather than writing from memory — the layered-architecture and rule
DSLs have changed across versions, and a rule that doesn't compile, or worse, compiles but
doesn't check what you think it does, is worse than no rule at all.

## Step 5 — Encode the layering rule, with the commons exception

The core rule: Web depends on Domain, Domain depends on Persistence, and never the reverse —
using the package-layer names found in Step 2 (typically matched as `..web..`, `..domain..`,
`..persistence..` so it applies across every feature package at once). On top of that:

- A commons/shared package (Step 2) may be depended on **by** any layer.
- Commons must **not** depend on Web, Domain, or Persistence itself — a "shared" package that
  reaches back into a specific layer isn't actually shared, it's a hidden coupling wearing a
  shared package's name. Encode this as an explicit rule, not just an omission, so it's
  impossible to add that coupling later without the test catching it.

This is what makes the check flexible without making it toothless: layers stay one-way, and a
genuinely shared package stays genuinely shared.

## Step 6 — Encode the rest of AGENTS.md's structural rules

Translate each remaining rule from Step 1 into its own `@ArchTest`, expressed as precisely as
ArchUnit's API actually allows — for example (adjust to what Step 1 found and what Step 4's API
check confirmed is expressible):

- `@Entity` classes contain no business logic — e.g. no methods beyond accessors/constructors.
- No class name ends in `Service`; use-case classes end in `UseCase`.
- Each `@RestController` exposes exactly one use case (e.g. exactly one use-case dependency).
- Classes named `*Mapper` are MapStruct `@Mapper` interfaces, not hand-written classes.

If something in the doc genuinely can't be expressed as a reliable static check, don't force a
weak rule that gives false confidence — tell the user it needs manual review instead of
pretending it's automated.

## Step 7 — Run it, and treat what it finds as real

Run the suite via `mcp__idea__execute_terminal_command` (`./mvnw test` — these are fast,
no Spring context or database, so they belong in the regular unit-test run, not a separate
phase). If it fails against the current codebase, that's a real violation being caught, not a
bug in the test — report exactly which rule, which class, and what it depends on that it
shouldn't. Let the user decide whether to fix the violating code or deliberately widen a rule
(e.g. adding a new package to the commons exception) — don't quietly loosen a rule just to reach
green, since that defeats the entire point of having it.

## Step 8 — Report back

List the rules now encoded, the package convention they assume (so it's visible if this
project's structure changes later and the rule needs revisiting), any dependency added, and the
suite's pass/fail status with the specifics of any violation found.
