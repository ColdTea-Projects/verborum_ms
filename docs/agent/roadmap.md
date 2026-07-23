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

<!-- Tasks P0-16 … P0-18 added 2026-07-21 after reviewing the client-team docs (Android
     Development, Frontend–Backend Integration, Dockerization & Environments) -->
- [x] `P0-16` **Extend `supported.languages` to the 19-code list** (ms_dictionary AND ms_user)
  - The Android client selects 10 languages today and expands to 19; the backend validator was
    still the original 8, so PT/NL dictionaries failed upload and sat permanently unsynced
  - Done (commit `053dce0`): both services' `application.properties` now list
    `EN,DE,FR,ES,IT,PT,NL,TR,AZ,LT,PL,UK,AR,FA,JA,ZH,KO,EL,RU`. The validator uppercases before
    matching, so the client's lowercase codes pass. This unblocks Android roadmap A1.
- [x] `P0-17` **Correct the word/meta doc fiction + widen `word`/`translation` to `TEXT`**
  - `Word.java`, `verborum.md`, and `ms_dictionary/CLAUDE.md` documented a placeholder meta shape
    (`partOfSpeech`/`example`/`notes`) written before the client schema existed — no client used
    it. Web/iOS/ms_autofil implementing against it would build the wrong parser
  - Done 2026-07-21: all three now document the canonical client contract — `word`/`translation`
    are a JSON array of per-meaning surfaces; `word_meta`/`translation_meta` is
    `{lang, type?, genders?, fields?}` with lists index-aligned to the surfaces array, unknown
    keys ignored. New changeset `2026/07/21-01-changelog.json` widens `word`/`translation` from
    `VARCHAR(255)` to `TEXT` (multi-meaning entries can exceed 255). Storage stays opaque.
- [x] `P0-18` **Land the client-integration and ops docs into the repo**
  - Done 2026-07-21: `docs/integration/frontend-backend-integration.md` (cross-client API/auth/
    language/meta contract) and `docs/ops/dockerization-and-environments.md` (containerization plan)
    added as the single source of truth referenced by the Android and future KMP repos

<!-- P0-19 added 2026-07-21 after aligning the word/dictionary schema with the mobile client -->
- [x] `P0-19` **Add word `level`, and fix timestamp names/zones to match the clients** (ms_dictionary)
  - The mobile client stores a per-word `level` (mastery) and expects `createdAt`/`updatedAt` as
    zone-aware timestamps; the backend had no `level` and exposed `creationTimestamp`/
    `updateTimestamp` as zoneless `LocalDateTime` (ambiguous instant across dev/prod)
  - Done 2026-07-21:
    - `level` (INT, nullable) added to `Word`, `WordRequestDTO`, `WordResponseDTO` +
      `2026/07/21-02-changelog.json`. Nullable/optional so older clients keep uploading; `null` =
      not provided (treat as `0` client-side). Not on the `word.created` event.
    - `Word` and `Dictionary` timestamps changed `LocalDateTime` → `OffsetDateTime`, columns
      `creation_dt`/`update_dt` → `timestamptz` (`2026/07/21-03-changelog.json`), and the read-DTO
      fields renamed to `createdAt`/`updatedAt`. On the wire: ISO-8601 UTC, e.g.
      `"2026-07-21T09:34:42.622774Z"`. DB column names unchanged.
    - Verified live: POST with `level` stores/returns it; POST without `level` still returns 201
      (null); GET emits `createdAt`/`updatedAt` with a `Z`. 36 tests green. Docs updated
      (`verborum.md`, integration §4.3/§4.4, `ms_dictionary/CLAUDE.md`).
  - Note: this renames the (recently added, never correctly consumed) `creationTimestamp`/
    `updateTimestamp` keys — a coordinated change with the mobile client, done together with this.
    The `Response`/`ErrorResponse` envelope `timestamp` (OffsetDateTime, local offset) is unchanged.

