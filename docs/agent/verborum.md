# Verborum — Project Knowledge

## What Is Verborum?

A language learning app where users:
- Create personal **dictionaries** (a named word pair collection with a source and target language)
- Add **words** to dictionaries (word + translation + metadata for each)
- Share dictionaries publicly on a **Marketplace**
- (V2) Receive **word suggestions** powered by community translations

Each user provides their own translations — translations never overwrite each other.
The database stores dictionaries which have user id as foreign key and words which have
dictionary id as foreign key.

---

## System Architecture

```
Mobile Client
     │
     ▼
API Gateway          ← single entry point for all mobile requests
     │
     ├──► User Service        (auth, profile, vault, stats)
     ├──► Dictionary Service  (dictionaries + words CRUD)
     └──► Marketplace Service (public dictionaries, stats, ratings)

Web App ──► User Service      (sign-up / login flow only)

RabbitMQ                      ← async inter-service events

Keycloak                      ← identity server
Google Sign In                ← SSO provider

V2:
API Gateway ──► Autofil Service  (word suggestions from community data, NoSQL)
```

---

## Services

### ms_dictionary — SECURED (authentication + ownership)
- **Port:** 8085
- **DB:** `vdbdictionary` (PostgreSQL)
- **Base package:** `de.coldtea.verborum.msdictionary`
- **What it does:** Full CRUD for Dictionaries and Words
- **Docker:** `ms_dictionary/docker-compose.yml` (Postgres + Adminer)
- **Security:** Keycloak JWT required on every endpoint as of P3-03; the owner comes from the token
  subject, and acting on another user's data is refused (P3-05, P3-08). `/actuator/**` (health+info
  only) and Swagger are open.

### ✅ ms_user — PHASE 2 COMPLETE, SECURED, VERIFIED OVER HTTP
- **Port:** 8086
- **DB:** `vdbprofile` (PostgreSQL) — docker-compose in `ms_user/` (Postgres 5433 + Adminer 8081)
- **Base package:** `de.coldtea.verborum.msuser`
- **What it does:** User profile, auth integration with Keycloak, Dictionary Vault, User Stats
- **Mirror:** Follow ms_dictionary structure exactly
- **Built so far:** `User`/`UserStats`/`VaultEntry` entities, the User REST API (`/users`), the Vault
  REST API (`/users/{userId}/vault`), RabbitMQ publishing `user.deleted` and consuming
  `dictionary.imported`, and Keycloak realm-role mapping (roadmap P2-03…P2-11 — all of Phase 2)
- **Verified end-to-end 2026-07-23** (after P3-01/P3-02 brought Keycloak up): every `/users` and
  `/users/{userId}/vault` endpoint exercised over HTTP with a real Keycloak token — including
  401 without a token, 404s, validation 400s, idempotent vault POST, and `DELETE /users/{id}`
  publishing `user.deleted` on the wire.

### 🚧 ms_marketplace — SCAFFOLDED (P4-01), ENTITIES/ENDPOINTS TO BE BUILT
- **Port:** 8087
- **DB:** `vdbmarket` (PostgreSQL) — host port 5434; docker-compose in `ms_marketplace/` (Postgres
  5434 + Adminer 8082), and `db_market` in the root compose
- **Base package:** `de.coldtea.verborum.msmarketplace`
- **What it does:** Public dictionary listings, stats, ratings, user market imports
- **Security requirement:** Must be built with Spring Security + Keycloak JWT validation
  from the start — do not scaffold without it. See `docs/agent/security.md`.

### ❌ ms_autofil — V2, NOT STARTED
- **DB:** NoSQL (TBD — MongoDB or Redis)
- **What it does:** Returns top community translations for a given word + language pair
- Populated by consuming `word.created` events from RabbitMQ going forward
- **Security requirement:** Must be built with Spring Security + Keycloak JWT validation
  from the start — do not scaffold without it. See `docs/agent/security.md`.

---

## Domain Model

### Dictionary (`dictionaries` table in ms_dictionary)
```
dictionary_id   VARCHAR PK   UUID string, provided by client
fk_user_id      VARCHAR      UUID of owning user (no DB-level FK — cross-service ref)
name            VARCHAR      Display name
is_public       BOOLEAN      Whether visible on Marketplace
from_lang       VARCHAR      Language code e.g. "EN"
to_lang         VARCHAR      Language code e.g. "DE"
creation_dt     TIMESTAMPTZ  Auto-set by Hibernate @CreationTimestamp; JSON key createdAt
update_dt       TIMESTAMPTZ  Auto-set by Hibernate @UpdateTimestamp; JSON key updatedAt
```

