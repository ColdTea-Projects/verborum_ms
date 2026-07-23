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
- **Status:** Functionally complete and secured — JWT required (P3-03), owner taken from the token
  (P3-05), and id-addressed endpoints ownership-checked (P3-08).

## Entities
- `Dictionary` (`dictionaries`) — `dictionaryId`, `userId` (fk_user_id), `name`, `isPublic`,
  `fromLang`, `toLang`, timestamps
- `Word` (`words`) — `wordId`, `dictionaryId` (fk_dictionary_id), `word`, `wordMeta` (json),
  `translation`, `translationMeta` (json), `level` (int, nullable), timestamps

## Events
- **Publishes:** `dictionary.visibility.public/private`, `dictionary.deleted`, `word.created`
- **Consumes:** `user.deleted` on the durable queue `dictionary.user.deleted` →
  `UserEventListener` → `DictionaryService.deleteAllByUserId` (P2-10 done)
  - **Cascades on the event's `keycloakId`, not its `userId`.** `fk_user_id` holds the JWT subject,
    which is ms_user's `keycloak_id`; using `userId` deletes nothing and reports success.
  - Words are deleted before dictionaries (no DB-level FK). An empty result short-circuits, so a
    redelivery is a harmless no-op.
  - Deliberately publishes **no** `dictionary.deleted` for the cascaded rows — ms_marketplace
    consumes `user.deleted` itself.
- **Cross-service JSON:** `RabbitMQConfig`'s converter uses `DefaultJackson2JavaTypeMapper` with
  `INFERRED` type precedence. Without it the publisher's `__TypeId__` header (an ms_user class that
  does not exist here) makes every inbound message fail as ClassNotFound. Do not remove it.

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
- `word.level` is the per-user mastery of a word (mirrors the mobile local `level`). Nullable and
  optional on upload so older clients keep working (`null` = not provided; treat as `0` client-side);
  stored opaquely. Added in `2026/07/21-02-changelog.json`. Not carried on `word.created`.
- Entity timestamps are `OffsetDateTime` over `timestamptz` columns (`2026/07/21-03-changelog.json`),
  serialized as ISO-8601 UTC (`...Z`) under JSON keys **`createdAt`** / **`updatedAt`** (the entity
  fields were renamed from `creationTimestamp`/`updateTimestamp`; DB columns stay `creation_dt`/
  `update_dt`). Server-authoritative. NB: the `Response`/`ErrorResponse` envelope `timestamp` is a
  different field and uses the server's local offset — see `docs/integration/…` §4.4.
- Deleting a dictionary also deletes its words in the service layer (no DB-level FK).
- Authorization (P3-05/P3-08): services take an explicit `ownerId` — the token subject, passed in by
  the controller — and never trust an id from the body or path. Writes 403 on a mismatch; reads by id
  404 (so a caller cannot probe which ids exist); batch/list endpoints filter to the caller instead
  of refusing. `deleteAllByUserId` and the private delete helper are the exception: they are the
  `user.deleted` cascade, where the actor is ms_user rather than a logged-in caller.
- Security: `common/config/SecurityConfig.java` (P3-03) — stateless JWT resource server, `/actuator/**`
  and Swagger permitted, everything else authenticated. Realm roles are mapped by the hand-written
  `extractRealmRoles` (see the P2-11 note in `security.md`); do not swap in
  `JwtGrantedAuthoritiesConverter`. `jwk-set-uri` is set alongside `issuer-uri` so the service still
  starts when Keycloak is down.