<!-- P0-20 added 2026-07-21 — mirror the P0-19 timestamp treatment into ms_user for consistency -->
- [x] `P0-20` **Mirror zone-aware timestamps into ms_user** (ms_user)
  - After P0-19 made ms_dictionary timestamps zone-aware, ms_user still used zoneless
    `LocalDateTime`. Aligned for cross-service consistency before ms_user gets a live client
  - Done 2026-07-21: `User`, `UserStats`, `VaultEntry` timestamps changed `LocalDateTime` →
    `OffsetDateTime`; columns `users.creation_dt/update_dt`, `user_stats.update_dt`,
    `vault_entries.imported_at` → `timestamptz` (`2026/07/21-04-changelog.json`). `User` read-DTO
    fields renamed to `createdAt`/`updatedAt` (same wire contract as ms_dictionary); `VaultEntry`
    keeps the semantic `importedAt`. DB column names unchanged. Verified: Liquibase applies the
    changeset on boot, all four columns are `timestamptz`, and the full suite (6 tests) is green. The
    wire JSON is identical by construction to the P0-19 output already verified live in ms_dictionary
    (same Boot/Jackson/Hibernate, OffsetDateTime over timestamptz → ISO-8601 UTC `...Z`); not
    re-curled because ms_user endpoints require a JWT (secured, unlike ms_dictionary).

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
  - Note: credentials were plain `verborum`/`verborum` in `application.properties`, matching the
    existing datasource convention.
  - 2026-07-19: resolved ahead of the Phase 3 secret work. Every host and credential in both
    services' `application.properties` is now `${ENV_VAR:current-value}` — datasource URL/user/
    password, RabbitMQ host/port/user/password, server port, and the Keycloak issuer, JWK set,
    realm, auth-server URL and admin client-id. Defaults are the previous literals, so local dev
    is unchanged and no env vars are required to run today. This is the prerequisite for
    containerizing: in a container `localhost` resolves to the container itself, so these values
    must be overridable from outside the jar before any service can be Dockerized.
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
- [x] `P1-04` **Publish `dictionary.deleted` event from ms_dictionary**
  - Create `common/event/DictionaryDeletedEvent.java`
  - Modify `DictionaryServiceImpl.deleteDictionary()` to publish on delete
  - Done when: deleting a dictionary sends a message visible in RabbitMQ Management UI
  - Done 2026-07-16: `rabbitmq.md` gives no payload shape for this event, so it mirrors the
    minimal `UserDeletedEvent` — `dictionaryId`, `userId`, `eventTimestamp`. The dictionary is
    already gone when a consumer reads the event, so there is nothing to call back for; carrying
    `userId` lets a consumer scope the removal without a lookup.
  - `deleteDictionary()` now reads the dictionary before deleting (it needs `userId` for the
    payload). A delete of an unknown id publishes nothing rather than announcing a deletion that
    never happened. Verified live: delete emits `dictionary.deleted`, unknown id emits nothing.
  - Pre-existing behaviour left alone: DELETE of an unknown id still returns 200, not 404,
    because `deleteById` is a silent no-op in Spring Data JPA 3.x. Arguably wrong, but changing
    it is an API change and out of scope here. (Confirmed for the pinned 3.2.2: `deleteById`
    compiles to `findById(id).ifPresent(this::delete)` — it throws no
    `EmptyResultDataAccessException`, unlike Spring Data JPA 2.x. Verified live: a DELETE of an
    unknown id returns 200 and publishes nothing.)
  - The null-check sits *after* the word deletion on purpose: words can outlive a missing
    dictionary row (there is no DB-level FK), so the cleanup has to run even when the dictionary
    is already gone. Moving the guard above it would silently regress P0-09's orphan cleanup.
  - 2026-07-16: event timestamps are pinned to ISO-8601. `Jackson2JsonMessageConverter` registers
    `JavaTimeModule` but leaves `WRITE_DATES_AS_TIMESTAMPS` on, which rendered `eventTimestamp` as
    `[2026,7,16,15,17,53,415040500]`. Harmless while every consumer is a Java service using this
    same converter, brittle for anything else. `RabbitMQConfig.jsonMessageConverter()` now builds
    on `JacksonUtils.enhancedObjectMapper()` with that feature disabled. On the wire:
    `"eventTimestamp":"2026-07-16T15:38:13.8569117"`. Any new service's `RabbitMQConfig` must do
    the same or its events will disagree — see `rabbitmq.md`.
