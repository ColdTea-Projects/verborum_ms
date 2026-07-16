# Verborum — Build Roadmap

## How to Use This File

When asked "what should I build next?" or "what's the current status?":
1. Find the first task that is `[ ]` (not done)
2. Check its dependencies — all must be `[x]` before starting
3. Explain what the task is, why it comes next, and what files to create/modify
4. After completing a task, mark it `[x]` and commit

Status markers:
- `[x]` Done
- `[-]` In progress
- `[ ]` Not started

Task IDs:
Every task has a stable ID like `P2-06` (Phase 2, task 6). Use these IDs to refer to
tasks unambiguously — e.g. "do P2-06" or "what's blocking P4-03?". IDs never change even
if tasks are reordered, so they are safe to reference in commits and conversation.

---

## Phase 0 — Fix ms_dictionary Bugs
> Goal: Make ms_dictionary production-ready before building anything on top of it.
> All Phase 1+ work depends on ms_dictionary being correct.

- [x] `P0-01` Core CRUD for Dictionary and Word is implemented
- [x] `P0-02` Validation, exception handling, Liquibase, MapStruct all working
- [x] `P0-03` **Fix `Response` and `ErrorResponse` missing `@Getter`**
  - Files: `common/response/Response.java`, `common/response/ErrorResponse.java`
  - Why: Jackson serializes these as empty `{}` without getters. Every API response is broken.
  - Done when: POST /dictionaries/ returns a properly populated JSON response body
- [x] `P0-04` **Add missing `GET /dictionaries/dictionary/{dictionaryId}` endpoint**
  - Files: `DictionaryController`, `DictionaryService`, `DictionaryServiceImpl`, `DictionaryRepository`
  - Why: Shown in architecture diagram, needed by mobile client and ms_marketplace
  - Done when: endpoint returns a single `DictionaryResponseDTO` or 404 if not found
- [x] `P0-05` **Add missing batch fetch endpoints**
  - `GET /dictionaries/batch?ids=id1,id2` — fetch multiple dictionaries by ID list
  - `GET /words/batch?ids=id1,id2` — fetch multiple words by ID list
  - Done when: both endpoints return correct lists; empty list for no matches (not 404)
- [x] `P0-06` **Document `word_meta` / `translation_meta` JSON contract**
  - Add a comment in `Word.java` and `verborum.md` defining the expected JSON shape
  - Done when: the shape is documented and both entity and changelog use `columnDefinition = "json"`
  - Note (2026-07-12 full review): the columns are actually `VARCHAR(255)` in the changelog,
    not `json` as previously documented — metadata longer than 255 chars will fail to insert.
    Fixing this needs a new changeset (never modify the existing one).
  - Done 2026-07-12: new changeset `2026/07/12-01-changelog.json` converts both columns to
    `json`; entity uses `@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "json"`; the
    JSON shape (optional keys partOfSpeech/example/notes) is documented in Word.java + verborum.md

<!-- Tasks P0-07 … P0-13 added 2026-07-12 after a one-time full review of the existing code -->
- [x] `P0-07` **Add `RecordNotFoundException` handler to `GlobalExceptionHandler`**
  - Thrown by `WordServiceImpl` (unknown dictionary on save/delete) but has no handler,
    so it falls into the generic `Exception` handler and returns 500 instead of 404
  - Done when: POST /words with an unknown dictionaryId returns 404 with a proper ErrorResponse
- [x] `P0-08` **Fix swapped arguments in `handleMethodArgumentNotValidException`** (ms_dictionary AND ms_user)
  - `buildErrorResponse(status, errorMessage, ExceptionName, request)` — message and exception
    name are reversed vs. the signature `(status, simpleName, detail, request)`
  - Done when: `error` = exception name, `errorDetail` = field messages, in both services
- [x] `P0-09` **Cascade-delete words when a dictionary is deleted**
  - `DictionaryServiceImpl.deleteDictionary()` deletes only the dictionary; its words are
    orphaned forever (no DB-level FK to stop it). `WordRepository.deleteByDictionaryIdIn`
    already exists (currently unused)
  - Done when: DELETE /dictionaries/{id} removes the dictionary and all its words in one transaction
