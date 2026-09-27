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
  snapshot reconciliation fed by ms_dictionary events (P4-03), private → hidden (P4-04),
  deletion and `user.deleted` (P4-05), browse API (P4-06). Import endpoint + `dictionary.imported`
  next (P4-07).

## Entities
- `DictionaryStats` (`dictionary_stats`) — `dictionaryId` (PK, ms_dictionary's id, no DB FK),
  `userId` (`fk_user_id`, the JWT subject), `name`, `fromLang`, `toLang`, `importCount`,
  `publishedAt`, `sourceUpdatedAt`, `createdAt`/`updatedAt`. Migration `2026/09/27-01-changelog.json`.
  - `sourceUpdatedAt` is ms_dictionary's `updatedAt` — compare against it to drop stale events.
    `updatedAt` is only when this row was written. Do not mix them up.
  - `importCount` must be set to 0 explicitly on create; the column default does not apply through
    Hibernate.
  - `isListed` (`is_listed`, P4-04) — false means the dictionary went private. The row is kept on
    purpose as a stale-event guard; **browse must filter `is_listed = true`**. Set explicitly on
    create (Hibernate ignores column defaults). Migration `2026/09/27-02-changelog.json`.
  - `rating` / `viewCount` are deliberately absent until designed.

## Events (see `docs/agent/rabbitmq.md`)
- **Consumes (P4-03):** `common/listener/DictionaryEventListener` → `DictionaryStatsService`, one
  durable dead-lettered queue per event:
  - `dictionary.visibility.public` on `marketplace.dictionary.visibility.public` → `publishListing`
    — upsert on `dictionaryId`.
  - `dictionary.visibility.private` on `marketplace.dictionary.visibility.private` → `hideListing`
    (P4-04) — sets `isListed = false`, keeps the row, `importCount` and `publishedAt`. With no row
    yet it creates a hidden one, so a public event that was overtaken still compares as stale.
  - Every public path re-lists a hidden row when newer and resets `publishedAt`.
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
- The snapshot also removes hidden rows older than its `takenAt` — routine cleanup, so hidden rows
  do not accumulate. Only a manual DLQ replay of an old public event could then re-list one, until
  the next snapshot.
- `dictionary.deleted` on `marketplace.dictionary.deleted` → `hideDeletedListing` (P4-05) — hides,
  with the event's `eventTimestamp` as `sourceUpdatedAt` (a deleted dictionary has no `updatedAt`).
  No row → no-op: the event lacks the name/languages a hidden row needs.
- `user.deleted` (from ms_user) on `marketplace.user.deleted` → `UserEventListener` →
  `deleteListingsByUser` (P4-05) — **deletes** every row of the user, listed or hidden. Matches on the
  event's **`keycloakId`**, never its `userId` (ms_user's own key; matches nothing here). Deleted, not
  hidden, because ms_user's clock cannot be compared with ms_dictionary's `updatedAt`.
- **Publishes:** `dictionary.imported` (P4-07) — ms_user already has the queue bound. The payload is
  `{dictionaryId, keycloakId, eventTimestamp}`: the field is **`keycloakId`**, i.e. the caller's JWT
  subject.
- `RabbitMQConfig` mirrors the other services: same exchange, fanout DLX + DLQ, ISO-8601 converter
  with `INFERRED` type precedence (ms_dictionary's `__TypeId__` names classes that do not exist here).

## API — MarketplaceController (`/marketplace/dictionaries`, P4-06)
- `GET` (newest first) · `GET /popular` (most imported) · `GET /language?from=&to=` ·
  `GET /publisher/{publisherId}` — all paginated (`page` 0-based default 0, `size` default 20, max 100),
  all **listed rows only**. Contract table in `docs/agent/verborum.md`.
- Returns `common/response/PageResponse` — Verborum's own paging envelope. Reuse it for any future
  paged read rather than returning Spring's `Page`.
- Every sort ends in `dictionaryId` so ties are stable across pages; the sorts live as constants in
  `DictionaryStatsServiceImpl`.
- Parameter constraints (`@Min`/`@Max`, `@SupportedLanguage`) run via Spring MVC's built-in method
  validation → `HandlerMethodValidationException` → 400. **Do not add class-level `@Validated`** —
  that switches to AOP validation and a `ConstraintViolationException` nobody handles.
- `SupportedLanguage` here has `@Constraint` and its validator returns false. The copies in
  ms_dictionary/ms_user are inert (P4-09) — do not copy from them.
- Language codes are stored and returned **uppercase** (normalized on write, `Locale.ROOT`).
- `publisherId` = the owner's JWT subject (`fk_user_id`). Safe to expose; no display name (BL-04).

## Security
- `common/config/SecurityConfig.java` is in place from the first commit: stateless JWT resource
  server, `/actuator/**` and Swagger permitted, everything else requires authentication.
- `SecurityUtils.getCurrentUserId()` returns the JWT subject — the same value ms_dictionary stores
  as `fk_user_id` and ms_user stores as `keycloak_id`.

## Service-specific quirks
- The listing's `name`/`fromLang`/`toLang` are a **copy**. They are kept current by events, not by
  reading ms_dictionary; if they drift, the fix is the event or the reconciliation job, not a
  synchronous call.
