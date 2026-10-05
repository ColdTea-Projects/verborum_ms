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
- `DictionaryTag` (`dictionary_tags`) — `tagId` (**server-generated**), `dictionaryId`
  (fk_dictionary_id), `tag` (`TEXT`, no length cap), `createdAt`. Many per dictionary. Migrations
  `2026/07/23-01-changelog.json` (table) and `-02` (widened `tag` from `VARCHAR(50)` to `TEXT`).
  For marketplace discovery and the later AI word-prediction work.

## Events
- **Publishes after commit, never inside the transaction** (rule 1 in `docs/agent/rabbitmq.md`).
  Services raise an `OutboundEvent` application event; `OutboundEventPublisher` is the only class
  here that touches `RabbitTemplate`. Unit tests verify `ApplicationEventPublisher`. The rollback
  guarantee is proven once, in ms_user's `UserDeletedAfterCommitTest` — both services share the
  listener.
- **Publishes:** `dictionary.visibility.public/private`, `dictionary.deleted`, `dictionary.updated`,
  `dictionary.snapshot`, `word.created`
- `dictionary.updated` (P4-03) fires only when a dictionary that was public **and stays public**
  changes `name`, `fromLang` or `toLang`. A visibility flip never also fires it. The old values are
  copied out *before* `saveAndFlush`, which merges the new values onto the managed instance
  `findById` returned — comparing after the save would always see "no change". A test simulates
  exactly that; keep it.
- **Tags ride on the listing events (P4-12):** `dictionary.visibility.*`, `dictionary.updated` and
  every snapshot entry carry `tags` — sorted, `[]` when untagged, never null (null tells the
  marketplace "unknown, keep yours"). A tag add/remove that actually changes a **public** dictionary
  calls `DictionaryService.publishTagChange`, which **bumps the dictionary's `updatedAt`** and sends
  `dictionary.updated`. The bump is load-bearing: the marketplace drops events not newer than what it
  holds (rule 4), and a tag write alone never touches `dictionaries.update_dt`. Re-adds, absent
  deletes and private dictionaries send nothing.
- **Consumes `user.profile.updated` (P4-16)** on `dictionary.user.profile.updated` →
  `MarketplaceMemberService.applyProfileUpdate`. Keeps `marketplace_members` (keycloakId, isMember,
  sourceUpdatedAt — rule 4) and acts only on a **transition**: joining → `setVisibilityOfAll(true)`,
  leaving → `setVisibilityOfAll(false)`, each changed dictionary saved and announced with
  `dictionary.visibility.*`. The membership row is saved before unsharing, so the sharing rule never
  blocks a leave. Events without `marketplaceAgreementAccepted` are ignored.
- **Sharing rule (P4-16):** a member who has dictionaries keeps at least one public —
  `requireMemberKeepsOneShared` on a save that hides a public dictionary or creates a private one, and
  on deleting a public one, checked on the result (re-saving an already-private dictionary and deleting
  a private one take nothing shared away and are not checked); `SharingRequiredException` → 400. Deleting the last dictionary outright is allowed.
- `dictionary.snapshot` (P4-03) — every public dictionary in one message, sent by
  `common/scheduler/DictionarySnapshotScheduler` on `dictionary.snapshot.cron`
  (`DICTIONARY_SNAPSHOT_CRON`, nightly 03:00 by default; `@EnableScheduling` in `SchedulingConfig`).
  `takenAt` is read **before** the query — ms_marketplace relies on that to avoid removing a listing
  made public mid-snapshot. Sent even when empty. Backed by `idx_dictionaries_is_public`
  (`2026/09/27-01-changelog.json`). Every instance runs the schedule: add a lock (ShedLock) before
  scaling out.
- `DictionaryVisibilityEvent` carries the dictionary's `updatedAt` as an ordering key (rule 4): the
  marketplace projection must drop an event older than the state it holds, or two quick edits
  delivered out of order leave the listing permanently stale.
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
- Deleting a dictionary also deletes its words in the service layer (no DB-level FK), and its tags
  via the DB-level FK cascade.