- [x] `P0-10` **Add `@Valid` to `WordBundleRequestDTO.words`**
  - Without cascade, the per-word constraints (`@ValidUUID` on wordId, `@NotBlank`s) never run
    on POST/PUT /words — invalid words are accepted
  - Done when: posting a bundle with a malformed wordId returns 400
- [x] `P0-11` **Make custom validators null-safe**
  - `UUIDValidator`: `UUID.fromString(null)` throws NPE (only `IllegalArgumentException` is
    caught); `SupportedLanguageValidator`: `language.toUpperCase()` NPEs on null → both 500
    instead of a clean validation error when the field is missing
  - Done when: null input returns the proper 400 validation error (leave null-checking to `@NotBlank`)
- [x] `P0-12` **Remove pinned ancient test dependencies from both poms** (ms_dictionary AND ms_user)
  - `junit-jupiter-engine` 5.6.2 and `mockito-junit-jupiter` 2.23.0 are pinned at compile scope,
    conflicting with Spring Boot 3.2's managed JUnit 5.10 — `MsDictionaryApplicationTests` fails
    with `AbstractMethodError` and the whole suite exits red; they also ship in the prod jar
  - Done when: versions/scopes removed (Boot manages them), `mvnw test` passes in both modules
  - Done 2026-07-12. All 16 unit tests green, `mvnw test` passes in both modules. The
    `contextLoads` `@SpringBootTest` in each module was `@Disabled` because it needs the
    docker-compose Postgres running — re-enable once a DB is available in CI/dev
  - 2026-07-16: Docker is now installed, so both `contextLoads` tests are re-enabled and pass
    against the compose Postgres. Full suite: 24 tests, 0 failures, 0 skipped. They need
    `docker compose up -d` first — a plain `mvnw test` on a machine with no DB will now fail
    rather than skip.
- [x] `P0-13` **Un-pin `postgresql` 42.3.8** (ms_dictionary AND ms_user)
  - Overrides Boot-managed driver and is affected by CVE-2024-1597; drop the `<version>` tag
  - Done when: both modules build with the Boot-managed driver version
  - Done 2026-07-12. Note: Boot 3.2.2 itself manages 42.6.0 (also affected by CVE-2024-1597),
    so both poms set `<postgresql.version>42.6.2</postgresql.version>` — Boot's sanctioned
    override property. Remove the property once the Boot parent is bumped to ≥3.2.3

<!-- Tasks P0-14 … P0-15 added 2026-07-16, found while smoke-testing the live service -->
- [x] `P0-14` **Return 400 instead of 500 for a malformed request body** (ms_dictionary AND ms_user)
  - A body that does not parse (e.g. a JSON object where `POST /words` expects an array) throws
    `HttpMessageNotReadableException`, which had no handler and fell through to the generic
    `Exception` handler → 500. A client error was being reported as a server error
  - Done 2026-07-16: `HttpMessageNotReadableException` handler added to `GlobalExceptionHandler`
    in both services; verified live — the payload that returned 500 now returns 400
- [x] `P0-15` **Populate `path` in `Response` and `ErrorResponse`** (ms_dictionary AND ms_user)
  - Both used `request.getContextPath()`, which is `""` unless a servlet context path is set
    (none is), so every response carried `"path": ""`. Also, the three `*_DELETED_SUCCESSFULLY`
    constants lacked the trailing space the `SAVED`/`UPDATED` ones have, producing messages like
    `"Deleted successfully3d88b7cb-..."`
  - Done 2026-07-16: added `ResponseUtils.extractPath(WebRequest)` (strips the `uri=` prefix from
    `getDescription(false)`), used by `buildResponse` and `buildErrorResponse` in both services;
    trailing space added to the delete constants. Verified live — `"path": "/words"` on a 400,
    `"path": "/dictionaries/{id}"` and `"Deleted successfully e5ecb5c3-..."` on a delete

---

## Phase 1 — Add RabbitMQ Infrastructure
> Goal: Get the message broker running locally and wired into ms_dictionary.
> ms_user and ms_marketplace cannot publish/consume events without this.
> Depends on: Phase 0 complete

