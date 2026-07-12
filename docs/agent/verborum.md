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
Configured in `application.properties` as `supported.languages=EN,DE,FR,ES,IT,TR,AZ,LT`
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
supported.languages=EN,DE,FR,ES,IT,TR,AZ,LT
```

Each service has its own `docker-compose.yml` with a Postgres + Adminer setup.
