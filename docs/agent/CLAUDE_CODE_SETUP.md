# Claude Code Setup for Verborum

This repo is configured for Claude Code with a root memory file, shared skills, subagents,
and per-service guides. Here's how it all fits together and how to use it.

## What's in the repo

```
verborum_ms/
├── CLAUDE.md                      # Root memory — always loaded. Orientation + rules.
├── docs/agent/                    # Detailed knowledge (referenced by skills)
│   ├── verborum.md                #   project state, domain, APIs, events
│   ├── roadmap.md                 #   phased build plan with task IDs (P0-01 …)
│   ├── clean-code.md              #   conventions
│   ├── java-spring.md             #   Spring Boot / JPA / Liquibase patterns
│   ├── rabbitmq.md                #   messaging patterns
│   ├── security.md                #   Keycloak / JWT
│   └── testing.md                 #   test conventions
├── .claude/
│   ├── agents/                    # Subagents (isolated context, delegated work)
│   │   ├── spring-boot-architect.md
│   │   ├── spring-boot-development.md
│   │   ├── planner.md
│   │   ├── code-reviewer.md
│   │   ├── security-auditor.md
│   │   └── test-writer.md
│   └── skills/                    # Skills (loaded on demand into the session)
│       ├── java/                                # language style
│       ├── spring-boot/                         # framework mechanics + config
│       ├── spring-boot-app-architecture/        # layout, boundaries, new service
│       ├── maven/                               # build, modules, dependencies
│       ├── web-api/                             # controllers, DTOs, errors, new endpoint
│       ├── persistence/                         # JPA, Postgres, Liquibase, new entity
│       ├── messaging/                           # RabbitMQ, events, new event
│       ├── security/                            # Keycloak, JWT, ownership
│       ├── infra-ops/                           # compose, ports, .env, running & debugging
│       ├── unit-testing/
│       └── integration-testing/
│            each: SKILL.md (≤100 lines) + references/*.md (detail + checklists)
└── ms_dictionary/
    └── CLAUDE.md                  # Per-service guide (auto-loaded in this folder)
```

## The three layers (and why)

- **CLAUDE.md** (root + per-service) — always-loaded rules. Stable facts every turn needs.
  Kept short. The root has global rules; each service folder has a thin supplement.
- **Skills** — loaded only when relevant, so long reference material costs almost nothing
  until used. One skill per area of the stack, each holding both the standing conventions for that
  area and the step-by-step checklist for changing it (adding an endpoint lives in `web-api`,
  adding a table in `persistence`, wiring an event in `messaging`, scaffolding a service in
  `spring-boot-app-architecture`, running and debugging the stack in `infra-ops`).
  Each is a folder: a `SKILL.md` under 100 lines with the quick start and the rules, plus a flat
  `references/` folder holding the detail. The agent reads the SKILL.md and either has enough to
  act or has a direct link to the one reference file that answers its question — no deeper.
- **Subagents** — run in their own context window and return a summary, keeping big reviews,
  scaffolds, and research out of your main session.

## Installing / using

Everything is file-based — no install step. Just open the repo in Claude Code and the files
are discovered automatically:
- Root and per-directory `CLAUDE.md` load as memory.
- `.claude/skills/*/SKILL.md` register by their `description` and load when a task matches
  (or invoke explicitly with `/persistence`, `/web-api`, etc.).
- `.claude/agents/*.md` are available for delegation. Claude picks them automatically based
  on their `description`, or you invoke one explicitly, e.g. `@planner what's next?`.

## Typical workflows

**Start of a session — what should I build?**
```
@planner what's next?
```
The planner reads the roadmap, finds the first unblocked task, and tells you the task ID,
files, and which skill to use.

**Fix the first bug (P0-03):**
```
Fix P0-03 — add @Getter to Response and ErrorResponse
```
Main agent does it (small task). Then:
```
@code-reviewer review my changes
```

**Scaffold a service (e.g. ms_marketplace, P4-01):**
```
@spring-boot-architect scaffold ms_marketplace per the roadmap
```
Produces the running, secured shell. Then add entities and endpoints:
```
@spring-boot-development create the Listing entity, migration and CRUD endpoints
```

**Add an event:**
```
@spring-boot-architect wire dictionary.visibility.public from ms_dictionary to ms_marketplace
```

**Before committing anything:**
```
@code-reviewer review the diff
```
And for a service you're about to expose:
```
@security-auditor is ms_user safe to ship?
```

**After finishing a task — update the roadmap:**
```
@planner mark P0-03 done
```

## Design decisions (recap)

- **Per-project, not per-service, for conventions.** All services are homogeneous (same
  stack, mirror ms_dictionary), so shared skills/agents live once at the root. Duplicating
  them per service would cause drift — the exact thing conventions prevent.
- **Per-service only for facts.** Each service's own `CLAUDE.md` holds its port, DB, entities,
  events, and quirks — the things that genuinely differ.
- **Skills are split by stack area, not by task.** Each one owns both the conventions and the
  procedure for its layer, so there is a single place to look and a single place to update when
  the code moves on. Task-shaped skills (`new-entity`, `new-event`, …) duplicated conventions and
  drifted from them.
- **Every response ends with a skills footer.** The root `CLAUDE.md` requires each turn — and each
  subagent's final report — to list the skills actually read that turn, and the `references/*.md`
  files opened. It makes routing visible: a persistence change made without loading `persistence`
  is a signal the answer ran on memory rather than the current conventions, and over time it shows
  which skills earn their place versus which are never reached. Honest record, not a relevance
  guess — `none` is a valid answer.
- **Skills follow the `write-a-skill` format** (Matt Pocock's, MIT — installed at
  `~/.claude/skills/write-a-skill/`): an explicit "Use when" trigger in the description, a
  `SKILL.md` under 100 lines, one-level-deep references, concrete examples. Its
  `scripts/skill_review_checklist_runner.py` enforces all six checks; every skill here passes 6/6.
  Run it before merging a skill change.
- **Two writing agents, split by scope.** `spring-boot-architect` decides and lays down structure
  (services, cross-service events, boundaries); `spring-boot-development` implements inside it.
  Structural mistakes are the expensive ones, so they get their own reviewer-of-first-resort.
- **Future exception:** ms_autofil (V2) uses NoSQL instead of Postgres/Liquibase, so it will
  get its own persistence skill when it's built.
- **Read-only agents stay read-only.** `planner`, `code-reviewer`, and `security-auditor`
  can't write app code — this keeps planning and review honest and prevents surprise edits.