- [x] `P1-05` **Publish `word.created` event from ms_dictionary (V2 prep)**
  - Create `common/event/WordCreatedEvent.java`
  - Modify `WordServiceImpl.saveWords()` to publish per word saved
  - Done when: adding words sends messages visible in RabbitMQ Management UI
  - Done 2026-07-16: event DTO per the shape in `rabbitmq.md`; one message per newly created word.
    Verified live: a POST of two new words emitted 2 events, a PUT editing one of them emitted
    nothing, and a mixed batch (one existing + one new) emitted exactly 1.
  - Publishes for **newly created words only**. `saveWords()` backs both POST and PUT, so
    `saveWords()` reads which of the incoming ids already exist before saving and filters to the
    genuinely new ones. This matters more than it does for P1-03: ms_autofil (P6-03) aggregates a
    per-translation *count*, so re-announcing an edited word would silently inflate the frequency
    data it ranks suggestions by.
  - `userId`, `fromLang` and `toLang` are not on `Word` — they are read from the word's
    `Dictionary` and carried in the payload so ms_autofil can bucket by language pair without
    calling back. `publishWordCreatedEvents()` fetches them with a single batched `findAllById`
    over the distinct dictionary ids, rather than one query per word. (`findAllById` builds a real
    `IN` query — unlike `findById` it does *not* read through the persistence context, so this is
    one query per batch, not zero.)
  - Known gap for P6-03: editing a word's translation publishes nothing, so ms_autofil never
    learns a correction — it keeps counting the original. Decide there whether a `word.updated`
    event is needed, and note that the fix has to *decrement* the old translation, not just add
    the new one.

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
- [x] `P2-03` **Design and create User entity + Liquibase migration**
  - Entity: `User` — fields: `userId`, `keycloakId`, `email`, `displayName`, `creationTimestamp`, `updateTimestamp`
  - Changeset file: `db/changelog/{YEAR}/{MONTH}/{date}-01-changelog.json`
  - Done when: table `users` is created in `vdbprofile` on startup
  - Done 2026-07-21: `user/entity/User.java` + changeset `2026/07/21-01-changelog.json` (registered
    in master). `keycloak_id` NOT NULL + UNIQUE (1:1 Keycloak-subject link; the cross-service join
    key, since other services store the JWT subject as `fk_user_id`). `email` NOT NULL + UNIQUE
    (product rule: one profile per email; Keycloak remains the identity authority). `display_name`
    nullable. Verified live: Liquibase created `users` in `vdbprofile` on boot with both unique
    constraints. Repository/DTOs/mapper/endpoints are P2-06.
- [x] `P2-04` **Design and create UserStats entity + migration**
  - Entity: `UserStats` — fields: `userId` (PK, FK to user), `totalWords`, `totalDictionaries`, `updatedAt`
  - Done when: table `user_stats` is created
  - Done 2026-07-21: `userstats/entity/UserStats.java` + changeset `2026/07/21-02-changelog.json`
    (registered in master). `user_id` is PK **and** a real FK to `users(user_id)` with
    `ON DELETE CASCADE` — chosen over the `word → dictionary` "no DB FK" convention because
    `UserStats` is a 1:1 same-DB satellite of `User` (not a split-ready/cross-service ref), so the FK
    is the correct model and gives free, correct cleanup on user deletion. The 1:1 is NOT mapped as a
    JPA association (project convention). `updatedAt` is `updateTimestamp`/`update_dt` with
    `@UpdateTimestamp`, matching the naming used across the services. Verified live: Liquibase created
    `user_stats` in `vdbprofile` with `pk_user_stats` and FK `fk_user_stats_user` (ON DELETE CASCADE);
    a stats row is auto-deleted when its user is deleted. Repository/DTOs/endpoints are later phases.
