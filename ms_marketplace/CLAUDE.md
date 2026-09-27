# ms_marketplace — Service Guide

This file supplements the root `CLAUDE.md` and the `.claude/skills/`. It covers only what is
specific to this service. All conventions are inherited from the root — do not duplicate them.

## What this service does
Public dictionary listings, stats, ratings and imports. It serves browse entirely from its own
read model (`dictionary_stats`, fed by ms_dictionary events) and never calls ms_dictionary at
request time — decided 2026-07-23, see roadmap `P4-03`.

## Facts
- **Port:** 8087
- **DB:** `vdbmarket` (PostgreSQL) — docker-compose in this module (Postgres on 5434 + Adminer on
  8082), and `db_market` in the root compose on the same host port. Run one or the other.
- **Base package:** `de.coldtea.verborum.msmarketplace`
- **Status:** Scaffolded (P4-01), `dictionary_stats` table (P4-02), listing projection +
  snapshot reconciliation fed by ms_dictionary events (P4-03). No endpoints yet.

## Entities
- `DictionaryStats` (`dictionary_stats`) — `dictionaryId` (PK, ms_dictionary's id, no DB FK),
  `userId` (`fk_user_id`, the JWT subject), `name`, `fromLang`, `toLang`, `importCount`,
  `publishedAt`, `sourceUpdatedAt`, `createdAt`/`updatedAt`. Migration `2026/09/27-01-changelog.json`.
  - `sourceUpdatedAt` is ms_dictionary's `updatedAt` — compare against it to drop stale events.
    `updatedAt` is only when this row was written. Do not mix them up.
  - `importCount` must be set to 0 explicitly on create; the column default does not apply through
    Hibernate.
  - `rating` / `viewCount` are deliberately absent until designed.

## Events (see `docs/agent/rabbitmq.md`)
- **Consumes (P4-03):** `common/listener/DictionaryEventListener` → `DictionaryStatsService`, one
  durable dead-lettered queue per event:
  - `dictionary.visibility.public` on `marketplace.dictionary.visibility.public` → `publishListing`
    — upsert on `dictionaryId`.
  - `dictionary.updated` on `marketplace.dictionary.updated` → `updateListing` — **update-only,
    never creates**: an update for a missing listing may belong to a dictionary already made private
    or deleted, and creating it would re-list it. The snapshot restores a genuinely missed listing.
  - `dictionary.snapshot` on `marketplace.dictionary.snapshot` → `reconcile` — one transaction:
    create missing, correct stale, remove listings absent from the snapshot **only if their
    `sourceUpdatedAt` is before the snapshot's `takenAt`**. Logs a summary line; non-zero
    "created or corrected / removed" counts mean the event path lost something.
  - All three drop anything not strictly newer than the held `sourceUpdatedAt` (rule 4), so
    redeliveries and DLQ replays are no-ops. A missing `updatedAt` falls back to the event
    timestamp (snapshot: `takenAt`).
- **Known gap for P4-04:** a dictionary made private in the seconds between the snapshot query and
  `reconcile` is recreated by it until the next snapshot, because a removed listing leaves nothing to
  compare against. Keeping a hidden row with its `sourceUpdatedAt` on private (instead of deleting)
  would close it.
- **Not yet consumed:** `dictionary.visibility.private` (P4-04), `dictionary.deleted` (P4-05) and
  `user.deleted` — until then those removals arrive with the next snapshot.
- **Publishes:** `dictionary.imported` (P4-07) — ms_user already has the queue bound. The payload is
  `{dictionaryId, keycloakId, eventTimestamp}`: the field is **`keycloakId`**, i.e. the caller's JWT
  subject.
- `RabbitMQConfig` mirrors the other services: same exchange, fanout DLX + DLQ, ISO-8601 converter
  with `INFERRED` type precedence (ms_dictionary's `__TypeId__` names classes that do not exist here).

## Security
- `common/config/SecurityConfig.java` is in place from the first commit: stateless JWT resource
  server, `/actuator/**` and Swagger permitted, everything else requires authentication.
- `SecurityUtils.getCurrentUserId()` returns the JWT subject — the same value ms_dictionary stores
  as `fk_user_id` and ms_user stores as `keycloak_id`.

## Service-specific quirks
- The listing's `name`/`fromLang`/`toLang` are a **copy**. They are kept current by events, not by
  reading ms_dictionary; if they drift, the fix is the event or the reconciliation job, not a
  synchronous call.
