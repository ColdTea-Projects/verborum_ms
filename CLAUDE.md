# Verborum MS — Claude Code Agent Guide

You are a senior Java/Spring Boot engineer helping build the **Verborum** microservices backend.
Always read the relevant skill files before writing any code.

---

## Project Summary

Verborum is a language learning app where users create personal vocabulary dictionaries,
share them on a marketplace, and (V2) get AI-powered word suggestions.

**Stack:** Java 17, Spring Boot 3.2.2, Maven, PostgreSQL, Liquibase, RabbitMQ, Keycloak

**Repo layout:**
```
verborum_ms/
├── CLAUDE.md                  ← you are here
├── pom.xml                    ← aggregator pom — register every new service in <modules>
├── docs/agent/                ← all skill files
├── ms_dictionary/             ← ✅ almost complete (reference implementation)
├── ms_user/                   ← 🚧 scaffolded, entities/endpoints to be built
└── sql_dumps/
```

---

## Skill Files — Read These Before Acting

| File | Read when... |
|---|---|
| `docs/agent/verborum.md` | Starting any task — always read this first |
| `docs/agent/java-spring.md` | Writing any Java/Spring Boot code |
| `docs/agent/clean-code.md` | Writing any code — conventions must match existing style |
| `docs/agent/rabbitmq.md` | Working on messaging, events, or inter-service communication |
| `docs/agent/security.md` | Working on auth, Keycloak, JWT, or endpoint protection |
| `docs/agent/testing.md` | Writing or reviewing any test code |
| `docs/agent/roadmap.md` | Asked "what's next?", "what should I build?", or "what's the status?" |
| `docs/integration/client-login-guide.md` | Any question from a client team (Android/iOS/web) about login, sign-up, tokens or identity ids |

---

## Answering "What Should I Build Next?"

1. Read `docs/agent/roadmap.md`
2. Find the current phase — the first phase that has any `[ ]` tasks
3. Within that phase, find the first `[ ]` task whose dependencies are all `[x]`
4. Explain: what the task is, which files to create or modify, and why it comes before the others
5. After completing a task together, mark it `[x]` in `roadmap.md`

Never skip phases. Never start a task whose dependencies are not marked `[x]`.

---

## Agents (Subagents)

Delegate specialized work to these subagents (in `.claude/agents/`):

| Agent | Use for | Writes code? |
|---|---|---|
| `planner` | "What's next?", status, marking roadmap tasks done | No (docs only) |
| `code-reviewer` | Reviewing a diff against conventions before commit | No (reports) |
| `security-auditor` | Checking a service is safe to expose | No (reports) |
| `test-writer` | Writing unit tests per project style | Yes (tests) |
| `service-scaffolder` | Creating a new microservice shell | Yes |
| `event-wirer` | Wiring a RabbitMQ event end-to-end | Yes |

## Skills

Knowledge skills (standing conventions, `.claude/skills/`):
`verborum-conventions`, `verborum-messaging`, `verborum-security`, `verborum-testing`.

Procedure skills (step-by-step recipes):
`new-service`, `new-entity`, `new-endpoint`, `new-event`, `db-migration`.

Invoke the procedure skill that matches the task (e.g. adding a table → `new-entity`;
adding an event → `new-event`). Skills reference the detailed docs in `docs/agent/`.

---

## Per-Service Guides

Each service folder has its own thin `CLAUDE.md` (service purpose, port, DB, entities, events,
status, quirks). Claude Code loads it automatically when working in that directory. It
supplements — never overrides — this root file and the shared conventions.

---

## Golden Rules

1. **`ms_dictionary` is the reference implementation.** When in doubt about structure, patterns,
   or conventions, look there first. New services must mirror it.

2. **Never break existing conventions.** Don't introduce new patterns (e.g. `@Autowired`,
   different response structures, different ID strategies) without explicit instruction.

3. **Always read `verborum.md` first.** It contains the current state of the project,
   what is built, what is missing, and the domain model.

4. **One Liquibase changeset per schema change.** Never modify existing changesets.

5. **No hardcoded secrets.** Credentials, URLs, and keys go in `application.properties`
   or environment variables — never in Java code.

6. **Ask before inventing.** If something is unclear (e.g. a missing endpoint, an ambiguous
   field), ask rather than assume.
