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
  deletion and `user.deleted` (P4-05), browse API (P4-06), import + `dictionary.imported` (P4-07),
  language-pair filter + slices + browse indexes (P4-11), tag filter (P4-12), publisher display names
  + name filter (P4-13). The marketplace search plan (P4-11..P4-13) is complete. Ratings (P4-19..P4-22): 1–5 stars
  by importers, aggregates on the listing, `/top-rated`.

## Entities
- `DictionaryStats` (`dictionary_stats`) — `dictionaryId` (PK, ms_dictionary's id, no DB FK),
  `userId` (`fk_user_id`, the JWT subject), `name`, `fromLang`, `toLang`, `importCount`,
  `publishedAt`, `sourceUpdatedAt`, `createdAt`/`updatedAt`. Migration `2026/09/27-01-changelog.json`.
  - `sourceUpdatedAt` is ms_dictionary's `updatedAt` — compare against it to drop stale events.
    `updatedAt` is only when this row was written. Do not mix them up.
  - `langPair` (`lang_pair`, P4-11) — `fromLang`/`toLang` without direction, alphabetical. Derived
    in the service from the two languages; never set it on its own. Migration `2026/10/04-01`.
  - `tags` (`tags`, P4-12) — `String[]` over **`varchar[]`** (not `text[]`: Hibernate binds the filter
    array as `varchar[]` and Postgres has no `text[] && varchar[]`). Normalised + sorted on every write
    (`normalizeTags`). An event with **null** tags predates P4-12 → keep the held ones; `[]` clears.
    Set to `{}` explicitly on create. Migration `2026/10/04-03`.
  - `reconcile` also repairs **tags only** when a snapshot entry has the *same* `updatedAt` as the
    row but different tags (`isMissingTagsOfThisVersion`) — how listings stored before P4-12 get theirs.
  - `importCount` must be set to 0 explicitly on create; the column default does not apply through
    Hibernate.
  - `isListed` (`is_listed`, P4-04) — false means the dictionary went private. The row is kept on
    purpose as a stale-event guard; **browse must filter `is_listed = true`**. Set explicitly on
    create (Hibernate ignores column defaults). Migration `2026/09/27-02-changelog.json`.
  - `ratingCount` / `ratingSum` / `ratingScore` (P4-19) — aggregates of `dictionary_ratings`, changed only by
    `DictionaryStatsRepository.applyRatingChange` (one atomic UPDATE, `flushAutomatically` + `clearAutomatically`:
    without the flush a pending rating delete was discarded unwritten). `ratingScore` is the Bayesian average
    `(sum + 15) / (count + 5)` (`RatingScore`: m = 3, C = 5) that `/top-rated` sorts by; the listing shows
    `RatingScore.average` (sum/count, one decimal, null when unrated). Set to 0/0/3.0 explicitly on create.
    Migration `2026/10/05-02`. `viewCount` is still absent until designed.
- `Publisher` (`publishers`, P4-13) — `keycloakId` (PK, the JWT subject = `fk_user_id`), `displayName`
  (nullable, trimmed; blank stored as null), `marketplaceAgreementAccepted` (P4-14, NOT NULL, set
  explicitly; null in an event = pre-P4-14, keep held; new row without it = false), `sourceUpdatedAt`
  (ms_user's `updatedAt`, rule 4). Migrations `2026/10/04-04` and `-05`. Own
  package `publisher/`. Migration `2026/10/04-04-changelog.json` — also installs `pg_trgm` and the GIN
  trigram index on `lower(display_name)`. No row or a null name = that user's listings are hidden. A
  cleared name keeps the row (stale-event guard). No backfill: names set before P4-13 arrive only when
  the user next saves their profile.
- `DictionaryImport` (`dictionary_imports`, P4-07) — `importId` (server-generated), `dictionaryId`
  (real FK to `dictionary_stats`, ON DELETE CASCADE), `userId` (importer's subject), `importedAt`.
  UNIQUE (dictionary, user) — `import_count` counts unique importers. Migration
  `2026/09/27-05-changelog.json`. Own package `dictionaryimport/`.
- `DictionaryRating` (`dictionary_ratings`, P4-19) — `ratingId` (server-generated), `dictionaryId` (real FK to
  `dictionary_stats`, ON DELETE CASCADE: kept while hidden, gone with the listing), `userId` (rater's subject),
  `stars` (1–5, CHECK), timestamps. UNIQUE (dictionary, user). Migration `2026/10/05-01`. Own package
  `dictionaryrating/`. Only importers rate — checked against `dictionary_imports`, no call out (rule 5).

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
- **Publishes:** `dictionary.imported` (P4-07) from `DictionaryImportServiceImpl`, via
  `OutboundEvent` → `OutboundEventPublisher` after commit (the only class here touching
  `RabbitTemplate`). Payload `{dictionaryId, keycloakId, eventTimestamp}` — fixed by ms_user's P2-09
  consumer; **`keycloakId`** is the importer's JWT subject. Sent on every successful import, repeats
  included (the vault is idempotent; a re-send repairs a lost first event).
- `user.deleted` also deletes the user's import records; their earlier imports stay counted. Their **ratings** are
  removed first and the aggregates corrected (`DictionaryRatingService.deleteRatingsByUser`, P4-22) — a rating is an
  opinion, an import count is history.
- `user.profile.updated` (from ms_user, P4-13) on `marketplace.user.profile.updated` →
  `UserEventListener.handleUserProfileUpdated` → `PublisherService.updateDisplayName` — upsert on
  `keycloakId`, dropped unless newer than the held `sourceUpdatedAt`. `user.deleted` also deletes the
  `publishers` row; a late profile event can recreate it, harmlessly (no listings, subjects never reused).
- `RabbitMQConfig` mirrors the other services: same exchange, fanout DLX + DLQ, ISO-8601 converter
  with `INFERRED` type precedence (ms_dictionary's `__TypeId__` names classes that do not exist here).

## API — MarketplaceController (`/marketplace/dictionaries`, P4-06, P4-11)
- `GET` (newest first) · `GET /popular` (most imported) — both take the optional filters ·
  `GET /publisher/{publisherId}` — all paginated (`page` 0-based default 0, `size` default 20, max 100),
  all **listed rows only**. Contract table in `docs/agent/verborum.md`.
- Filters (P4-11): `pair=EN-TR` (repeatable, max 10, `@LanguagePair` per element). Direction is
  ignored via the `lang_pair` column — both codes alphabetical, set by `LanguagePairUtils` on **every**
  write path that sets the languages (keep it that way; the column is NOT NULL). `tag=food` (P4-12,
  repeatable, max 10): **any** match through `hasAnyTag` → Hibernate `arrayOverlaps` → `tags && ?`.
  `publisher=nna` (P4-13, 3–255 chars): case-insensitive substring of the display name, through
  `LikePatternUtils.toContainsPattern` (escapes `%`, `_`, `\`) and `hasActivePublisher(pattern)`.
- **The Forum gate (P4-15):** browse and import are for members only. `PublisherService.requireMember`
  (a member = a `publishers` row with a name and `marketplaceAgreementAccepted`) runs first in every
  browse method and in `importDictionary` → `ForbiddenOperationException` → **403**. Browse methods
  therefore take the caller's subject. Import also 404s a listing whose publisher is not a member
  (`isMember`), so leaving the marketplace cannot be bypassed with a remembered `dictionaryId`.
  Membership is this service's event-fed copy, so it lags a join by about a second.
- **Never the caller's own (P4-17):** `GET` and `/popular` add `isNotPublishedBy(callerId)`; the
  publisher endpoint does not (asking for your own id is deliberate).
- **`hasActivePublisher` is applied to every browse read, filter or not** — a listing whose publisher has
  no `publishers` row, a null name, or has not accepted the marketplace terms (P4-14) is never returned. `publisherName` on each listing comes from one
  `publisherRepository.findAllById` per page; the mapper ignores the field.
- Returns `common/response/SliceResponse` `{items, page, size, hasNext}` — infinite scroll, no count
  query. Reuse it for any future paged read. Browse goes through `findSlice` (the
  `DictionaryStatsSliceRepository` fragment, size + 1 rows), never `findAll(spec, pageable)`, which
  counts.
- `DictionaryStatsSpecifications.isListed()` must stay a literal (`isTrue`): the browse indexes are
  partial `WHERE is_listed`, and a bound parameter can stop Postgres from using them.
- Every sort ends in `dictionaryId` so ties are stable across pages; the sorts live as constants in
  `DictionaryStatsServiceImpl`.
- Parameter constraints (`@Min`/`@Max`, `@Size`, `@LanguagePair`) run via Spring MVC's built-in method
  validation → `HandlerMethodValidationException` → 400. **Do not add class-level `@Validated`** —
  that switches to AOP validation and a `ConstraintViolationException` nobody handles.
- `SupportedLanguage` and `ValidUUID` have `@Constraint` and validators that return false — the same
  in all three services since P4-09.
- Language codes are stored and returned **uppercase** (normalized on write, `Locale.ROOT`).
- `publisherId` = the owner's JWT subject (`fk_user_id`). Safe to expose; show `publisherName` instead.

- `POST /{dictionaryId}/import` (P4-07) — the one write. Importer = token subject. 404 hidden or
  unknown (never reveal a private dictionary exists), 400 `SelfImportException` for your own, 201
  otherwise and on repeats. The count increment is one atomic `@Modifying` UPDATE in
  `DictionaryStatsRepository` — never read-modify-write it in Java.
- `GET /top-rated` (P4-21) — same filters, `hasRatings()` (rated only), sorted `ratingScore` desc → `publishedAt` desc →
  `dictionaryId`; served by the partial index `idx_dictionary_stats_listed_top_rated`.
- `PUT|GET|DELETE /{dictionaryId}/rating` (P4-20) — `DictionaryRatingService`. PUT: member (403) → listed with an active
  publisher (404) → not your own (400 `SelfRatingException`) → imported (403) → create or change. GET: your rating or
  404. DELETE: 200, also when absent. No events — nothing else consumes ratings.

## Security
- `common/config/SecurityConfig.java` is in place from the first commit: stateless JWT resource
  server, `/actuator/**` and Swagger permitted, everything else requires authentication.
- `SecurityUtils.getCurrentUserId()` returns the JWT subject — the same value ms_dictionary stores
  as `fk_user_id` and ms_user stores as `keycloak_id`.

## Service-specific quirks
- The listing's `name`/`fromLang`/`toLang` are a **copy**. They are kept current by events, not by
  reading ms_dictionary; if they drift, the fix is the event or the reconciliation job, not a
  synchronous call.
