# Verborum MS — Claude Code Agent Guide

You are a senior Java/Spring Boot engineer helping build the **Verborum** microservices backend.
Always load the relevant skills (`.claude/skills/`) before writing any code.

---

## Project Summary

Verborum is a language learning app where users create personal vocabulary dictionaries,
share them on a marketplace, and (V2) get AI-powered word suggestions.

**Stack:** Java 17 · Spring Boot 3.2.2 · Maven (wrapper) · PostgreSQL 14 + Liquibase · RabbitMQ 3 ·
Keycloak 23 · Lombok + MapStruct · springdoc-openapi · JUnit 5 + Mockito + JaCoCo · Docker Compose

**Repo layout:**
```
verborum_ms/
├── CLAUDE.md                  ← you are here
├── pom.xml                    ← aggregator pom — register every new service in <modules>
├── docker-compose.yml         ← full local infra (root); per-module files clash on ports
├── .env.example               ← committed template; real .env is git-ignored
├── .claude/skills/            ← the working conventions — load before writing code
├── .claude/agents/            ← subagents for delegated work
├── docs/agent/                ← project state + long-form rationale
├── docs/ops/                  ← local development, dockerization plan
├── docs/integration/          ← client-facing contracts
├── keycloak/                  ← realm import, bootstrap scripts, custom image, themes
├── ms_dictionary/             ← ✅ complete and secured (reference implementation)
├── ms_user/                   ← ✅ Phase 2 complete, verified over HTTP
└── sql_dumps/
```

---

## Reference Docs — Read These Before Acting

The `.claude/skills/` files (listed further down) are the working conventions. These docs hold the
project state and the long-form rationale behind them.

| File | Read when... |
|---|---|
| `docs/agent/verborum.md` | Starting any task — always read this first |
| `docs/agent/roadmap.md` | Asked "what's next?", "what should I build?", or "what's the status?" |
| `docs/agent/java-spring.md` | Background on the Spring Boot / JPA / Liquibase patterns |
| `docs/agent/clean-code.md` | Background on the naming and structure conventions |
| `docs/agent/rabbitmq.md` | Background on messaging — the seven rules in full |
| `docs/agent/security.md` | The normative auth contract — realm, clients, tokens, ownership rules |
| `docs/agent/testing.md` | Background on test conventions |
| `docs/integration/client-login-guide.md` | Any question from a client team (Android/iOS/web) about login, sign-up, tokens or identity ids |
| `docs/integration/kmp-client-alignment.md` | Working on or asked about the KMP (iOS/web) client — what it does not yet use, and what this backend already offers it |
| `docs/ops/local-development.md` | Running the stack, getting a token, running tests, verifying events by hand, troubleshooting |
| `docs/ops/dockerization-and-environments.md` | The containerization plan — topologies, Dockerfiles, environments, deployment |

---

## Answering "What Should I Build Next?"

1. Read `docs/agent/roadmap.md`
2. Find the current phase — the first phase that has any `[ ]` tasks
3. Within that phase, find the first `[ ]` task whose dependencies are all `[x]`
4. Explain: what the task is, which files to create or modify, and why it comes before the others
5. Name the skill that covers it and the agent that should do it (`spring-boot-architect` for a new
   service, a new cross-service event, or a boundary decision; `spring-boot-development` for feature
   work inside an existing service)
6. After completing a task together, mark it `[x]` in `roadmap.md`

Never skip phases. Never start a task whose dependencies are not marked `[x]`.

---

## Agents (Subagents)

Delegate specialized work to these subagents (in `.claude/agents/`):

| Agent | Use for | Writes code? |
|---|---|---|
| `spring-boot-architect` | Scaffolding a service, wiring an event end-to-end, boundary decisions | Yes (structure) |
| `spring-boot-development` | Feature work inside an existing service — entities, endpoints, migrations | Yes (features) |
| `planner` | "What's next?", status, marking roadmap tasks done | No (docs only) |
| `code-reviewer` | Reviewing a diff against conventions before commit | No (reports) |
| `security-auditor` | Checking a service is safe to expose | No (reports) |
| `test-writer` | Unit tests and MockMvc web-slice tests per project style | Yes (tests) |

