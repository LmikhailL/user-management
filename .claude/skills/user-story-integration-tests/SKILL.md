---
name: user-story-integration-tests
description: >
  Write the happy-path Spring Boot integration test for a story's REST endpoint — real HTTP
  calls via the JDK's java.net.http.HttpClient against a running embedded server, a real
  PostgreSQL via TestContainers, structured Given/When/Then, and every integration test class
  sharing a single base class so Spring's context (and the container) loads once for the whole
  suite instead of per test. Use this whenever the user asks to write an integration test, test
  a story end-to-end, verify the real HTTP flow, or close out the integration-test half of a
  story's testing (as opposed to unit tests, which cover all cases and are
  "user-story-unit-tests"'s job — this skill is happy path only, per this project's AGENTS.md).
  Phrasing like "write the integration test for US-123", "IT test this endpoint",
  "end-to-end test RequestPasswordReset", or "test this against a real database". This skill
  requires the story's REST controller to already exist (built by "user-story-rest-api") since
  it tests through real HTTP — if it doesn't exist yet, point the user there first instead of
  testing an endpoint that isn't wired up. Use the IntelliJ IDEA MCP tools (list_directory_tree,
  read_file, search_symbol, get_symbol_info, create_new_file, apply_patch,
  execute_terminal_command, build_project, get_file_problems) wherever they fit, rather than
  shell search/edit/run commands, for surveying existing code, editing pom.xml, writing test
  files, and running the suite to confirm it actually passes and actually reuses one context.
---

# User Story Integration Tests

