---
name: user-story-liquibase
description: >
  Write the Liquibase YAML changeset(s) for the schema change a story's implementation plan
  calls for at ./stories/<ID>/plan.md — deriving exact column types, nullability, and
  constraints from the actual @Entity class (built by "user-story-business-logic") rather than
  the plan's prose, then registering the changeset so it's picked up by the master changelog.
  Use this whenever a story's plan adds or changes a persistence-layer component that implies a
  new table or column, or when the user directly asks to "add a migration", "create a
  changeset", "add a column via Liquibase", or "generate the schema change for" a story.
  This skill requires the corresponding @Entity class to already exist — it reads the entity's
  real fields and JPA annotations via IntelliJ IDEA MCP tools to get accurate column
  definitions, so if the entity is still "New" in the plan, stop and point the user at
  "user-story-business-logic" first rather than guessing column types from the plan text alone.
  Only produces the Liquibase YAML changeset and, on first use, the master changelog and the
  pom.xml/AGENTS.md bootstrap if genuinely missing — it does not write or modify Java code (that
  is "user-story-business-logic"'s job) and does not apply the migration against a live
  database. Use the IntelliJ IDEA MCP tools (list_directory_tree, read_file, search_symbol,
  search_text, get_symbol_info, create_new_file, apply_patch, execute_terminal_command,
  build_project, get_file_problems) wherever they fit, rather than shell search/edit commands,
  for surveying the entity and existing changelogs, writing the changeset and master changelog
  files, and confirming nothing is broken.
---

# User Story Liquibase Changesets

This project's rule is that Liquibase changesets are the only source of DDL — never manual SQL,
never Hibernate auto-generated schema. The one thing worth being careful about: the plan
describes *intent* ("add an email column"), but the changeset needs to match what the entity
class *actually* declares — its real Java type, its `@Column` constraints, its nullability. Read
the entity, don't infer types from the plan's one-line description.

## Step 1 — Load the plan

Each story lives under its own `./stories/<ID>/` directory. If the user names a story ID, use
`./stories/<ID>/` directly; otherwise use `mcp__idea__list_directory_tree` on `./stories` to see
which story directories exist — if there's exactly one, use it, if there's more than one, list
them and ask which story this is for.

Require that directory's `plan.md` to exist — read it with `mcp__idea__read_file`. If it doesn't
exist and the user hasn't given you an explicit ad hoc schema change to make instead, stop and
tell them to run `user-story-spec-plan` first.

## Step 2 — Find the schema-affecting components

From the plan's Component Plan table, take the persistence-layer rows that actually imply a
schema change: a new `@Entity` (new table) or a new/changed field on an existing entity (new
column). Rows that are purely new repository methods, queries, or other code with no new
persisted field don't need a changeset — skip them.

## Step 3 — Confirm the entity actually exists

Use `mcp__idea__search_symbol` to find each in-scope entity class.

- **Doesn't exist yet** → stop. Tell the user this skill derives column definitions from the
  real entity class, and point them at `user-story-business-logic` to build it first — don't
  guess a schema from the plan's prose, since a mismatch here means the changeset and the actual
  JPA mapping silently diverge.
- **Exists** → continue to Step 4 with this class.

## Step 4 — Read the entity's real shape

Use `mcp__idea__get_symbol_info` and `mcp__idea__read_file` on the entity class to get its
actual fields and annotations: `@Id`/`@GeneratedValue` (primary key strategy), `@Column`
(name overrides, `nullable`, `unique`, `length`), `@Enumerated` (stored as string vs. ordinal),
relationship annotations (`@ManyToOne`/`@JoinColumn`, foreign keys), and plain Java types for
everything else. Map Java/JPA types to PostgreSQL column types explicitly — don't let a mapping
guess slip through:

| Java type | PostgreSQL type |
|---|---|
| `String` | `VARCHAR(n)` if `@Column(length=n)` set, else `VARCHAR(255)` |
| `UUID` | `UUID` |
| `Long`/`long` | `BIGINT` |
| `Integer`/`int` | `INT` |
| `Boolean`/`boolean` | `BOOLEAN` |
| `BigDecimal` | `NUMERIC` (with precision/scale if `@Column` declares them) |
| `LocalDate` | `DATE` |
| `LocalDateTime`/`Instant` | `TIMESTAMP` |
| `enum` (`@Enumerated(STRING)`) | `VARCHAR(255)` |
| `enum` (`@Enumerated(ORDINAL)` or unspecified) | `INT` — flag this to the user, since storing enums ordinally is fragile; confirm it's intentional |

## Step 5 — Check for an existing changeset first

Use `mcp__idea__search_text` (or `list_directory_tree`) over
`src/main/resources/db/changelog/changes/` to see whether a changeset for this table/column
already exists. If one does and already matches, skip it and note that in your report — don't
create a duplicate `createTable`/`addColumn` for something already migrated. If it exists but no
longer matches the entity (drift), tell the user rather than silently adding a second changeset
that fights the first.

## Step 6 — Write the changeset

One file per changeset, under `src/main/resources/db/changelog/changes/`, named
`YYYYMMDDHHmmss_description.yaml` (e.g. `20260829163000_create_users_table.yaml`). Use
`mcp__idea__execute_terminal_command` (`date +%Y%m%d%H%M%S`) to get a real current timestamp
rather than inventing one.

Each file holds one `databaseChangeLog` with one `changeSet`:
- `id`: a short descriptive slug matching the filename minus the timestamp/extension (e.g.
  `create-users-table`) — not a bare number, so it stays meaningful in `DATABASECHANGELOG`.
- `author`: run `git config user.name` via `mcp__idea__execute_terminal_command`; if that's
  empty, ask the user what to use rather than inventing a name.
- Use the appropriate change type — `createTable` for a new entity, `addColumn` for a new field
  on an existing table — with each column's `name`, `type`, and constraints (`nullable`,
  `unique`, `primaryKey`, `foreignKeyName`/`references` for relationships) from Step 4.