- [x] `P1-01` **Add RabbitMQ to docker-compose**
  - Create a root-level `docker-compose.yml` that includes RabbitMQ + all service DBs
  - See `docs/agent/rabbitmq.md` for the RabbitMQ service definition
  - Done when: `docker-compose up` starts Postgres (5432), RabbitMQ (5672), and Management UI (15672)
  - 2026-07-16: root `docker-compose.yml` written — RabbitMQ (5672/15672, verborum/verborum),
    `db_dictionary` (5432, vdbdictionary), `db_user` (5433, vdbprofile), one Adminer (8080),
    named volumes + healthchecks.
  - Done 2026-07-16: Docker installed on the dev machine and `docker compose up -d` verified —
    all four containers healthy, Management UI 200 on 15672, AMQP open on 5672, Adminer 200 on
    8080, and both services' Liquibase changesets ran clean against the new Postgres containers.
  - Note: the root compose and the per-service compose files bind the same host ports —
    run one or the other, never both.
  - Note: `version:` is obsolete in current Compose and logs a warning on every run — harmless,
    remove the attribute when next touching this file.
- [x] `P1-02` **Add RabbitMQ dependency and config to ms_dictionary**
  - Add `spring-boot-starter-amqp` to `pom.xml`
  - Create `common/config/RabbitMQConfig.java` with exchange, DLQ declarations
  - Add RabbitMQ connection properties to `application.properties`
  - Done when: ms_dictionary starts without errors with RabbitMQ running
  - Done 2026-07-16: `RabbitMQConfig` declares `verborum.events` (topic, durable), the
    `verborum.events.dlx` direct exchange, the `verborum.dead-letter` queue and its binding,
    plus a `Jackson2JsonMessageConverter` and a `RabbitTemplate` using it. Routing key constants
    for the P1-03…P1-05 events are defined here too. Connection + listener retry properties added
    to `application.properties`. Verified live against the broker: app starts clean and both
    exchanges, the DLQ and the binding show up in the Management API. No consumer queue yet —
    ms_dictionary is publisher-only until P2-10.
  - Note: credentials are plain `verborum`/`verborum` in `application.properties`, matching the
    existing datasource convention. Move to env vars alongside the Phase 3 secret work.
  - Design change 2026-07-16: the DLX is a **fanout**, not the `DirectExchange` the old
    `rabbitmq.md` template showed. RabbitMQ preserves a message's original routing key when
    dead-lettering, so a direct DLX bound only on `verborum.dead-letter` would drop a failed
    `user.deleted` as unroutable — silently, exactly where you most need the message. Fanout
    ignores the routing key, so a consumer queue only needs `x-dead-letter-exchange`.
    `rabbitmq.md` is updated to match. Verified: publishing to the DLX with routing key
    `user.deleted` lands in `verborum.dead-letter`. This matters at P2-10, the first consumer
    queue — do not revert the DLX to direct without adding `x-dead-letter-routing-key` to every
    consumer queue.
- [x] `P1-03` **Publish `dictionary.visibility.public/private` events from ms_dictionary**
  - Create `common/event/DictionaryVisibilityEvent.java`
  - Modify `DictionaryServiceImpl.saveDictionary()` to publish when `is_public` changes
  - Done when: saving a public dictionary sends a message visible in RabbitMQ Management UI
  - Done 2026-07-16: event DTO created per the shape in `rabbitmq.md`; `saveDictionary()` reads
    the previous `is_public` before saving and publishes only on an actual flip. 5 unit tests
    cover each transition (ms_dictionary suite now 28). Verified live by binding a temporary
    queue to `dictionary.visibility.#`: creating a public dictionary emitted
    `dictionary.visibility.public`, flipping it emitted `dictionary.visibility.private`, and a
    rename in between emitted nothing.
  - Publishes on **change only**, not on every save of a public dictionary (which is what the
    `rabbitmq.md` publisher example shows). `saveDictionary()` backs both POST and PUT, so
    re-announcing on every save would have ms_marketplace create a duplicate listing on a rename.
  - Known gap for Phase 4: because nothing is published when a public dictionary is renamed,
    ms_marketplace's stored `name`/`fromLang`/`toLang` go stale after an edit. Decide in P4-03
    whether to add a `dictionary.updated` event or have the listing re-read from ms_dictionary.
  - Note: the publish happens inside `@Transactional`, per the `rabbitmq.md` publisher pattern.
    `RabbitTemplate` is not transactional here, so this is a dual-write with two known races,
    both inherited from that pattern rather than introduced here:
    1. Anything that throws *after* the send rolls back the write while the event stays
       published. The publish is deliberately the last statement in `saveDictionary()` to keep
       that window as small as possible — keep it there.
    2. The send happens **before** the transaction commits. A consumer that reacts immediately
       and reads back from ms_dictionary can beat the commit and see stale or absent data.
       Nothing consumes these events yet, so this is not live — but it becomes real at P4-03,
       and that is the task that should switch publishing to
       `@TransactionalEventListener(phase = AFTER_COMMIT)`.
