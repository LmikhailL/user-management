---
name: user-story-unit-tests
description: >
  Write JUnit 5 unit tests, structured Given/When/Then, for the domain logic a story's
  implementation plan named — covering the happy path, edge cases, and failures from the
  story's acceptance criteria and corner cases, then run them and iterate until they pass. Use
  this whenever the user asks to write unit tests, test a use case, add Gherkin-style tests,
  cover the edge cases with tests, or close out the testing gap for a story that already has
  business logic implemented — phrasing like "write tests for US-123", "unit test
  RegisterUserUseCase", "add Given/When/Then tests for this", or "test the corner cases from the
  spec". This skill only writes unit tests for domain-layer logic (use cases and any other class
  with real business behavior) — it does not write integration tests (TestContainers, full
  request-to-DB flows), and it requires the logic under test to already exist; if
  ./stories/<ID>/plan.md's domain-layer components aren't built yet, point the user at
  "user-story-business-logic" first instead of writing tests against code that doesn't exist.
  Use the IntelliJ IDEA MCP tools (list_directory_tree, read_file, search_symbol, get_symbol_info,
  create_new_file, apply_patch, execute_terminal_command, build_project, get_file_problems,
  get_run_configurations, execute_run_configuration) wherever they fit, rather than shell
  search/edit/run commands, for surveying the code under test, writing test files, and running
  them to confirm they actually pass.
---

# User Story Unit Tests

A test that was never run is just a claim. This skill's job isn't finished when the test code
compiles — it's finished when the tests actually execute and pass, and when a failure is
correctly diagnosed as either a bad test (fix it) or a real gap between the implementation and
the spec (stop and say so, don't paper over it).

## Step 1 — Gather context and confirm there's something to test

Each story lives under its own `./stories/<ID>/` directory. If the user names a story ID, use
`./stories/<ID>/` directly; otherwise use `mcp__idea__list_directory_tree` on `./stories` to see
which story directories exist — if there's exactly one, use it, if there's more than one, list
them and ask which story this is for. Read that directory's `plan.md` and `spec.md` with
`mcp__idea__read_file`. The plan's Test Plan table already names which test classes cover which
acceptance criteria — treat that as a starting checklist, not the final word, since the spec's
actual AC and Corner Cases text carries the behavioral detail the test bodies need.

For each domain-layer class the plan names, confirm it actually exists with
`mcp__idea__search_symbol`. If the plan's domain-layer components aren't built yet, stop and
point the user at `user-story-business-logic` — there's nothing to test against. Use
`mcp__idea__get_symbol_info` on each class under test to get its real method signatures, exact
field names, and constructor/collaborator dependencies — write tests against what's actually
there, not what the plan assumed it would look like.

Only test domain-layer logic: use-case classes and anything else that holds real behavior. Skip
anemic entities (nothing to unit test — no logic) and repository interfaces (their behavior is
Spring Data / JPA's, covered by integration tests, not unit tests here).

## Step 2 — Check what test-support libraries are actually available

`AGENTS.md` names both Mockito (mocking) and AssertJ (assertions) as approved — but Spring
Boot's test starters don't always pull them onto the classpath as an explicit dependency, so
verify before assuming, the same way any other skill in this family checks a library actually
resolves rather than trusting the doc alone. Search for `org.mockito.Mockito` and
`org.assertj.core.api.Assertions` via `mcp__idea__search_symbol` with `include_external: true`.

- If it resolves, use it.
- If it doesn't, add it to `pom.xml` directly (`mcp__idea__apply_patch`) — this isn't a new
  stack decision needing sign-off, just wiring in something already approved that isn't on the
  classpath yet.

## Step 3 — Derive test scenarios from the spec, not just the plan

For each class under test, build the scenario list from:
- **Every acceptance criterion** the class is responsible for — at minimum, its happy path.
- **Every corner case** from the spec that applies to this class's behavior — each one is a
  scenario, not a comment. If the spec raised "what happens on a second reset request before the
  first is used," that's a test, not a note.
- Anything the spec's Traceability Matrix marked Ambiguous or Missing that's since been resolved
  (by the business-logic implementation, or by an answer the user gave when that gap was
  surfaced) — the resolution needs a test asserting the behavior that was actually decided.

Don't invent scenarios beyond what the spec and the implementation's actual branching justify —
a test for a case the code doesn't even distinguish is testing nothing.

## Step 4 — Write the tests

One test class per class under test, at `src/test/java/<mirrored package>/<ClassName>Test.java`,
matching the plan's Test Plan naming where it named one.

Each scenario is its own `@Test` method, structured in three clearly marked sections:

```java
@Test
@DisplayName("given <the specific setup>, when <the action>, then <the observable outcome>")
void descriptiveMethodName() {
    // Given
    ...

    // When
    ...

    // Then
    ...
}
```

The `@DisplayName` is the Gherkin-style scenario statement — write it as a full sentence
describing the behavior, not a restatement of the method name. When a class has enough
scenarios that they fall into natural groups (e.g. all the scenarios for one acceptance
criterion, or all the failure-path scenarios), group them with `@Nested` classes named for the
group rather than leaving a long flat list — but don't force nesting on a class with only two or
three scenarios.

Mock collaborators (repositories, other use cases, anything crossing a layer boundary) rather
than reaching for real infrastructure — that's what makes these unit tests instead of
integration tests. Assert on behavior and outcomes, not on implementation details that would
make the test brittle to a harmless refactor.

Write files with `mcp__idea__create_new_file` for new test classes, `mcp__idea__apply_patch` to
add scenarios to an existing test class rather than overwriting it wholesale.

## Step 5 — Run them, and actually resolve failures

Run the new/changed tests — `mcp__idea__execute_terminal_command` (e.g.
`./mvnw test -Dtest=<ClassName>Test`) or, if this project has a suitable run configuration,
`mcp__idea__get_run_configurations` / `mcp__idea__execute_run_configuration`. A test file that
was written but never executed hasn't actually verified anything.

When a test fails, diagnose which side is wrong before touching anything:

- **The test's expectation is wrong** (misread the spec, wrong mock setup, asserted the wrong
  value) — fix the test, rerun, confirm green.
- **The implementation doesn't actually do what the spec requires** — this is a real bug the
  test correctly caught. Do not quietly change the test to match broken behavior, and do not
  silently patch the implementation to make the test pass without saying so. Stop, name the
  specific discrepancy (which AC or corner case, what the code does vs. what the spec says it
  should do), and ask the user how they want to proceed — fix the implementation now (and note
  that's now outside this skill's stated scope), or leave it flagged as a known gap.

Only report a test as done once it's actually green for a reason you understand, not once it
happens to stop failing.

## Step 6 — Report back

Per class under test: which scenarios were written, confirmation they all pass, and which
acceptance criteria / corner cases each one maps back to (so the coverage is traceable, not just
asserted). Call out explicitly:

- Any test-support library (Mockito, AssertJ, etc.) that had to be added and flagged in Step 2.
- Any implementation discrepancy Step 5 surfaced and how it was resolved.
- That integration tests (TestContainers, happy-path end-to-end) are still separately required
  by this project's definition of done and are out of this skill's scope — don't let "unit tests
  pass" read as "testing is complete."

Once everything here is green, point the user at `user-story-integration-tests` next — running
it before `user-story-rest-api` has built the endpoint won't work, so mention that dependency
if it isn't already done.