- Always include an explicit `rollback` block (`dropTable` / `dropColumn` as appropriate) per
  `AGENTS.md`'s migration rule — don't rely on Liquibase's implicit rollback inference.

Write it with `mcp__idea__create_new_file`.

## Step 7 — Make sure the master changelog picks it up

Check for `src/main/resources/db/changelog/db.changelog-master.yaml` with
`mcp__idea__search_file`.

- **Missing** (first changeset in the project) → this is a one-time bootstrap, not a stack
  decision to surface — `AGENTS.md` already names Liquibase and this directory layout as
  approved. Create it with `mcp__idea__create_new_file` containing a single `includeAll`
  pointing at `db/changelog/changes/` (relative to the changelog file), so every future
  changeset in that directory is picked up automatically with no further edits to this file.
- **Exists** → read it and confirm the `includeAll` already covers the changes directory. If
  someone changed it to list files individually instead, follow that project's existing
  convention (add an explicit `include` entry) rather than silently switching its structure.

## Step 8 — Verify nothing is broken

Run `mcp__idea__build_project` (or `mcp__idea__get_file_problems` on the new YAML files) to
confirm the changeset and master changelog are well-formed. This doesn't apply the migration
against a live database — that happens when the app boots or via
`user-story-integration-tests`'s TestContainers run — so don't report this step as having
verified the DDL actually executes cleanly.

## Step 9 — Report back

Tell the user: which changeset file(s) were created (or which were skipped because they already
existed, per Step 5), the table/columns each one adds or changes, whether the master changelog
was created fresh or already covered it, and any type-mapping calls worth a second look (e.g.
the ordinal-enum flag from Step 4). Make clear this only adds the migration file — it hasn't
been run against a real database yet, and doesn't replace `user-story-integration-tests` for
confirming the schema actually works end-to-end.
