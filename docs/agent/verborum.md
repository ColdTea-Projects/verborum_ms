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

### ⚠️ ms_dictionary — FUNCTIONALLY COMPLETE, NOT PRODUCTION-READY
- **Port:** 8085
- **DB:** `vdbdictionary` (PostgreSQL)
- **Base package:** `de.coldtea.verborum.msdictionary`
- **What it does:** Full CRUD for Dictionaries and Words
- **Docker:** `ms_dictionary/docker-compose.yml` (Postgres + Adminer)
- **Missing:** No authentication or authorization — all endpoints are completely open.
  Security will be added in Phase 3 once Keycloak is configured via ms_user.
  Until then, do not expose ms_dictionary to public traffic.

### 🚧 ms_user — SCAFFOLDED, NO FEATURES YET
- **Port:** 8086
- **DB:** `vdbprofile` (PostgreSQL) — docker-compose in `ms_user/` (Postgres 5433 + Adminer 8081)
- **Base package:** `de.coldtea.verborum.msuser`
- **What it does:** User profile, auth integration with Keycloak, Dictionary Vault, User Stats
- **Mirror:** Follow ms_dictionary structure exactly

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
creation_dt     TIMESTAMP    Auto-set by Hibernate @CreationTimestamp
update_dt       TIMESTAMP    Auto-set by Hibernate @UpdateTimestamp
```

### Word (`words` table in ms_dictionary)
```
word_id             VARCHAR PK   UUID string, provided by client
fk_dictionary_id    VARCHAR      UUID of parent dictionary (no DB-level FK)
word                VARCHAR      The word in from_lang
word_meta           JSON         JSON object, optional keys: partOfSpeech, example, notes (extra keys allowed)
translation         VARCHAR      The translation in to_lang
translation_meta    JSON         Same JSON contract as word_meta
creation_dt         TIMESTAMP
update_dt           TIMESTAMP
```

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
`EN, DE, FR, ES, IT, TR, AZ, LT`
Configured in `application.properties` as `supported.languages=EN,DE,FR,ES,IT,PT,NL,TR,AZ,LT,PL,UK,AR,FA,JA,ZH,KO,EL,RU`
Validated via custom `@SupportedLanguage` annotation + `SupportedLanguageValidator`.

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

**Current wiring state (2026-07-16):** Phase 1 is complete. ms_dictionary declares the exchange and
the dead letter infrastructure (roadmap P1-02) and publishes every event it owns:
`dictionary.visibility.public` / `dictionary.visibility.private` (P1-03), `dictionary.deleted`
(P1-04) and `word.created` (P1-05). Nothing consumes any of them yet — ms_marketplace and
ms_autofil do not exist, and a topic exchange discards a message with no bound queue, so these are
fire-and-forget until P4-03. ms_dictionary has no consumer queue until it starts consuming
`user.deleted` (P2-10). All services declare the same exchange; declarations are idempotent, so
whichever service starts first creates it.

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

1. **No security on any endpoint** — ms_dictionary has no authentication or authorization.
   All endpoints are open. This is intentional for local development only and will be
   fixed in Phase 3 by adding Spring Security + Keycloak JWT validation. Do not expose
   ms_dictionary to public traffic until Phase 3 is complete.

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
on 5433, and a single Adminer on 8080. It binds the same host ports as the per-service files,
so run the root compose or a service compose — never both at once.
