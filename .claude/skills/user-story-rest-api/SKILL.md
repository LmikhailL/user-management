---
name: user-story-rest-api
description: >
  Build a REST endpoint contract-first: write (or update) an OpenAPI spec for one use case,
  generate its Java controller interface and model DTOs from that spec via the
  openapi-generator-maven-plugin, then implement the generated interface with a
  @RestController that delegates to the existing use-case class, wiring its domain exceptions
  into the project's shared @RestControllerAdvice — following this project's AGENTS.md layering
  rules. Use this whenever the user asks to add a REST endpoint, expose a
  use case over HTTP, "wire up the API for" a story, generate an OpenAPI spec and implement it,
  or create a controller from a spec — phrasing like "add the REST endpoint for US-123", "expose
  RegisterUserUseCase over HTTP", "write the OpenAPI spec and controller for this", or "generate
  the API interface and implement it". This skill only builds the web layer on top of a use case
  that must already exist in the codebase — it does not create domain or persistence code, and
  it does not write business logic. If the story's implementation plan
  (./stories/<ID>/plan.md) says the use case is still "New" (not yet built), stop and tell
  the user to implement it first rather than inventing one. Use the IntelliJ IDEA MCP tools
  (list_directory_tree, search_symbol, search_file, search_text, get_symbol_info, read_file,
  create_new_file, apply_patch, execute_terminal_command, build_project, get_file_problems)
  wherever they fit, rather than shell search/edit commands — surveying existing code, editing
  pom.xml, running the codegen build, and verifying compilation. Do not use this for generating
  the story spec (that's "user-story-spec"), validating it ("user-story-spec-validate"), or
  planning the build ("user-story-spec-plan") — this skill is the last step: turning an already
  -planned, already-built use case into a real HTTP endpoint.
---

# User Story REST API

This is a contract-first workflow: the OpenAPI spec is the source of truth for the endpoint's
shape, a generated Java interface enforces that the controller can never silently drift from
it, and the controller itself is thin — it only adapts HTTP to the use case that already holds
the actual business logic. If that use case doesn't exist yet, there's nothing to wire up; stop
rather than inventing one, since scaffolding fake domain logic here would violate the same
layering discipline this skill exists to uphold.

## Step 1 — Find out what to build

Each story lives under its own `./stories/<ID>/` directory. If the user names a story ID, look
for `./stories/<ID>/plan.md` directly; if they haven't, use `mcp__idea__list_directory_tree` on
`./stories` to see which story directories exist — if there's exactly one, use it, if there's
more than one, list them and ask which story this is for. Read the plan with
`mcp__idea__read_file` — it should already name the endpoint's controller class, the use-case
class it delegates to, and the request/response shape per acceptance criterion. Use that as the
source of truth for what you're building.

If no story directory has a plan at all (the user is asking for this ad hoc, outside the story
pipeline), ask them directly for what's missing: the HTTP method and path, the request/response
fields, and which existing use-case class this should call. Don't guess at a contract nobody
described.

## Step 2 — Confirm the use case already exists

Use `mcp__idea__search_symbol` to find the use-case class this endpoint is supposed to call.
If it's not there:

- Stop. Tell the user which class is missing and that this skill only builds the web layer on
  top of code that already exists — point them at `user-story-business-logic` to build it
  rather than scaffolding a stand-in.

If it exists, use `mcp__idea__get_symbol_info` on its public method to get the exact command/
result types it expects — the controller's mapping code in Step 6 has to match these exactly,
not an assumed shape.

## Step 3 — Write the OpenAPI spec

One file per use case, under `src/main/resources/openapi/<kebab-case-use-case-name>.yaml` (e.g.
`request-password-reset.yaml` for `RequestPasswordResetUseCase`) — this keeps each endpoint's
contract isolated and reviewable on its own, matching this project's one-controller-per-use-case
rule.

If a spec file for this endpoint already exists, read it first (`mcp__idea__read_file`) and
update it rather than overwriting blindly — someone may have hand-tuned a description or an
example. If it doesn't exist, write it fresh with `mcp__idea__create_new_file`.

The spec needs, at minimum: the path and HTTP method, an `operationId` (this becomes the
generated interface's method name — pick something that reads naturally, e.g.
`requestPasswordReset`), request/response schemas with the fields the use case's command/result
types actually have (from Step 2 — don't invent fields the use case can't fulfill), and the
realistic non-2xx responses. For the latter, don't guess — use `mcp__idea__search_text` (or
`search_symbol`) on the use case to see which concrete exceptions it can actually throw, then
declare one response per `common.exception` category those exceptions extend (404 for
`NotFoundException`, 409 for `ConflictException`, 400 for `ValidationException`) using a small
generic error schema (message + category) inline in this spec — each spec stays self-contained
rather than `$ref`-ing a shared file, consistent with why these specs are one-per-use-case in
the first place.