- [ ] `P1-04` **Publish `dictionary.deleted` event from ms_dictionary**
  - Create `common/event/DictionaryDeletedEvent.java`
  - Modify `DictionaryServiceImpl.deleteDictionary()` to publish on delete
  - Done when: deleting a dictionary sends a message visible in RabbitMQ Management UI
- [ ] `P1-05` **Publish `word.created` event from ms_dictionary (V2 prep)**
  - Create `common/event/WordCreatedEvent.java`
  - Modify `WordServiceImpl.saveWords()` to publish per word saved
  - Done when: adding words sends messages visible in RabbitMQ Management UI

---

## Phase 2 — Build ms_user
> Goal: User profile management, auth integration with Keycloak.
> Depends on: Phase 1 complete (needs RabbitMQ to consume `user.deleted` cascade logic later)
> Mirror ms_dictionary structure exactly — see `docs/agent/clean-code.md`

- [x] `P2-01` **Scaffold ms_user module**
  - Create `ms_user/` directory with `pom.xml`, `MsUserApplication.java`
  - Port: 8086
  - Base package: `de.coldtea.verborum.msuser`
  - Done when: app starts (even with no endpoints) on port 8086
- [x] `P2-02` **Create `docker-compose.yml` for ms_user**
  - Postgres on port 5433, Adminer on port 8081, DB name: `vdbprofile`
  - Done when: `docker-compose up` in `ms_user/` starts the DB
- [ ] `P2-03` **Design and create User entity + Liquibase migration**
  - Entity: `User` — fields: `userId`, `keycloakId`, `email`, `displayName`, `creationTimestamp`, `updateTimestamp`
  - Changeset file: `db/changelog/{YEAR}/{MONTH}/{date}-01-changelog.json`
  - Done when: table `users` is created in `vdbprofile` on startup
- [ ] `P2-04` **Design and create UserStats entity + migration**
  - Entity: `UserStats` — fields: `userId` (PK, FK to user), `totalWords`, `totalDictionaries`, `updatedAt`
  - Done when: table `user_stats` is created
- [ ] `P2-05` **Design and create VaultEntry entity + migration**
  - Entity: `VaultEntry` — tracks imported public dictionaries per user
  - Fields: `vaultEntryId`, `userId`, `dictionaryId`, `importedAt`
  - Done when: table `vault_entries` is created
- [ ] `P2-06` **Implement UserController + UserService**
  - Endpoints:
    - `POST /users/` — create user profile (called after Keycloak registration)
    - `GET /users/{userId}` — get user profile
    - `PUT /users/` — update user profile
    - `DELETE /users/{userId}` — delete user (also triggers `user.deleted` event)
  - Done when: all endpoints work, unit tests pass
- [ ] `P2-07` **Implement VaultController + VaultService**
  - Endpoints:
    - `GET /users/{userId}/vault` — list imported dictionaries
    - `POST /users/{userId}/vault` — add dictionary to vault (manual import)
    - `DELETE /users/{userId}/vault/{dictionaryId}` — remove from vault
  - Done when: all endpoints work, unit tests pass
- [ ] `P2-08` **Publish `user.deleted` event from ms_user**
  - Create `common/event/UserDeletedEvent.java`
  - Publish from `UserServiceImpl.deleteUser()`
  - Done when: deleting a user publishes a message to `user.deleted` routing key