- [x] `P2-05` **Design and create VaultEntry entity + migration**
  - Entity: `VaultEntry` — tracks imported public dictionaries per user
  - Fields: `vaultEntryId`, `userId`, `dictionaryId`, `importedAt`
  - Done when: table `vault_entries` is created
  - Done 2026-07-21: `vault/entity/VaultEntry.java` + changeset `2026/07/21-03-changelog.json`
    (registered in master). `fk_user_id` is a real FK to `users(user_id)` with `ON DELETE CASCADE`
    (same intra-service satellite rationale as P2-04). `fk_dictionary_id` is a **cross-service** ref
    (ms_dictionary) — plain String, **no DB FK**, the genuine case the "no FK" convention exists for.
    Added a composite **UNIQUE (fk_user_id, fk_dictionary_id)** so a vault is a set (no duplicate
    imports) — this also backs P2-09 idempotency. `importedAt` uses `@CreationTimestamp`. Verified
    live: table created with `pk_vault_entries`, FK `fk_vault_entries_user` (cascade), and the unique
    constraint; a duplicate (user, dictionary) insert is rejected and a user delete cascades to
    remove the vault rows.
- [x] `P2-06` **Implement UserController + UserService**
  - Endpoints:
    - `POST /users/` — create user profile (called after Keycloak registration)
    - `GET /users/{userId}` — get user profile
    - `PUT /users/` — update user profile
    - `DELETE /users/{userId}` — delete user (also triggers `user.deleted` event)
  - Done when: all endpoints work, unit tests pass
  - Done 2026-07-21: full `user` slice added mirroring the ms_dictionary `dictionary` slice —
    `UserController`, `UserService`/`UserServiceImpl`, `UserRepository`, `UserRequestDTO`/
    `UserResponseDTO`, `common/mapper/UserMapper` (MapStruct). `saveUser` backs both POST and PUT.
    Added the exception/validator scaffolding ms_user was missing (`RecordNotFoundException`,
    `InvalidUUIDException`, `ValidUUID`/`UUIDValidator`) plus their `GlobalExceptionHandler` handlers,
    and populated the three constants classes. 5 `UserServiceImplTest` cases pass.
  - The `user.deleted` event is NOT part of this task — it is P2-08 (ms_user has no RabbitMQ wiring
    yet). DELETE currently relies on the DB `ON DELETE CASCADE` to clear stats/vault; the event that
    lets ms_dictionary/ms_marketplace cascade their own data comes with P2-08.
  - `userId` is still taken from the request body, not the JWT — switching to the token subject is
    P3-05, deliberately not done here to stay within Phase 2 scope.
  - Note (latent, pre-existing in ms_dictionary too): `@ValidUUID` has no `@Constraint(validatedBy=…)`
    meta-annotation, so Bean Validation never invokes `UUIDValidator` — the annotation is inert in
    both services. Mirrored as-is here to keep behaviour identical; wiring it up (and deciding the
    resulting 400s are wanted) should be a separate, both-services task, not a silent divergence.
- [x] `P2-07` **Implement VaultController + VaultService**
  - Endpoints:
    - `GET /users/{userId}/vault` — list imported dictionaries
    - `POST /users/{userId}/vault` — add dictionary to vault (manual import)
    - `DELETE /users/{userId}/vault/{dictionaryId}` — remove from vault
  - Done when: all endpoints work, unit tests pass
  - Done 2026-07-23: full `vault` slice mirroring the `user` one — `VaultController`,
    `VaultService`/`VaultServiceImpl`, `VaultEntryRepository`, `VaultEntryRequestDTO`/
    `VaultEntryResponseDTO`, `common/mapper/VaultEntryMapper` (MapStruct), plus the vault constants.
    6 `VaultServiceImplTest` cases pass (module unit suite now 11).
  - **`vaultEntryId` is server-generated** (`UUID.randomUUID()`), not client-supplied — a deliberate
    exception to the "client provides IDs" convention. A vault entry is a system-owned row: P2-09
    creates identical rows from a `dictionary.imported` event where no client id exists, and having
    the two paths mint ids differently would be worse. The request body carries only `dictionaryId`;
    `userId` comes from the path.
  - **POST is idempotent.** It looks up `(userId, dictionaryId)` first and returns the existing entry
    instead of inserting a duplicate (the P2-05 composite UNIQUE would otherwise throw). This is the
    same code path P2-09 needs when RabbitMQ redelivers an event — build the listener on
    `addVaultEntry` rather than writing a second insert.
  - POST checks `userRepository.existsById` and throws `RecordNotFoundException` (404) for an unknown
    user, because `fk_user_id` is a real FK and would otherwise fail at the DB as a 500. GET does no
    such check — an unknown user just has an empty vault, matching `GET /dictionaries/{userId}`.
    DELETE of an entry not in the vault is a silent no-op returning 200, matching `deleteDictionary`
    and `deleteUser`.
  - Not verified live: ms_user endpoints require a JWT and Keycloak is not configured until Phase 3
    (same limitation recorded for P0-20). The `contextLoads` `@SpringBootTest` also needs the compose
    Postgres and was not run in this session — Docker Desktop was not running.