## Step 4 — Make sure the codegen plugin is configured

Check `pom.xml` (`mcp__idea__read_file`) for `openapi-generator-maven-plugin`.

**If it's already configured**, just add this spec file to its `inputSpec`/multi-spec
configuration if the existing setup handles one file at a time, and move on.

**If it isn't configured yet**, this is a one-time setup — but not a stack decision to surface
for sign-off: `AGENTS.md` already names the OpenAPI Generator plugin as approved build-time
tooling for exactly this. Just add it via `mcp__idea__apply_patch`, configured for
**interface-only generation** (`interfaceOnly: true`
for the `spring` generator) so it produces the controller interface and model classes but
never a concrete controller — that's the skill's job in Step 6, not the generator's. Point the
generated sources at a package consistent with this project's actual base package (find it by
locating the `@SpringBootApplication` class via `mcp__idea__search_symbol`, don't assume one),
under a `web.generated` (or similar) sub-package so it's obviously not hand-written code.

## Step 5 — Run the codegen

Use `mcp__idea__execute_terminal_command` to run `./mvnw generate-sources` so the plugin
actually produces the interface and models before you write code against them. Then locate the
generated interface (`mcp__idea__search_file` under `target/generated-sources`) and read it to
get its exact package, interface name, and method signature — write against what was actually
generated, not what you expect the generator to have produced.

## Step 6 — Implement the controller

Create a `@RestController` implementing the generated interface, named for the use case per
this project's naming rules (e.g. `RequestPasswordResetController`, one class per use case,
never a shared multi-endpoint controller). It should do exactly three things: accept the
generated request model, map it to the use case's command type, call the use case, and map the
result back to the generated response model.

That mapping goes through a MapStruct mapper (e.g. `RequestPasswordResetWebMapper`), converting
between the OpenAPI-generated models and the use case's own command/result records — per this
project's rule that every cross-layer conversion goes through MapStruct, no hand-written
mapping. Create it with `mcp__idea__create_new_file`.

The controller itself should contain no business logic — if you find yourself writing a
conditional that isn't purely about HTTP concerns (status code selection, header setting), that
logic belongs in the use case, and its absence there means Step 2 should have stopped you
earlier, not that this controller should compensate for it.

## Step 7 — Make sure domain exceptions map to the right HTTP response

The exceptions Step 3 found the use case can throw should already extend one of
`common.exception`'s three categories, per `AGENTS.md`'s error-handling rule. Use
`mcp__idea__search_symbol` to find the shared `@RestControllerAdvice` (in `common.web` or
equivalent) that maps those categories to HTTP status codes.

- **If it doesn't exist yet**, this is a one-time bootstrap, not a per-endpoint task — create it
  (`mcp__idea__create_new_file`) with one `@ExceptionHandler` per category (`NotFoundException`
  → 404, `ConflictException` → 409, `ValidationException` → 400), returning a small generic
  error body (matching the schema Step 3 declared) rather than leaking internal exception
  detail. Every future endpoint reuses this same class without needing to touch it again.
- **If it exists**, confirm it already covers every category this use case's exceptions extend —
  it should, since new concrete exceptions are supposed to extend an existing category rather
  than invent one. Only add a new handler if `user-story-business-logic`'s report flagged a
  genuinely new category, and treat that the same as any other shared-file change: worth telling
  the user about explicitly, not a silent edit.

## Step 8 — Verify it compiles

Run `mcp__idea__build_project` (or `mcp__idea__get_file_problems` on the new files) after
writing the controller and mapper. Fix anything that doesn't compile before calling this done —
a generated interface whose method signature doesn't match your controller's `@Override` is the
most common failure here, and it's cheap to catch immediately rather than leave for the user to
discover.

## Step 9 — Report back

Tell the user: the endpoint's method and path, which files were created or changed (spec,
generated interface location, controller, mapper, the shared exception handler if this was the
first endpoint to bootstrap or extend it, and `pom.xml` if this was the first endpoint using the
plugin), and confirm the build passed. If Step 2 stopped you because the use case didn't exist,
that's the entire report — say so plainly rather than partially building around the gap, and
don't suggest a next step that assumes this succeeded.

If it did succeed, point the user at `user-story-integration-tests` next for happy-path
end-to-end coverage of the endpoint just built.