- [ ] `P2-09` **Consume `dictionary.imported` event in ms_user**
  - Create `common/config/RabbitMQConfig.java` in ms_user
  - Create `common/listener/MarketplaceEventListener.java`
  - On `dictionary.imported` event: add a VaultEntry for the user
  - Done when: importing a dictionary via marketplace creates a vault entry
- [ ] `P2-10` **Consume `user.deleted` event in ms_dictionary**
  - Add queue + binding to ms_dictionary's `RabbitMQConfig`
  - Create `common/listener/UserEventListener.java` in ms_dictionary
  - On `user.deleted`: delete all dictionaries and words for that userId
  - Done when: deleting a user cascades to remove their dictionaries and words
- [ ] `P2-11` **Fix Keycloak role mapping in ms_user `SecurityConfig`** (added 2026-07-12 after full review)
  - `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles")` does NOT
    resolve nested claims — Keycloak realm roles live under `realm_access` → `roles`, so no
    roles are ever mapped to authorities. Needs a custom converter that reads the nested claim
  - Done when: a token with realm role `user` yields authority `ROLE_user` in the security context

---

## Phase 3 — Add Security Layer
> Goal: Protect all endpoints with JWT authentication via Keycloak.
> Depends on: Phase 2 complete (Keycloak must be configured with ms_user)
> See `docs/agent/security.md` for full implementation details

- [ ] `P3-01` **Add Keycloak to root docker-compose**
  - Image: `quay.io/keycloak/keycloak:23.0.0` on port 8180
  - Done when: Keycloak Admin UI accessible at http://localhost:8180
- [ ] `P3-02` **Configure Keycloak realm and clients**
  - Create realm `verborum`
  - Create client `verborum-app` (public, for mobile)
  - Create client `verborum-backend` (confidential, for service-to-service)
  - Done when: a test user can obtain a JWT token via Keycloak
- [ ] `P3-03` **Add Spring Security to ms_dictionary**
  - Add `spring-boot-starter-security` + `oauth2-resource-server` to `pom.xml`
  - Create `common/config/SecurityConfig.java` — stateless JWT, permit actuator + Swagger
  - Add `issuer-uri` and `jwk-set-uri` to `application.properties`
  - Done when: unauthenticated requests to ms_dictionary return 401; authenticated requests still work
  - Note: ms_dictionary has been running with ALL endpoints open — this closes that gap
- [ ] `P3-04` **Add Spring Security to ms_user**
  - Same pattern as ms_dictionary
  - Add Keycloak Admin Client for user registration flows
  - Done when: ms_user endpoints are protected and user registration works end-to-end
- [ ] `P3-05` **Extract userId from JWT in controllers (stop trusting client-provided userId)**
  - Create `common/utils/SecurityUtils.java` in each secured service
  - Update create/mutate endpoints to use `SecurityUtils.getCurrentUserId()`
  - Done when: userId in Dictionary and Word always comes from the token, not request body
- [ ] `P3-06` **Lock down actuator exposure in all services** (added 2026-07-12 after full review)
  - `management.endpoints.web.exposure.include=*` combined with `permitAll` on `/actuator/**`
    exposes `/actuator/env`, heapdump, etc. publicly — in ms_user this can leak
    `KEYCLOAK_ADMIN_CLIENT_SECRET` via the env endpoint
  - Restrict to `health,info` (or secure the rest with a role)
  - Done when: `/actuator/env` is not publicly reachable in any service

---

## Phase 4 — Build ms_marketplace
> Goal: Public dictionary listings, stats, ratings.
> Depends on: Phase 3 complete (needs secured ms_dictionary events flowing via RabbitMQ)

- [ ] `P4-01` **Scaffold ms_marketplace module**
  - Port: 8087, base package: `de.coldtea.verborum.msmarketplace`
  - DB: `vdbmarket` on port 5434, Adminer on 8082
  - **Include Spring Security + Keycloak JWT from the start** — see `docs/agent/security.md`
  - Add `spring-boot-starter-security` + `oauth2-resource-server` to `pom.xml`
  - Create `common/config/SecurityConfig.java` alongside the initial scaffold
  - Done when: app starts on port 8087 AND unauthenticated requests return 401
