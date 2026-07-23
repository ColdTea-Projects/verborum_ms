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

### 🚧 ms_user — PHASE 2 COMPLETE, NOT YET EXERCISED OVER HTTP
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

### ❌ ms_marketplace — TO BE BUILT
- **Port:** TBD (suggest 8087)
- **DB:** `DB_Market` (PostgreSQL)
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

### User Profile (ms_user — to be designed)
```
Entities needed:
- User           (user_id, keycloak_id, email, display_name, created_at, updated_at)
- UserStats      (user_id, total_words, total_dictionaries, ...)
- VaultEntry     (user_id, dictionary_id, imported_at)  ← imported public dictionaries
```

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

Note: DELETE `/dictionaries/{dictionaryId}` also deletes all words of that dictionary (no DB-level FK).

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
| `dictionary.visibility.public` | ms_dictionary | ms_marketplace | `is_public` set to true |
| `dictionary.visibility.private` | ms_dictionary | ms_marketplace | `is_public` set to false |
| `dictionary.deleted` | ms_dictionary | ms_marketplace | Dictionary deleted |
| `user.deleted` | ms_user | ms_dictionary, ms_marketplace | User account deleted |
| `dictionary.imported` | ms_marketplace | ms_user | User imports a public dictionary |
| `word.created` | ms_dictionary | ms_autofil (V2) | New word added |

**Dead letter infrastructure:** `verborum.events.dlx` (type: `fanout`) → queue `verborum.dead-letter`.
Consumer queues are declared with `x-dead-letter-exchange` pointing at it, so a listener that keeps
throwing sends the message here after the configured retries rather than redelivering forever.
The DLX is a fanout on purpose — dead-lettered messages keep their original routing key, which a
direct DLX would fail to match and drop. See `docs/agent/rabbitmq.md`.

**User-identifying events carry `keycloakId`.** This applies to `user.deleted` (below) and to
`dictionary.imported`, whose payload is `{dictionaryId, keycloakId, eventTimestamp}` — fixed by the
P2-09 consumer, and what ms_marketplace must publish at P4-07. ms_marketplace and ms_dictionary only
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
`user.dictionary.imported`. Nothing publishes `dictionary.imported` until ms_marketplace ships
(P4-07), but a bound durable queue captures those imports instead of letting the topic exchange
discard them.

As of P2-10 ms_dictionary consumes `user.deleted` on the durable queue `dictionary.user.deleted` and
cascade-deletes that user's dictionaries and words — **matching on the event's `keycloakId`**, since
`fk_user_id` is the JWT subject. It publishes no `dictionary.deleted` for the cascaded rows, because
ms_marketplace consumes `user.deleted` itself. Verified live end-to-end: `DELETE /users/{userId}` on
ms_user removes the user's dictionaries and words from ms_dictionary, and a redelivery is a no-op.

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