- [x] `P2-08` **Publish `user.deleted` event from ms_user**
  - Create `common/event/UserDeletedEvent.java`
  - Publish from `UserServiceImpl.deleteUser()`
  - Done when: deleting a user publishes a message to `user.deleted` routing key
  - Done 2026-07-23: `spring-boot-starter-amqp` added to the ms_user pom, `common/config/RabbitMQConfig`
    created mirroring ms_dictionary's (same exchange, same fanout DLX + DLQ + binding, same
    ISO-8601-pinned `Jackson2JsonMessageConverter` — the timestamp format is a wire contract, not a
    local choice), RabbitMQ connection + listener-retry properties added to `application.properties`
    as `${ENV_VAR:default}`. ms_user is publisher-only until P2-09.
  - **The event carries BOTH `userId` and `keycloakId`, and P2-10 must match on `keycloakId`.**
    `rabbitmq.md` shows a minimal `{userId, eventTimestamp}` payload, which would have been a silent
    bug: ms_dictionary and ms_marketplace store the *JWT subject* in their `fk_user_id` columns, and
    that value is ms_user's `keycloak_id`, not its `user_id` (the quirk documented in
    `ms_user/CLAUDE.md`). A consumer matching on `userId` would delete nothing and report success.
    `rabbitmq.md`'s `UserDeletedEvent` sample is updated to match.
  - `deleteUser()` now reads the user before deleting (the event needs `keycloakId`); a delete of an
    unknown id publishes nothing rather than announcing a deletion that never happened, and still
    returns 200. Same shape as `deleteDictionary`.
  - Publishes inside `@Transactional` as the last statement, inheriting the two known dual-write
    races documented in P1-03. The AFTER_COMMIT switch stays a single coordinated change for all
    publishers at P4-03 — but note the second race is worse here than for visibility events: a
    ms_dictionary consumer that beats the commit cascades a delete that may then roll back.
  - Verified live 2026-07-23 against the compose broker with a throwaway `@SpringBootTest` (bound a
    temp queue to `user.deleted`, then deleted a real user through the service; test removed after,
    temp queue deleted). On the wire:
    `{"userId":"51b2dbe6-…","keycloakId":"8502ce45-…","eventTimestamp":"2026-07-23T11:17:59.0721981"}`
    — both ids present, timestamp ISO-8601 as required. Deleting an unknown id emitted nothing.
    The HTTP path (`DELETE /users/{userId}`) still cannot be curled: it requires a JWT and Keycloak
    arrives in Phase 3. Full suite: 14 tests green (3 of them new, covering the publish).
