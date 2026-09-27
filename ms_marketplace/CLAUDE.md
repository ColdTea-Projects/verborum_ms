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
- **Status:** Scaffolded (P4-01) — an empty, running, secured shell. No entities, endpoints or
  RabbitMQ yet.

## Entities
None yet. `DictionaryStats` (`dictionary_stats`) arrives at P4-02, **with** its reconciliation job
(rule 6).

## Events (planned)
- **Consumes:** `dictionary.visibility.public` (P4-03), `dictionary.visibility.private` (P4-04),
  `dictionary.deleted` (P4-05), and a new `dictionary.updated` that ms_dictionary must start
  publishing (P4-03). Every consumer upserts on `dictionaryId` and drops anything whose `updatedAt`
  is not newer than what it holds.
- **Publishes:** `dictionary.imported` (P4-07) — ms_user already has the queue bound. The payload is
  `{dictionaryId, keycloakId, eventTimestamp}`: the field is **`keycloakId`**, i.e. the caller's JWT
  subject.
- `spring-boot-starter-amqp`, `RabbitMQConfig` (with the `INFERRED` type-precedence converter every
  consuming service needs) and the RabbitMQ properties are added at P4-03, not before.

## Security
- `common/config/SecurityConfig.java` is in place from the first commit: stateless JWT resource
  server, `/actuator/**` and Swagger permitted, everything else requires authentication.
- `SecurityUtils.getCurrentUserId()` returns the JWT subject — the same value ms_dictionary stores
  as `fk_user_id` and ms_user stores as `keycloak_id`.

## Service-specific quirks
- The listing's `name`/`fromLang`/`toLang` are a **copy**. They are kept current by events, not by
  reading ms_dictionary; if they drift, the fix is the event or the reconciliation job, not a
  synchronous call.