- [ ] `P4-02` **Design DictionaryStats entity + migration**
  - Fields: `dictionaryId`, `userId`, `name`, `fromLang`, `toLang`, `importCount`, `viewCount`, `rating`, `publishedAt`
  - Done when: table `dictionary_stats` created on startup
- [ ] `P4-03` **Consume `dictionary.visibility.public` event**
  - On event: create a `DictionaryStats` record for the dictionary
  - Done when: making a dictionary public creates a marketplace entry
  - Read the P1-03 notes first — this is the task where two known issues stop being theoretical:
    1. ms_dictionary publishes *before* its transaction commits, so a listener that calls back
       into ms_dictionary can beat the commit. Switch ms_dictionary to
       `@TransactionalEventListener(phase = AFTER_COMMIT)` as part of this task. The event
       already carries the full listing payload, so the consumer should not need a callback.
    2. Visibility events fire on change only — a renamed public dictionary emits nothing, so a
       listing's `name`/`fromLang`/`toLang` will go stale. Decide here: either add a
       `dictionary.updated` event or have the listing re-read on access.
  - Make the listener idempotent regardless: a redelivery must not create a second listing.
- [ ] `P4-04` **Consume `dictionary.visibility.private` event**
  - On event: remove or deactivate the `DictionaryStats` record
  - Done when: making a dictionary private removes it from marketplace
- [ ] `P4-05` **Consume `dictionary.deleted` event**
  - On event: remove the `DictionaryStats` record
  - Done when: deleting a dictionary removes its marketplace entry
- [ ] `P4-06` **Implement MarketplaceController**
  - `GET /marketplace/dictionaries` — list all public dictionaries (paginated)
  - `GET /marketplace/dictionaries/popular` — sorted by import count
  - `GET /marketplace/dictionaries/language?from=EN&to=DE` — filter by language pair
  - `POST /marketplace/dictionaries/{dictionaryId}/import` — import dictionary to vault
  - Done when: all endpoints work and publish/consume events correctly
- [ ] `P4-07` **Publish `dictionary.imported` event from ms_marketplace**
  - On import: publish event so ms_user can add to vault
  - Done when: importing triggers a vault entry in ms_user

---

## Phase 5 — API Gateway
> Goal: Single entry point for all mobile traffic.
> Depends on: Phase 4 complete

- [ ] `P5-01` **Scaffold ms_gateway module**
  - Use Spring Cloud Gateway
  - Port: 8080
  - Done when: gateway starts and routes requests to the correct service
- [ ] `P5-02` **Configure routes**
  - `/dictionaries/**` → ms_dictionary (8085)
  - `/words/**` → ms_dictionary (8085)
  - `/users/**` → ms_user (8086)
  - `/marketplace/**` → ms_marketplace (8087)
  - Done when: all routes work end-to-end through the gateway
- [ ] `P5-03` **Add JWT validation at gateway level**
  - Validate token once at the gateway, forward user info in headers
  - Done when: invalid tokens are rejected at the gateway before reaching services

---

## Phase 6 — Autofil Service (V2)
> Goal: Word suggestions based on community translations.
> Depends on: Phase 5 complete, sufficient word data in the system

- [ ] `P6-01` **Choose NoSQL store** (MongoDB recommended — flexible schema, good Spring support)
- [ ] `P6-02` **Scaffold ms_autofil module**
  - **Include Spring Security + Keycloak JWT from the start** — see `docs/agent/security.md`
  - Add `spring-boot-starter-security` + `oauth2-resource-server` to `pom.xml`
  - Create `common/config/SecurityConfig.java` alongside the initial scaffold
  - Done when: app starts AND unauthenticated requests to its endpoints return 401
- [ ] `P6-03` **Consume `word.created` events and aggregate by language pair**
  - Store: `{ word, fromLang, toLang, translations: [{translation, count}] }`
  - Done when: adding words populates the suggestion store
- [ ] `P6-04` **Implement AutofilController**
  - `GET /autofil?word=Haus&from=DE&to=EN` → returns ranked translation suggestions
  - Done when: endpoint returns community translations ordered by frequency