An integration test earns its cost by proving the whole stack actually works together — real
HTTP in, real controller, real mapper, real use case, real database. That's expensive if every
test class pays for its own Spring context and container startup, which is exactly why every
class this skill writes shares one base class with identical configuration: Spring's test
context cache keys on the configuration, so identical config across every subclass means one
context load and one container for the entire suite, not one per class. Breaking that (adding
per-class configuration that doesn't need to be per-class) quietly makes the whole suite slow
again, so it's worth protecting deliberately, not just hoping it works out.

Scope is happy path only — this project's `AGENTS.md` puts edge cases and failures on unit
tests (`user-story-unit-tests`'s job) and reserves integration tests for confirming the pieces
actually fit together.

## Step 1 — Confirm there's a real endpoint to test

Each story lives under its own `./stories/<ID>/` directory. If the user names a story ID, use
`./stories/<ID>/` directly; otherwise use `mcp__idea__list_directory_tree` on `./stories` to see
which story directories exist — if there's exactly one, use it, if there's more than one, list
them and ask which story this is for. Read that directory's `plan.md` and `spec.md` for
context, then use `mcp__idea__search_symbol` to find the story's `@RestController`. If it doesn't exist yet, stop
and point the user at `user-story-rest-api` — this skill tests through real HTTP, so there's
nothing to hit without it.

Use `mcp__idea__get_symbol_info` on the controller and on the OpenAPI-generated request/response
model classes it uses, so the test builds requests and asserts on responses against the actual
generated contract, not an assumed shape.

## Step 2 — Get (or bootstrap) the shared base integration test class

Look for an existing shared base class under `src/test/java` via `mcp__idea__search_symbol` /
`mcp__idea__list_directory_tree` (e.g. `AbstractIntegrationTest`). This repo already has the raw
material for one — `TestcontainersConfiguration` (a `@ServiceConnection`-backed Postgres
container bean) and an example `@SpringBootTest` class demonstrating the pattern.

**If no shared base class exists yet**, create one (`mcp__idea__create_new_file`) reusing that
existing `TestcontainersConfiguration` rather than duplicating container setup:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    protected final HttpClient httpClient = HttpClient.newHttpClient();
    protected final ObjectMapper objectMapper = new ObjectMapper();

    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}
```

(Adjust package/imports to match this project's actual structure — check it rather than assume.)

**If one already exists**, use it as-is. Only extend it if this story's dependencies need
something every integration test should have (rare — e.g. another universally-needed
container); a mock or setup that's specific to one story's dependencies belongs in that story's
own IT class, not the shared base, or every other integration test in the suite silently
inherits configuration it doesn't need — which is exactly the kind of divergence that breaks
context-cache reuse across the suite.

**Every IT test class this skill writes must extend this base class**, with no added
class-level configuration that isn't genuinely required — that's what keeps every subclass's
context-cache key identical.

## Step 3 — Handle non-database dependencies: container first, mock only if you must

The base class already gives every test a real Postgres via TestContainers. For any other
external dependency the use case relies on (a mail sender, a third-party API client, a message
queue):

- Check whether a TestContainers module can stand in for it (check what's already a dependency
  in `pom.xml`, and whether a suitable module exists for this kind of dependency). If yes, wire
  it in — as a shared bean in the base class if every story will need it, or scoped to this
  story's IT class if it's specific to this one. Adding a new TestContainers module dependency
  is a stack addition worth flagging to the user the same way any other new dependency in this
  project would be, per `AGENTS.md`'s closed-stack rule.
- If nothing containerizable exists for it (e.g. a real third-party SaaS client with no
  container equivalent), mock it instead using `@MockitoBean` (Spring's current bean-override
  API) — `AGENTS.md` already approves Mockito, so just verify `org.mockito.Mockito` actually
  resolves via `mcp__idea__search_symbol` with `include_external: true` before using it, adding
  it to `pom.xml` directly if it's missing rather than flagging it as a new decision. Configure
  the mock to return a realistic canned success response — this test only covers the happy path,
  so the mock only needs to support that path.

## Step 4 — Make sure Failsafe is configured (once)

Check `pom.xml` for `maven-failsafe-plugin`. `AGENTS.md` already names Failsafe as approved
build tooling for exactly this, so if it's missing, just add it via `mcp__idea__apply_patch`,
bound to the `integration-test` and `verify` goals — no sign-off needed, this is wiring in
something already agreed to, not a new stack decision. No custom include/exclude configuration should be
necessary: Failsafe's default patterns already pick up `*IT` classes, and Surefire's default
patterns already exclude them — naming every class this skill writes with an `IT` suffix (e.g.
`RequestPasswordResetIT`) is what makes that split work for free. Confirm Surefire hasn't been
customized in a way that would accidentally include `*IT` classes in the fast unit-test run
before assuming this.

## Step 5 — Write the test

One `IT`-suffixed class per story endpoint, extending the shared base class, with one (or a
small handful, only if the single happy path genuinely has more than one meaningful successful
route) `@Test` method structured Given/When/Then:

```java
@Test
@DisplayName("given <the real precondition>, when <the actual HTTP call>, then <the observable result>")
void descriptiveMethodName() throws Exception {
    // Given — seed real state via the actual repository/JPA, against the real container DB
    ...

    // When — a real HTTP call via java.net.http.HttpClient to baseUrl(), body serialized
    // through the shared ObjectMapper using the OpenAPI-generated request model
    ...

    // Then — assert on the real HTTP response (status, body deserialized into the generated
    // response model) AND on the real persisted state via the repository — an integration test
    // that only checks the HTTP response without confirming the DB actually changed hasn't
    // proven the layers are wired together correctly
    ...
}
```

## Step 6 — Run it, and confirm the context is actually shared

Run `./mvnw verify` via `mcp__idea__execute_terminal_command` — not `mvn test`, since these
live in the Failsafe-bound phase. Don't stop at "it passed": check the output for how many
times the Spring context (and the container) actually started. One start for the whole run
means the shared base class is doing its job; a start per class means something broke context
caching (an accidental per-class annotation, differing property sources) and is worth fixing
before calling this done, since that's the entire point of the shared-base-class requirement.

When a test fails, diagnose before changing anything: a wrong expectation in the test gets
fixed; a real gap between what the endpoint actually does and what the spec says it should do
is a genuine bug — stop, name the specific discrepancy, and ask the user how to proceed rather
than quietly editing either side to force a pass.

## Step 7 — Report back

The endpoint tested, the scenario(s) covered, confirmation the suite passes via `mvn verify`,
and confirmation the context/container loaded once across the run (not per class). Call out
explicitly: any new dependency added (a TestContainers module, the Failsafe plugin, a mocked
external client and why no container existed for it), and any implementation discrepancy Step 6
surfaced and how it was resolved.

With this passing, it's a reasonable point to (re)run `architecture-tests` if it hasn't run
recently — the new classes this story added are exactly what that suite needs to check.
