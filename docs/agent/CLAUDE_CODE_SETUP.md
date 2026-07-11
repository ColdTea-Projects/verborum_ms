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
│   │   ├── planner.md
│   │   ├── code-reviewer.md
│   │   ├── security-auditor.md
│   │   ├── test-writer.md
│   │   ├── service-scaffolder.md
│   │   └── event-wirer.md
│   └── skills/                    # Skills (loaded on demand into the session)
│       ├── verborum-conventions/SKILL.md
│       ├── verborum-messaging/SKILL.md
│       ├── verborum-security/SKILL.md
│       ├── verborum-testing/SKILL.md
│       ├── new-service/SKILL.md
│       ├── new-entity/SKILL.md
│       ├── new-endpoint/SKILL.md
│       ├── new-event/SKILL.md
│       └── db-migration/SKILL.md
└── ms_dictionary/
    └── CLAUDE.md                  # Per-service guide (auto-loaded in this folder)
```

## The three layers (and why)

- **CLAUDE.md** (root + per-service) — always-loaded rules. Stable facts every turn needs.
  Kept short. The root has global rules; each service folder has a thin supplement.
- **Skills** — loaded only when relevant, so long reference material costs almost nothing
  until used. Knowledge skills = "how we build"; procedure skills = "the steps, in order".
- **Subagents** — run in their own context window and return a summary, keeping big reviews,
  scaffolds, and research out of your main session.

## Installing / using

Everything is file-based — no install step. Just open the repo in Claude Code and the files
are discovered automatically:
- Root and per-directory `CLAUDE.md` load as memory.
- `.claude/skills/*/SKILL.md` register by their `description` and load when a task matches
  (or invoke explicitly with `/verborum-conventions`, `/new-entity`, etc.).
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

**Scaffold ms_user (P2-01):**
```
@service-scaffolder scaffold ms_user per the roadmap
```
Produces the running, secured shell. Then add entities:
```
/new-entity  (then: "create the User entity and migration")
```

**Add an event (P1-03):**
```
@event-wirer wire dictionary.visibility.public from ms_dictionary to ms_marketplace
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
- **Future exception:** ms_autofil (V2) uses NoSQL instead of Postgres/Liquibase, so it will
  get its own DB skill when it's built.
- **Read-only agents stay read-only.** `planner`, `code-reviewer`, and `security-auditor`
  can't write app code — this keeps planning and review honest and prevents surprise edits.