## Skills

Eleven skills in `.claude/skills/`, one per area of the stack, each written to the `write-a-skill`
format: a `SKILL.md` under 100 lines holding the quick start and the rules, with the detail and the
step-by-step checklists in a flat `references/` folder beside it.

```
.claude/skills/{skill}/
├── SKILL.md            ← always read this first (≤ 100 lines)
└── references/*.md     ← open the one the SKILL.md points at
```

| Skill | Covers | Checklist in `references/` |
|---|---|---|
| `java` | Lombok sets per class kind, constants, utility classes, locale and null rules | — |
| `spring-boot` | Beans, config classes, `application.properties`, actuator exposure | — |
| `spring-boot-app-architecture` | Topology, package layout, layering, service boundaries | `scaffold-a-service.md` |
| `maven` | Aggregator pom, module registration, the pinned dependency set, build commands | — |
| `web-api` | Controllers, the Response envelope, validation, exception handling, status semantics | `add-an-endpoint.md` |
| `persistence` | Postgres, JPA entities, repositories, Liquibase migrations | `add-an-entity.md` |
| `messaging` | RabbitMQ, routing keys, the seven rules, after-commit publishing, dead-letter setup | `wire-an-event.md` |
| `security` | Keycloak, JWT resource server, realm roles, ownership 403/404 rules, secrets | `ownership-rules.md` |
| `infra-ops` | Compose, port map, `.env`, running the stack, verification recipes, troubleshooting | `verification-recipes.md` |
| `unit-testing` | JUnit 5 + Mockito conventions for isolated tests | `mockito-patterns.md` |
| `integration-testing` | MockMvc web slices, full-context tests, manual end-to-end verification | `web-slice-tests.md` |

**Load the skills that match the layers you are touching.** A typical endpoint change is
`web-api` + `persistence` + `unit-testing`; a new event is `messaging` + `infra-ops`.

**Authoring or expanding a skill?** Use the user-level `write-a-skill` skill — it carries the
format rules (description with an explicit "Use when" trigger, the 100-line ceiling, one-level-deep
references, concrete examples) and validators that check them:

```bash
python3 ~/.claude/skills/write-a-skill/scripts/skill_review_checklist_runner.py .claude/skills/<name>
```

Every skill here passes 6/6. Keep it that way — the gate is what stops a skill drifting back into a
300-line manual.

---

## Report Which Skills You Used — Every Response

**End every response with a skills footer.** This is mandatory and applies to every turn, including
answers to questions, reviews, and turns that write no code.

Format — a horizontal rule, then one line:

```
---
**Skills used:** `web-api`, `persistence` → `add-an-endpoint.md`, `liquibase-migrations.md`
```

Rules:

- List the skills whose `SKILL.md` you actually read this turn, in the order you loaded them.
- After the arrow, name any `references/*.md` files you opened. Omit the arrow if none.
- If you used none, say so plainly: **`Skills used:** none`. Do not pad the list with skills that
  looked relevant but were never opened — the point is an honest record of what informed the
  answer, not a relevance guess.
- Recalled context does not count. Only files read in this turn.
- A subagent reports its own skills inside its final report; the main agent lists what **it** read,
  and names the subagent alongside.

Why: it makes the routing visible. A change that touched persistence without loading `persistence`
is a signal the answer may be running on memory rather than the current conventions — and it shows
which skills earn their place versus which are never reached.

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

5. **No hardcoded secrets.** Credentials, URLs, and keys are `${VAR:local-default}` in
   `application.properties` (with a placeholder in `.env.example`) — never in Java code, never in
   an image, never in the Keycloak realm JSON.

6. **Security is never a later task.** Every service ships with `SecurityConfig` from its first
   commit, and ownership comes from the JWT subject — never from an id in the body or path.

7. **Ask before inventing.** If something is unclear (e.g. a missing endpoint, an ambiguous
   field, a port or DB name marked TBD), ask rather than assume.

8. **End every response with the skills footer.** See "Report Which Skills You Used" above.
   Honest record of what was read, not a relevance guess.