- **`dictionary_tags` is the one table here with a real FK** (`ON DELETE CASCADE` to `dictionaries`).
  That is deliberate and not a break with the `word → dictionary` convention: words are split-ready
  (they could move to their own service), a tag is a same-service satellite with no independent life.
  Same reasoning as `UserStats`/`VaultEntry` in ms_user.
- **Tags are normalised (trimmed + lower-cased) on write *and* on delete.** They are grouping keys
  for marketplace browse and the later AI aggregation, so `Food`/`food `/`FOOD` must be one tag. If a
  client ever needs the original casing for display, that is a new column, not a change here.
- Adding a tag is idempotent (`UNIQUE (fk_dictionary_id, tag)`); re-adding returns the existing row.
- **`GET /words/dictionary/{id}` is a by-id read (SEC-14):** 404 when the dictionary is absent or unreadable
  (`getWordsByDictionary`), never `200 []` — an importer must be able to tell "gone private" from "empty".
- **Request limits (SEC-07):** 5 bundles × 500 words per `POST`/`PUT /words`, 100 batch ids (`@Size`, constants in
  `DTOMessageConstants`); quotas 1,000 dictionaries/account and 5,000 words/dictionary on new rows only
  (`QuotaExceededException` → 400); `common/config/RequestBodyLimitFilter` caps bodies at 2 MB (413) and refuses
  chunked bodies (411) — it answers itself, because `/error` is behind the security chain.
- **A word never changes dictionary (SEC-01).** `saveWords` refuses (403 `WORD_IN_ANOTHER_DICTIONARY`) an
  existing `wordId` sent under a different `dictionaryId`. Before the fix the upsert moved — took over —
  another user's word into the caller's dictionary. Keep the check ahead of `saveAllAndFlush`.
- Tags follow their dictionary's ownership rules: writes on someone else's dictionary 403, reads 404
  unless the dictionary is public (P4-10).
- Authorization (P3-05/P3-08): services take an explicit `ownerId` — the token subject, passed in by
  the controller — and never trust an id from the body or path. Writes 403 on a mismatch; reads by id
  404 (so a caller cannot probe which ids exist); batch/list endpoints filter to the caller instead
  of refusing. `deleteAllByUserId` and the private delete helper are the exception: they are the
  `user.deleted` cascade, where the actor is ms_user rather than a logged-in caller.
- **Public dictionaries are readable by anyone authenticated (P4-10)** — by id, in batch, their words
  and tags — so a marketplace import (a vault reference) can be opened. One rule,
  `common/utils/DictionaryAccessUtils.isReadableBy` (owned or public), used by all three services;
  writes stay owner-only. Non-owners get a word's `level` as `null` (it is the owner's mastery).
  `getWordsByIds` now resolves the words' dictionaries in one batch query instead of one per word.
- **`@ValidUUID` / `@SupportedLanguage` validate since P4-09** (2026-09-27). Before, both lacked
  `@Constraint` and were silently ignored: `fromLang: "XX"`, non-UUID ids and `/words/language/from/XX`
  were all accepted. Validators return false (never throw); failures on a DTO are
  `MethodArgumentNotValidException`, on a path variable or inside the `POST /words` list body
  `HandlerMethodValidationException` — both 400. Before P4-09 a blank word in a bundle was a 500.
  Language codes are still stored as sent (lowercase allowed); the marketplace uppercases its copy.
- Security: `common/config/SecurityConfig.java` (P3-03) — stateless JWT resource server, `/actuator/**`
  and Swagger permitted, everything else authenticated. Realm roles are mapped by the hand-written
  `extractRealmRoles` (see the P2-11 note in `security.md`); do not swap in
  `JwtGrantedAuthoritiesConverter`. `jwk-set-uri` is set alongside `issuer-uri` so the service still
  starts when Keycloak is down.
