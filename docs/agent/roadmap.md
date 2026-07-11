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
- [ ] `P0-03` **Fix `Response` and `ErrorResponse` missing `@Getter`**
  - Files: `common/response/Response.java`, `common/response/ErrorResponse.java`
  - Why: Jackson serializes these as empty `{}` without getters. Every API response is broken.
  - Done when: POST /dictionaries/ returns a properly populated JSON response body
- [ ] `P0-04` **Add missing `GET /dictionaries/dictionary/{dictionaryId}` endpoint**
  - Files: `DictionaryController`, `DictionaryService`, `DictionaryServiceImpl`, `DictionaryRepository`
  - Why: Shown in architecture diagram, needed by mobile client and ms_marketplace
  - Done when: endpoint returns a single `DictionaryResponseDTO` or 404 if not found
- [ ] `P0-05` **Add missing batch fetch endpoints**
  - `GET /dictionaries/batch?ids=id1,id2` — fetch multiple dictionaries by ID list
  - `GET /words/batch?ids=id1,id2` — fetch multiple words by ID list
  - Done when: both endpoints return correct lists; empty list for no matches (not 404)
- [ ] `P0-06` **Document `word_meta` / `translation_meta` JSON contract**
  - Add a comment in `Word.java` and `verborum.md` defining the expected JSON shape
  - Done when: the shape is documented and both entity and changelog use `columnDefinition = "json"`

---

## Phase 1 — Add RabbitMQ Infrastructure
> Goal: Get the message broker running locally and wired into ms_dictionary.
> ms_user and ms_marketplace cannot publish/consume events without this.
> Depends on: Phase 0 complete

- [ ] `P1-01` **Add RabbitMQ to docker-compose**
  - Create a root-level `docker-compose.yml` that includes RabbitMQ + all service DBs
  - See `docs/agent/rabbitmq.md` for the RabbitMQ service definition
  - Done when: `docker-compose up` starts Postgres (5432), RabbitMQ (5672), and Management UI (15672)
- [ ] `P1-02` **Add RabbitMQ dependency and config to ms_dictionary**
  - Add `spring-boot-starter-amqp` to `pom.xml`
  - Create `common/config/RabbitMQConfig.java` with exchange, DLQ declarations
  - Add RabbitMQ connection properties to `application.properties`
  - Done when: ms_dictionary starts without errors with RabbitMQ running
- [ ] `P1-03` **Publish `dictionary.visibility.public/private` events from ms_dictionary**
  - Create `common/event/DictionaryVisibilityEvent.java`
  - Modify `DictionaryServiceImpl.saveDictionary()` to publish when `is_public` changes
  - Done when: saving a public dictionary sends a message visible in RabbitMQ Management UI
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

- [ ] `P2-01` **Scaffold ms_user module**
  - Create `ms_user/` directory with `pom.xml`, `MsUserApplication.java`
  - Port: 8086
  - Base package: `de.coldtea.verborum.msuser`
  - Done when: app starts (even with no endpoints) on port 8086
- [ ] `P2-02` **Create `docker-compose.yml` for ms_user**
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
