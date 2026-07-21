# ms_dictionary — Service Guide

This file supplements the root `CLAUDE.md` and `docs/agent/` skills. It covers only what is
specific to this service. All conventions are inherited from the root — do not duplicate them.

## What this service does
Full CRUD for **Dictionaries** and **Words** — the core vocabulary store.

## Facts
- **Port:** 8085
- **DB:** `vdbdictionary` (PostgreSQL) — docker-compose in this module (Postgres + Adminer)
- **RabbitMQ:** only the **root** `docker-compose.yml` runs a broker — this module's compose file
  has Postgres + Adminer but no RabbitMQ. Use the root compose for anything touching events.
  The two bind the same host ports, so run one or the other, never both.
- **Base package:** `de.coldtea.verborum.msdictionary`
- **Status:** Functionally complete, NOT production-ready (no security yet — see Phase 3)

## Entities
- `Dictionary` (`dictionaries`) — `dictionaryId`, `userId` (fk_user_id), `name`, `isPublic`,
  `fromLang`, `toLang`, timestamps
- `Word` (`words`) — `wordId`, `dictionaryId` (fk_dictionary_id), `word`, `wordMeta` (json),
  `translation`, `translationMeta` (json), timestamps

## Events
- **Publishes:** `dictionary.visibility.public/private`, `dictionary.deleted`,
  `word.created` (once Phase 1 is done)
- **Consumes:** `user.deleted` (cascade-delete this user's dictionaries and words)

## Service-specific quirks
- The JPA `@OneToMany`/`@ManyToOne` between Dictionary and Word is **intentionally disabled**.
  Joins are done via explicit repository calls. Do not activate the relationship without
  discussion.
- `wordMeta` / `translationMeta` are `json` columns in Postgres, mapped as `String` in Java
  via `@JdbcTypeCode(SqlTypes.JSON)`. The value must be valid JSON or the insert fails at the DB.
  The canonical shape is a JSON object `{lang, type?, genders?, fields?}` with all lists
  index-aligned to the surfaces array — owned by the clients, stored opaquely here. See
  `Word.java` and `docs/integration/frontend-backend-integration.md` §4.2 for the full contract.
- `word` / `translation` are `TEXT` columns holding a **JSON array of per-meaning surface forms**
  as a string (e.g. `["kaufen","erwerben"]`), also stored opaquely. They were widened from
  `VARCHAR(255)` to `TEXT` in `2026/07/21-01-changelog.json` so multi-meaning entries are not
  truncated.
- Deleting a dictionary also deletes its words in the service layer (no DB-level FK).
- **No security** on any endpoint yet (task P3-03). Do not expose publicly until then.