- [x] `P2-09` **Consume `dictionary.imported` event in ms_user**
  - Create `common/config/RabbitMQConfig.java` in ms_user
  - Create `common/listener/MarketplaceEventListener.java`
  - On `dictionary.imported` event: add a VaultEntry for the user
  - Done when: importing a dictionary via marketplace creates a vault entry
  - Done 2026-07-23: durable queue `user.dictionary.imported` bound to `dictionary.imported` with
    `x-dead-letter-exchange`, `common/listener/MarketplaceEventListener`, consumer-side
    `common/event/DictionaryImportedEvent`, and `VaultService.importDictionary`. 5 new tests
    (module suite now 19).
  - **The event identifies the user by `keycloakId`, not ms_user's `userId`** — same rule as
    `user.deleted` (P2-08). ms_marketplace only ever sees the JWT subject; `user_id` is private to
    ms_user. `vault_entries.fk_user_id` is a real FK to `users(user_id)`, so `importDictionary`
    resolves keycloakId → user_id (`UserRepository.findByKeycloakId`) before writing. **P4-07 must
    publish `{dictionaryId, keycloakId, eventTimestamp}`** — that is the contract this consumer
    defines.
  - **Cross-service deserialization: the converter now uses a `DefaultJackson2JavaTypeMapper` with
    `INFERRED` type precedence.** `Jackson2JsonMessageConverter` stamps outgoing messages with a
    `__TypeId__` header carrying the publisher's FQCN, and by default the consumer trusts it — so
    ms_marketplace's `…msmarketplace.common.event.DictionaryImportedEvent` would have failed here as
    ClassNotFound on every message, permanently, straight to the DLQ. INFERRED makes the listener's
    own parameter type win, so services only have to agree on JSON field names. Trusted packages are
    pinned to `de.coldtea.verborum.*`. **ms_dictionary needs the same change at P2-10** — it has the
    identical problem consuming ms_user's `user.deleted`.
  - The listener logs and re-throws (per `rabbitmq.md`), so an unknown `keycloakId` is retried and
    dead-lettered rather than silently acknowledged.
  - Verified live 2026-07-23 against the running service and compose broker — the full path, no JWT
    needed since publishing to the exchange is unauthenticated:
    1. Published `dictionary.imported` **with a foreign `__TypeId__` header naming an ms_marketplace
       class that does not exist in ms_user** → vault row created with the resolved `fk_user_id`
       (`u-p209-1` from `kc-p209-1`) and a server-generated UUID. This is the INFERRED fix working.
    2. Republished the identical event twice → still exactly one row, same `vault_entry_id`
       (idempotency via P2-07's `addVaultEntry`), nothing dead-lettered.
    3. Published with an unknown `keycloakId` → retried, then landed in `verborum.dead-letter`.
    Test rows and the DLQ message were cleaned up afterwards; deleting the seeded user also removed
    its vault row, re-confirming the P2-05 FK cascade.
- [ ] `P2-10` **Consume `user.deleted` event in ms_dictionary**
  - Add queue + binding to ms_dictionary's `RabbitMQConfig`
  - Create `common/listener/UserEventListener.java` in ms_dictionary
  - On `user.deleted`: delete all dictionaries and words for that userId
  - Done when: deleting a user cascades to remove their dictionaries and words
  - **Match on the event's `keycloakId`, not its `userId`** — ms_dictionary's `fk_user_id` holds the
    JWT subject, which equals ms_user's `keycloak_id`. See the P2-08 note; matching on `userId`
    silently deletes nothing.
  - **Copy the `INFERRED` type-mapper fix from ms_user's `RabbitMQConfig` (P2-09) first.** Without
    it, ms_user's `__TypeId__` header (`de.coldtea.verborum.msuser.common.event.UserDeletedEvent`)
    is a class ms_dictionary does not have, and every message fails to deserialize.
  - The listener must be idempotent (a redelivery must not fail) and this is the task where P1-03's
    pre-commit publish race stops being theoretical for deletes — see P2-08.
- [x] `P2-11` **Fix Keycloak role mapping in ms_user `SecurityConfig`** (added 2026-07-12 after full review)
  - `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles")` does NOT
    resolve nested claims — Keycloak realm roles live under `realm_access` → `roles`, so no
    roles are ever mapped to authorities. Needs a custom converter that reads the nested claim
  - Done when: a token with realm role `user` yields authority `ROLE_user` in the security context
  - Done 2026-07-23: `SecurityConfig.extractRealmRoles(Jwt)` reads `realm_access` → `roles` by hand
    and maps each to `ROLE_{role}`; `jwtAuthConverter()` uses it instead of the built-in converter.
    5 `SecurityConfigTest` cases (module suite now 24): nested roles map to `ROLE_user`/`ROLE_admin`,
    and a missing `realm_access`, a `realm_access` without `roles`, a non-list `roles`, and
    non-string entries all yield no authorities without throwing — a malformed token must not turn
    into a 500 on the authentication path.
  - **The same bug was in the `security.md` template**, which is what P3-03 (ms_dictionary) and
    P4-01 (ms_marketplace) copy from. Fixed there too, with a warning note, so the bug is not
    re-introduced into the two services that have yet to be secured.
  - Realm roles only. Client roles (`resource_access.{clientId}.roles`) are not used in Verborum.
  - Verified against a synthetic `Jwt`, not a Keycloak-issued token — there is no Keycloak to issue
    one until P3-01/P3-02. Re-check this against a real token when the realm exists; the claim shape
    asserted here is Keycloak's documented one.

---

## Phase 3 — Add Security Layer
> Goal: Protect all endpoints with JWT authentication via Keycloak.
> Depends on: Phase 2 complete (Keycloak must be configured with ms_user)
> See `docs/agent/security.md` for full implementation details

- [x] `P3-01` **Add Keycloak to root docker-compose**
  - Image: `quay.io/keycloak/keycloak:23.0.0` on port 8180
  - Done when: Keycloak Admin UI accessible at http://localhost:8180
  - Done 2026-07-23: added to the root compose as `verborum-keycloak`, `start-dev --import-realm`,
    8180→8080, admin credentials `${KEYCLOAK_ADMIN:-admin}` / `${KEYCLOAK_ADMIN_PASSWORD:-admin}`,
    named volume `keycloak_data`, and a healthcheck that asks the realm's OIDC discovery document
    over `/dev/tcp` (the image ships no curl/wget). Verified: container reports healthy.
  - Also removed the obsolete `version:` attribute from the compose file, as the P1-01 note asked.
- [x] `P3-02` **Configure Keycloak realm and clients**
  - Create realm `verborum`
  - Create client `verborum-app` (public, for mobile)
  - Create client `verborum-backend` (confidential, for service-to-service)
  - Done when: a test user can obtain a JWT token via Keycloak
  - Done 2026-07-23: **the realm is imported from `keycloak/import/verborum-realm.json`, not
    hand-clicked** — versioned in git, so it is reproducible and reviewable. It configures realm
    `verborum`, roles `user`/`admin`, clients `verborum-app` (public, PKCE S256),
    `verborum-web` (public, PKCE S256 — Integration §6.1 names it), `verborum-backend`
    (confidential, service account), and the dev users `testuser`/`testadmin`.
  - Token policy from Integration §6.2: `accessTokenLifespan` 300s, SSO idle 30 min, offline session
    idle 60 days.
  - **Added a fourth client, `verborum-dev-cli`, which is NOT in the auth contract.** It enables
    direct access grants (password) so a developer can obtain a user token with one curl. The
    alternative — enabling password grant on `verborum-app` — would have contradicted the
    PKCE-for-every-platform spec and shipped that hole to production. It must never exist in a
    shared realm; documented as such in `security.md`.
  - The import runs **only on first start of an empty data volume**, and console changes are not
    written back to the file. To re-import: `docker compose down` +
    `docker volume rm verborum_ms_keycloak_data`.
  - `verborum-backend`'s secret in the committed file is the placeholder `local-dev-only-change-me`.
    Services still read it from `KEYCLOAK_ADMIN_CLIENT_SECRET`; nothing real is committed.
  - Not done: Google as an identity provider (needs real Google OAuth2 credentials — cannot be
    committed; add manually when they exist). Noted in `security.md`.
  - 2026-07-23, follow-up after the Android team reviewed this: three client-facing gaps closed.
    1. **Sign-up is Keycloak-hosted** — `registrationAllowed`/`resetPasswordAllowed` are now `true`
       and clients use `/protocol/openid-connect/registrations`. A native form would have required
       P3-04's Admin API path, which does not exist; hosted keeps Keycloak the identity authority.
       The client calls `POST /users/` once after first login to create the profile row.
    2. **Redirect URI recorded** as `de.coldtea.verborum://oauth2redirect/*` (+ `http://localhost:*`
       for emulators) in Integration §6.1 — it was configured here but written down nowhere.
    3. **Issuer pinned via `KC_HOSTNAME_URL`** (default `http://localhost:8180`), plus §6.2a on
       device testing. Unpinned, Keycloak echoes the caller's Host, so a phone on a LAN IP gets
       tokens every service rejects with a 401 that looks like a bad token.
    Also verified live: PKCE is genuinely enforced (a request without `code_challenge` is rejected),
    the hosted registration and reset-password pages render, and with the hostname pinned every
    token carries that one issuer regardless of the host used to request it.
    Logout/revocation was undocumented anywhere — now specified in Integration §6.1a and mirrored
    into `security.md`.
  - Verified live 2026-07-23: `testuser` obtained a token via `verborum-dev-cli`; the decoded
    payload carries `"iss":"http://localhost:8180/realms/verborum"`, a `sub`, and
    `"realm_access":{"roles":["user"]}` — the exact nested shape P2-11's converter reads.
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
- [x] `P3-07` **Document the auth contract in `security.md`** (added 2026-07-21, recommended by the
    Integration doc §11)
  - Done 2026-07-23, together with P3-01/P3-02 as the task itself asked: `security.md` has an
    "The Auth Contract" section mirroring Integration §6 — realm, the client table (including why
    `verborum-dev-cli` is excluded from the contract), PKCE-only flow, token lifetimes, realm roles,
    dev users, secret handling, and the two things §6 assumes that are not configured (Google IdP,
    guest-data migration). The Keycloak setup section was rewritten from "do once manually" to the
    realm-import workflow, since hand-clicking is no longer how the realm is built.
  - `docs/integration/frontend-backend-integration.md` §6 is the normative cross-client auth spec
    (realm `verborum`, Authorization Code + PKCE, Keycloak clients `verborum-app`/`verborum-web`,
    token policy, guest-data migration). `security.md` should carry a matching section so the
    backend and clients cannot drift on realm names, client ids, scopes, or token lifetimes
  - Do this alongside P3-01/P3-02 (realm + client configuration) so the doc and the actual Keycloak
    setup are designed together
  - Done when: `security.md` has an auth-contract section consistent with Integration §6

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
  - Read the P1-05 notes first. `word.created` fires only for genuinely new words, so the counts
    here are not self-correcting:
    1. Editing a word's translation publishes nothing — this store keeps counting the original
       and never learns the correction. If a `word.updated` event is added, it must *decrement*
       the old translation as well as add the new one, or counts drift upward forever.
    2. The listener must be idempotent. A redelivery of `word.created` must not increment twice —
       counts are the whole product here, and a DLQ replay would quietly skew rankings.
    3. **Multi-meaning shape (added 2026-07-21).** `word`/`translation` are no longer single strings
       — they are JSON arrays of per-meaning surfaces (see the canonical contract in
       `docs/integration/frontend-backend-integration.md` §4.2), and the meta object carries the
       language per side. So "aggregate by translation" is ambiguous: a `word.created` for
       `["kaufen","erwerben"]` → `["to buy","to purchase"]` is several word↔translation facts, not
       one. Decide here whether to explode each meaning into its own suggestion row and how to key
       them (surface form only, or surface + `type`). The `word.created` payload and its parser must
       be designed together with this decision — the backend stops treating word/meta as opaque at
       exactly this task. Lock the event shape before more consumers depend on it.
- [ ] `P6-04` **Implement AutofilController**
  - `GET /autofil?word=Haus&from=DE&to=EN` → returns ranked translation suggestions
  - Done when: endpoint returns community translations ordered by frequency

---

## Deferred / Backlog
> Known, low-urgency work with a clear trigger. Not blocking any phase; pull into a phase when its
> trigger fires.

- [ ] `BL-01` **Delta sync endpoint** (added 2026-07-21)
  - `GET /dictionaries/{userId}` and `GET /words/user/{userId}` return the user's entire corpus, and
    the Android sync engine re-fetches all of it on every download-merge. Fine at
    personal-vocabulary scale; wasteful on metered mobile connections and as corpora grow (e.g. after
    marketplace imports land in a user's vault)
  - Fix: add `?since=<ISO-8601>` returning only rows with `update_dt` newer than the timestamp. The
    `createdAt`/`updatedAt` fields are already on every response (commit `e9b3de6`), so this is a
    purely additive, backward-compatible change — existing full-fetch callers are unaffected
  - Trigger: sync payload sizes or read traffic become a measured problem, or marketplace import
    (Phase 4) starts adding large dictionaries to vaults