### DictionaryTag (`dictionary_tags` table in ms_dictionary)
```
tag_id              VARCHAR PK   UUID string, SERVER-generated (not client-supplied)
fk_dictionary_id    VARCHAR      Real DB FK → dictionaries(dictionary_id) ON DELETE CASCADE
tag                 TEXT         Normalised: trimmed and lower-cased; no length limit
creation_dt         TIMESTAMPTZ  Auto-set by Hibernate; JSON key createdAt
```
A dictionary carries many tags, one row each. **UNIQUE (fk_dictionary_id, tag)** keeps a
dictionary's tags a set, which also makes the add endpoint idempotent.

Added 2026-07-23. Purpose: marketplace discovery (browse by topic) and the later AI work that
predicts which words a user will meet — so tags are grouping keys, not display text. That is why
they are normalised on the way in: `"Food"`, `"food "` and `"FOOD"` must aggregate as one tag. A
client that wants a pretty label renders it itself.

**This is the one place in ms_dictionary with a real DB-level FK.** `word → dictionary` has none
because words are split-ready; a tag is a same-service satellite with no independent life, so the FK
is the correct model and the cascade is free and correct. Deleting a dictionary — directly, or via
the `user.deleted` cascade — removes its tags at the database.

### Word (`words` table in ms_dictionary)
```
word_id             VARCHAR PK   UUID string, provided by client
fk_dictionary_id    VARCHAR      UUID of parent dictionary (no DB-level FK)
word                TEXT         JSON array of per-meaning surface forms (see contract below)
word_meta           JSON         JSON object (see contract below)
translation         TEXT         JSON array of per-meaning surface forms (same contract as word)
translation_meta    JSON         Same JSON contract as word_meta
level               INT          Per-user mastery level (nullable; client-owned) — see note below
creation_dt         TIMESTAMPTZ  Auto-set by Hibernate @CreationTimestamp; JSON key createdAt
update_dt           TIMESTAMPTZ  Auto-set by Hibernate @UpdateTimestamp; JSON key updatedAt
```

**`level`** is the per-user practice/mastery of a word, mirroring the mobile client's local `level`.
Client-owned and stored opaquely. **Nullable and optional** on upload: a client that predates the
field simply omits it (backend stores `null`; treat `null` as `0` client-side). Sent on `POST`/`PUT`
`/words` (in each `WordRequestDTO`) and returned on reads. It is deliberately *not* on the
`word.created` event (autofil counts translations, not mastery).

**Timestamps are zone-aware.** `creation_dt`/`update_dt` are `timestamptz`, exposed on read DTOs as
JSON keys **`createdAt`** / **`updatedAt`** (renamed from the earlier `creationTimestamp`/
`updateTimestamp`), serialized as ISO-8601 with a zone — normalized to UTC, e.g.
`"2026-07-21T09:34:42.622774Z"`. Server-authoritative: Hibernate sets them, they are ignored on
write, and only appear on GET reads. (The `Response`/`ErrorResponse` envelope `timestamp` is a
separate field and carries the server's local offset, e.g. `+04:00` — do not confuse the two.)

**Word / translation content contract** (canonical; owned by the clients, stored opaquely by the
backend — full spec in `docs/integration/frontend-backend-integration.md` §4.2 and the Android
doc §4). `word` and `translation` each hold a **JSON array of per-meaning surface forms** as a
string — one entry per meaning, article included where the language composes one:
`["der Apfel"]`, `["kaufen","erwerben"]`, `["l'eau"]`. Blank meanings are dropped.

`word_meta` / `translation_meta` each hold **one JSON object**:
```json
{
  "lang": "de",           // lowercase two-letter code of this side's language
  "type": "verb",         // part of speech; absent for free text
  "genders": ["m", ""],   // codes m/f/n/c, index-aligned to the surfaces array; omitted if none
  "fields": {             // grammatical form key -> list of values, index-aligned per meaning
    "past": ["kaufte", "erwarb"],
    "aux": ["haben", "haben"]
  }
}
```
All lists are **index-aligned** to the surfaces array; keys empty in every meaning are omitted;
**unknown keys must be ignored** (schema-evolution rule). Field keys in use: `reading, plural,
feminine, comparative, superlative, present, past, past3, participle, aux, aspect, root, stem,
measure, class, polite`.

> The earlier `partOfSpeech` / `example` / `notes` shape documented here was a placeholder written
> before the client schema existed. No client ever used it. The contract above is the real one.

### User Profile (ms_user — built)
```
- User           (user_id, keycloak_id, email, display_name, marketplace_agreement_accepted,
                  marketplace_agreement_version, marketplace_agreement_accepted_at, creation_dt, update_dt)
- UserStats      (user_id, total_words, total_dictionaries, update_dt)
- VaultEntry     (vault_entry_id, fk_user_id, fk_dictionary_id, imported_at)  ← imported public dictionaries
```
Column-level detail, constraints and quirks (the cross-service user key is `keycloak_id`, not
`user_id`) are in `ms_user/CLAUDE.md`.

### DictionaryStats (`dictionary_stats` table in ms_marketplace)
```
- dictionary_id      VARCHAR(255) PK   ← ms_dictionary's id; no DB FK (other service's database)
- fk_user_id         VARCHAR(255)      ← owner's JWT subject (ms_user's keycloak_id)
- name, from_lang, to_lang VARCHAR(255) ← copies, kept current by events + nightly snapshot
- lang_pair          VARCHAR(255)      ← both codes alphabetical (DE-TR for either direction); derived, P4-11
- tags               VARCHAR[]         ← the dictionary's tags, normalised + sorted; GIN-indexed; P4-12
- is_listed          BOOLEAN, default true ← false = went private; row kept as a stale-event guard
- import_count       INT, default 0
- published_at       timestamptz       ← from the event, not the insert
- source_updated_at  timestamptz       ← ms_dictionary's updatedAt; rule-4 stale-event guard
- creation_dt / update_dt timestamptz
```
### Publisher (`publishers` table in ms_marketplace, P4-13)
```
- keycloak_id        VARCHAR(255) PK   ← the JWT subject = dictionary_stats.fk_user_id; no FK
- display_name       VARCHAR(255)      ← ms_user's display name, trimmed; null = none → listings hidden
- marketplace_agreement_accepted BOOLEAN ← P4-14; false = not accepted / withdrawn → listings hidden
- source_updated_at  timestamptz       ← ms_user's updatedAt; rule-4 stale-event guard
- creation_dt / update_dt timestamptz
GIN (lower(display_name) gin_trgm_ops)  ← the case-insensitive substring name filter
```
### DictionaryImport (`dictionary_imports` table in ms_marketplace, P4-07)
```
- import_id          VARCHAR(255) PK   ← server-generated
- fk_dictionary_id   VARCHAR(255)      ← real FK to dictionary_stats, ON DELETE CASCADE
- fk_user_id         VARCHAR(255)      ← the importer's JWT subject; indexed
- imported_at        timestamptz
UNIQUE (fk_dictionary_id, fk_user_id)   ← import_count counts unique importers
```

A read model, not a source of truth. Browse must filter `is_listed = true`. `rating` /
`view_count` are not built yet (undesigned).

---

## API Contracts

### ms_dictionary — DictionaryController (`/dictionaries`)
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/dictionaries/` | `DictionaryRequestDTO` | `Response` (201) |
| PUT | `/dictionaries/` | `DictionaryRequestDTO` | `Response` (201) |
| DELETE | `/dictionaries/{dictionaryId}` | — | `Response` (200) |
| GET | `/dictionaries/{userId}` | — | `List<DictionaryResponseDTO>` |
| GET | `/dictionaries/dictionary/{dictionaryId}` | — | `DictionaryResponseDTO` (404 if not found) |
| GET | `/dictionaries/batch?ids=id1,id2` | — | `List<DictionaryResponseDTO>` (empty list for no matches) |

Note: DELETE `/dictionaries/{dictionaryId}` also deletes all words of that dictionary (no DB-level FK)
and all its tags (DB-level FK cascade).

**Marketplace sharing (P4-16):** joining the marketplace makes all of the user's dictionaries public,
leaving makes them all private (driven by `user.profile.updated`). A member who has dictionaries must
keep at least one public: making the last one private, deleting the last public one while private ones
remain, or creating a private one while none is public → **400** `SharingRequiredException`. Deleting
the user's last dictionary is allowed.

### ms_dictionary — DictionaryTagController (`/dictionaries/{dictionaryId}/tags`)
| Method | Path | Body | Returns |
|---|---|---|---|
| GET | `/dictionaries/{dictionaryId}/tags` | — | `List<DictionaryTagResponseDTO>` (404 if the dictionary is not yours) |
| POST | `/dictionaries/{dictionaryId}/tags` | `{"tag": "travel"}` | `Response` (201; idempotent — re-adding returns the existing tag) |
| DELETE | `/dictionaries/{dictionaryId}/tags/{tag}` | — | `Response` (200; removing an absent tag is a no-op) |

Tags are a **separate endpoint on purpose**: tagging must not require re-sending — or racing with —
the whole dictionary payload. The tag in the path is normalised the same way as on write, so
`DELETE .../tags/Travel` removes what `POST {"tag":"travel"}` stored.

### ms_dictionary — WordController (`/words`)
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/words` | `List<WordBundleRequestDTO>` | `Response` (201) |
| PUT | `/words` | `List<WordBundleRequestDTO>` | `Response` (201) |
| DELETE | `/words/{wordId}` | — | `Response` (200) |
| DELETE | `/words/dictionary/{dictionaryId}` | — | `Response` (200) |
| GET | `/words/dictionary/{dictionaryId}` | — | `List<WordResponseDTO>` |
| GET | `/words/language/from/{language}` | — | `List<WordResponseDTO>` |
| GET | `/words/language/to/{language}` | — | `List<WordResponseDTO>` |
| GET | `/words/user/{userId}` | — | `List<WordResponseDTO>` |
| GET | `/words/batch?ids=id1,id2` | — | `List<WordResponseDTO>` (empty list for no matches) |

### ms_user — UserController (`/users`)
Owner only: every call acts on the token subject's own profile (P3-05).

| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/users/` | `UserRequestDTO` `{userId, keycloakId, email, displayName?}` | `Response` 201 — create after first login. **409** `ProfileConflictException` for a second profile of the same account or an email another profile uses (P4-15) |
| PUT | `/users/` | same | `Response` 201 — full profile. An omitted `displayName` is **kept**, not cleared (P4-14); the agreement is never changed here |
| GET | `/users/me` | — | `ProfileResponseDTO` `{id, email, displayName, marketplaceAgreementAccepted, marketplaceAgreementVersion}` — by token subject, for the profile page after login; **404 = no profile yet**, create it with POST (P4-14) |
| PUT | `/users/me/profile-info` | `{displayName?, marketplaceAgreementAccepted?, marketplaceAgreementVersion?}` | `Response` 201 — partial: absent fields unchanged; `""` removes the name (P4-14) |
| GET | `/users/{userId}` | — | `UserResponseDTO` (incl. the two agreement fields) |
| DELETE | `/users/{userId}` | — | `Response` 200 — deletes everything, publishes `user.deleted` |

Profile rules (P4-14), both PUTs, 400 `InvalidProfileException`: accepting needs a non-blank
`displayName` and a `marketplaceAgreementVersion`; while accepted, the name cannot be removed — withdraw
(`false`) first, or in the same request. `displayName` ≤ 255, version ≤ 50. Withdrawing makes all
of the user's dictionaries private and joining makes them all public (ms_dictionary, P4-16); only
deleting the account deletes them. Any unique-constraint
violation in ms_user is a 409 with a fixed message (P4-15), never a 500.

### ms_marketplace — MarketplaceController (`/marketplace/dictionaries`, P4-06, P4-11..P4-15)
Read-only browse, Forum members only (display name + accepted terms; 403 otherwise, P4-15). Every endpoint returns only **listed** dictionaries and
takes `page` (zero-based, default 0, ≥ 0) and `size` (default 20, 1–100); anything else is a 400.

| Method | Path | Returns |
|---|---|---|
| GET | `/marketplace/dictionaries?pair=EN-TR&pair=FR-DE&tag=food&publisher=nna` | `SliceResponse<DictionaryListingResponseDTO>` — newest first. `pair` optional, repeatable or comma-separated, max 10; **direction ignored** (`EN-TR` → EN→TR and TR→EN); any case; 400 on an unsupported code, the same code twice or a malformed pair. `tag` optional, repeatable, max 10, each non-blank and ≤ 100 chars, any case; **any** of them matches (P4-12). `publisher` optional, 3–255 chars: part of the publisher's display name, any case — `nna` finds "Anna Bauer" (P4-13). Different filters are AND-ed |
| GET | `/marketplace/dictionaries/popular?pair=...&tag=...&publisher=...` | same — most imported first, newest first among equals; same filters |
| GET | `/marketplace/dictionaries/publisher/{publisherId}` | same — one publisher's listings, newest first; unknown id → empty slice |

```
SliceResponse                { items, page, size, hasNext }
DictionaryListingResponseDTO { dictionaryId, publisherId, publisherName, name, fromLang, toLang, tags, importCount, publishedAt }
```
`SliceResponse` is Verborum's own paging envelope for infinite scroll — no totals, so no count query
(P4-11 replaced `PageResponse`). `GET /language?from=&to=` was removed at P4-11; use `pair`.
`publisherId` is the owner's JWT subject — the value for the publisher endpoint; it grants no access
(ownership always comes from the caller's token). **The Forum is for members both ways** (P4-13..P4-15):
the caller must have a display name and accepted terms — otherwise **403** on every browse endpoint and
on import — and only listings whose publisher is a member are returned or importable (a non-member's
listing is a 404 on import). `publisherName` carries the name. Language codes come back
uppercase; `tags` lowercase and sorted (`[]` when untagged).

| Method | Path | Returns |
|---|---|---|
| POST | `/marketplace/dictionaries/{dictionaryId}/import` | `Response` (201) — P4-07. 403 if the caller is not a Forum member (P4-15); 404 if unknown, private or deleted, or its publisher is not a member (P4-15); 400 (`SelfImportException`) for your own. Idempotent: a repeat is 201 again and counts nothing |

Import records `(dictionary, importer)` once in `dictionary_imports`, increments `import_count` only
on a first import (unique importers), and publishes `dictionary.imported` on every successful call.
The importer can open the dictionary only once public dictionaries are readable in ms_dictionary
(P4-10).

---

## Supported Languages
19 codes: `EN, DE, FR, ES, IT, PT, NL, TR, AZ, LT, PL, UK, AR, FA, JA, ZH, KO, EL, RU`
Configured in `application.properties` (both services) as
`supported.languages=EN,DE,FR,ES,IT,PT,NL,TR,AZ,LT,PL,UK,AR,FA,JA,ZH,KO,EL,RU`
Validated via custom `@SupportedLanguage` annotation + `SupportedLanguageValidator`.
The validator uppercases before matching, so clients may send lowercase codes (e.g. `de`).
This is the single source of truth — every client's language enum must be a subset of it.

---

## RabbitMQ Events

**Exchange:** `verborum.events` (type: `topic`)

| Routing Key | Published by | Consumed by | Trigger |
|---|---|---|---|
| `dictionary.visibility.public` | ms_dictionary | ms_marketplace (`marketplace.dictionary.visibility.public`) | `is_public` set to true |
| `dictionary.visibility.private` | ms_dictionary | ms_marketplace (`marketplace.dictionary.visibility.private`) | `is_public` set to false |
| `dictionary.deleted` | ms_dictionary | ms_marketplace (`marketplace.dictionary.deleted`) | Dictionary deleted |
| `dictionary.updated` | ms_dictionary | ms_marketplace (`marketplace.dictionary.updated`) | A public dictionary's `name`/`fromLang`/`toLang` changed and it stayed public, or a tag was actually added to/removed from it (P4-12; bumps its `updatedAt`) |
| `dictionary.snapshot` | ms_dictionary | ms_marketplace (`marketplace.dictionary.snapshot`) | Schedule, nightly by default (`DICTIONARY_SNAPSHOT_CRON`) — every public dictionary in one message |
| `user.deleted` | ms_user | ms_dictionary (`dictionary.user.deleted`), ms_marketplace (`marketplace.user.deleted`) | User account deleted |
| `user.profile.updated` | ms_user | ms_marketplace (`marketplace.user.profile.updated`), ms_dictionary (`dictionary.user.profile.updated`, P4-16: joining shares all the user's dictionaries, leaving makes them all private) | A user's display name was set, changed or cleared (P4-13), or the marketplace agreement accepted or withdrawn (P4-14). `{keycloakId, displayName, marketplaceAgreementAccepted, updatedAt, eventTimestamp}`; `displayName: null` = no name now |
| `dictionary.imported` | ms_marketplace | ms_user | User imports a public dictionary |
| `word.created` | ms_dictionary | ms_autofil (V2) | New word added |

**Dead letter infrastructure:** `verborum.events.dlx` (type: `fanout`) → queue `verborum.dead-letter`.
Consumer queues are declared with `x-dead-letter-exchange` pointing at it, so a listener that keeps
throwing sends the message here after the configured retries rather than redelivering forever.
The DLX is a fanout on purpose — dead-lettered messages keep their original routing key, which a
direct DLX would fail to match and drop. See `docs/agent/rabbitmq.md`.

**User-identifying events carry `keycloakId`.** This applies to `user.deleted` (below) and to
`dictionary.imported`, whose payload is `{dictionaryId, keycloakId, eventTimestamp}` — fixed by the
P2-09 consumer, and published by ms_marketplace since P4-07. ms_marketplace and ms_dictionary only
ever see the JWT subject; ms_user's `user_id` is private to ms_user, which resolves
keycloakId → user_id on the way in.

**`user.deleted` carries both `userId` and `keycloakId`.** ms_dictionary and ms_marketplace store the
JWT subject in `fk_user_id`, and that value is ms_user's `keycloak_id`, not its `user_id` — so a
consumer cascading a user deletion must match on **`keycloakId`**. Matching on `userId` deletes
nothing and reports success. Added at P2-08; `rabbitmq.md`'s minimal sample payload was wrong.

**Current wiring state (2026-07-16, updated 2026-07-23):** Phase 1 is complete. ms_dictionary declares the exchange and
the dead letter infrastructure (roadmap P1-02) and publishes every event it owns:
`dictionary.visibility.public` / `dictionary.visibility.private` (P1-03), `dictionary.deleted`
(P1-04) and `word.created` (P1-05). Nothing consumes any of them yet — ms_marketplace and
ms_autofil do not exist, and a topic exchange discards a message with no bound queue, so these are
fire-and-forget until P4-03. ms_dictionary has no consumer queue until it starts consuming
`user.deleted` (P2-10). All services declare the same exchange; declarations are idempotent, so
whichever service starts first creates it.
As of 2026-07-23 (P2-08, P2-09) ms_user is wired too: same exchange and dead letter infrastructure,
publishing `user.deleted` and consuming `dictionary.imported` on the durable queue
`user.dictionary.imported`. ms_marketplace publishes `dictionary.imported` since P4-07 (verified
end-to-end: marketplace import → vault entry).

As of P2-10 ms_dictionary consumes `user.deleted` on the durable queue `dictionary.user.deleted` and
cascade-deletes that user's dictionaries and words — **matching on the event's `keycloakId`**, since
`fk_user_id` is the JWT subject. It publishes no `dictionary.deleted` for the cascaded rows, because
ms_marketplace consumes `user.deleted` itself. Verified live end-to-end: `DELETE /users/{userId}` on
ms_user removes the user's dictionaries and words from ms_dictionary, and a redelivery is a no-op.

As of P4-03 (2026-09-27) ms_marketplace consumes `dictionary.visibility.public`,
`dictionary.updated` and `dictionary.snapshot` into its `dictionary_stats` read model — one queue
each, all dead-lettered. Publish-to-listing updates, idempotent and stale-safe by `updatedAt`:
`visibility.public` upserts; `updated` only updates an existing listing (never creates, so it cannot
re-list a dictionary already made private or deleted); `snapshot` reconciles the whole table —
creates missing, corrects stale, and removes listings absent from it **whose own state predates the
snapshot's `takenAt`**. Verified live end-to-end.
As of P4-04 `dictionary.visibility.private` is consumed too: it **hides** the listing
(`is_listed = false`) instead of deleting it, so the row's `source_updated_at` keeps rejecting older
public state; every public path re-lists a hidden row when newer.
As of P4-05 `dictionary.deleted` hides the listing too, using the event's `eventTimestamp` as the
ordering key (there is no `updatedAt` for a deleted dictionary), and `user.deleted` deletes every row
of that user — matched on **`keycloakId`**. The snapshot removes hidden rows once it confirms them
gone.

**Consuming services must set `INFERRED` type precedence on the message converter.**
`Jackson2JsonMessageConverter` writes the publisher's fully-qualified class name into a `__TypeId__`
header and trusts it on the way in — which cannot work across services, where that class does not
exist and every message fails as ClassNotFound straight to the DLQ. With `INFERRED`, the
`@RabbitListener` parameter type wins and services only agree on JSON field names. Done in ms_user
(P2-09); required in ms_dictionary at P2-10. See `docs/agent/rabbitmq.md`.

**Every ms_dictionary event fires on change only, never on a plain re-save.** `saveDictionary()`
and `saveWords()` each back both POST and PUT, so both compare against stored state first:
visibility events fire only when `is_public` flips, and `word.created` only for word ids that did
not already exist. Without this, a rename would give ms_marketplace a duplicate listing and an edit
would make ms_autofil double-count a translation. The trade-off is that edits are invisible to
consumers — see the P1-03 and P1-05 notes in `roadmap.md` for the two gaps this leaves.

**All events are published AFTER the transaction commits** (2026-07-23, rule 1 in `rabbitmq.md`).
Services raise a Spring `OutboundEvent` and a `@TransactionalEventListener(AFTER_COMMIT)` sends it.
Publishing inside the transaction meant a rollback could announce something that never happened —
and since ms_dictionary reacts to `user.deleted` by deleting data, that phantom event destroyed live
rows. The trade is deliberate: an event can now be lost if the process dies in the gap, which leaves
recoverable orphans instead.

**`DictionaryVisibilityEvent` carries the dictionary's `updatedAt`** as an ordering key. A consumer
building a projection must ignore an event that is not newer than the state it already holds; out-of
-order delivery of two quick edits would otherwise leave a permanently stale listing.

**Event payloads are JSON with ISO-8601 timestamps** (`"eventTimestamp":"2026-07-16T15:38:13.85"`).
This is a wire contract, not a local preference — every service's `RabbitMQConfig` must build its
`Jackson2JsonMessageConverter` the same way or publishers and consumers will disagree on the
timestamp format. See `docs/agent/rabbitmq.md`.

**Visibility events fire on change only.** `DictionaryServiceImpl.saveDictionary()` backs both POST
and PUT, so it compares `is_public` against the stored row and publishes only on an actual flip.
Re-saving a public dictionary (a rename) publishes nothing — otherwise ms_marketplace would create
a duplicate listing. The flip side: a renamed public dictionary sends no event, so a marketplace
listing's `name` can go stale. See the P1-03 note in `roadmap.md`.

See `docs/agent/rabbitmq.md` for implementation details.

---

## Known Issues in ms_dictionary

1. ~~No security on any endpoint~~ — resolved 2026-07-23. P3-03 added JWT validation, P3-05 made the
   owner come from the token (403 on a mismatch), P3-08 closed the id-addressed holes, and P3-06
   restricted actuator exposure to `health,info`.
   **Identity rule, worth repeating:** `fk_user_id` is the JWT subject. In ms_user that same value is
   the `keycloak_id` column, not its `user_id`. Ownership checks and event consumers must use it.

2. **`@GenericGenerator` imported but unused** in entity files (deprecated in newer Hibernate).

---

## Configuration Conventions

Each service has its own `application.properties`. Pattern:
```properties
server.port=<port>
spring.datasource.url=jdbc:postgresql://localhost:5432/<dbname>
spring.datasource.username=coldtea
spring.datasource.password=qwerty
spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.json
supported.languages=EN,DE,FR,ES,IT,PT,NL,TR,AZ,LT,PL,UK,AR,FA,JA,ZH,KO,EL,RU
```

Each service has its own `docker-compose.yml` with a Postgres + Adminer setup, for working on
that service in isolation.

The root `docker-compose.yml` brings up the whole local backend in one command: RabbitMQ
(5672, Management UI on 15672, `verborum`/`verborum`), `vdbdictionary` on 5432, `vdbprofile`
on 5433, Keycloak on 8180 (`admin`/`admin`), and a single Adminer on 8080. It binds the same host
ports as the per-service files, so run the root compose or a service compose — never both at once.

Keycloak's realm is imported from `keycloak/import/verborum-realm.json` (versioned, not
hand-clicked) and only on the first start of an empty `keycloak_data` volume — see
`docs/agent/security.md` for the re-import command and the full auth contract. Quick local token:
```
curl -s -X POST http://localhost:8180/realms/verborum/protocol/openid-connect/token \
  -d client_id=verborum-dev-cli -d username=testuser -d password=testuser -d grant_type=password
```
