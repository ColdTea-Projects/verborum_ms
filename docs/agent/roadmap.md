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
    user, because `fk_user_id` is a real FK and would otherwise fail at the DB as a 500. GET did no
    such check — an unknown user just had an empty vault, matching `GET /dictionaries/{userId}`.
    **Superseded by P3-05:** the ownership guard must load the profile to compare `keycloakId`, so
    GET now 404s for an unknown or unowned profile. 404 is also the consistent answer next to the
    P3-08 read rules. (Doc/code drift caught in the 2026-07-23 review.)
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
- [x] `P2-10` **Consume `user.deleted` event in ms_dictionary**
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
  - Done 2026-07-23: durable queue `dictionary.user.deleted` bound to `user.deleted` with
    `x-dead-letter-exchange`, `common/listener/UserEventListener`, consumer-side `UserDeletedEvent`,
    and `DictionaryService.deleteAllByUserId`. The `INFERRED` type-mapper fix from P2-09 was copied
    into this service's `RabbitMQConfig` first, as the note above required. 5 new tests
    (ms_dictionary suite now 41). **Phase 2 is complete.**
  - Cascades on the event's **`keycloakId`** — `fk_user_id` is the JWT subject. A test asserts
    `deleteAllByUserId` is never called with the event's `userId`, since that would delete nothing
    and look like success.
  - Words are deleted before dictionaries (no DB-level FK), asserted with `InOrder`. An empty
    dictionary list short-circuits, so a redelivery is a harmless no-op.
  - **Publishes no `dictionary.deleted` events for the cascaded rows.** ms_marketplace consumes
    `user.deleted` itself (see the routing table in `verborum.md`), so re-announcing each dictionary
    would duplicate work it already does. If that ever changes, this is the decision to revisit.
  - Verified live 2026-07-23, full cross-service chain with both services and Keycloak running:
    created an ms_user profile plus 2 dictionaries and 2 words owned by the JWT subject, then
    `DELETE /users/{userId}` over HTTP → the listener logged the event and ms_dictionary went from
    2 dictionaries / 2 words to 0 / 0, nothing dead-lettered. Republishing the identical event was
    consumed cleanly as a no-op. This is also the first live proof of the P2-09 type-mapper fix
    against a genuine cross-service message: ms_user stamps
    `__TypeId__: de.coldtea.verborum.msuser.common.event.UserDeletedEvent`, a class ms_dictionary
    does not have.
  - Still true after this task: the publish in ms_user happens before its transaction commits
    (P1-03/P4-03). The window is small and the cascade is not reversible, so P4-03's move to
    `@TransactionalEventListener(AFTER_COMMIT)` matters more now that a consumer acts on it.
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
- [x] `P3-03` **Add Spring Security to ms_dictionary**
  - Add `spring-boot-starter-security` + `oauth2-resource-server` to `pom.xml`
  - Create `common/config/SecurityConfig.java` — stateless JWT, permit actuator + Swagger
  - Add `issuer-uri` and `jwk-set-uri` to `application.properties`
  - Done when: unauthenticated requests to ms_dictionary return 401; authenticated requests still work
  - Note: ms_dictionary has been running with ALL endpoints open — this closes that gap
  - Done 2026-07-23: both starters added, `common/config/SecurityConfig` mirrors ms_user's (stateless,
    actuator + Swagger permitted, everything else authenticated) and was written against the
    **corrected** role-extraction template from P2-11 — this service never carried that bug. 5
    `SecurityConfigTest` cases; ms_dictionary suite now 46.
  - `jwk-set-uri` is set alongside `issuer-uri` deliberately: with only `issuer-uri`, Boot fetches the
    discovery document at startup and the service refuses to start whenever Keycloak is down. Both
    are `${ENV:default}`, so the LAN-issuer setup in Integration §6.2a works without code changes.
  - Verified live 2026-07-23: `POST /dictionaries/`, `GET /dictionaries/{userId}` and
    `GET /words/user/{userId}` all return **401** with no token and with a garbage token; the same
    calls with a real Keycloak token return 201/200 with correct bodies. `/actuator/health` and
    Swagger remain open by design.
  - **Authentication is enforced, ownership is not.** Endpoints still trust the `userId` in the
    request body, so any valid token can read or write another user's dictionaries. That is P3-05,
    and it is now the most security-relevant open item.
- `P3-03a` **Tell the client teams ms_dictionary is closed** — moved to Deferred / Backlog on
  2026-09-27 (a communication task that no backend work depends on; development is local-only).
- [x] `P3-09` **Post-review hardening** (added 2026-07-23 after an independent architecture review)
  - An independent review of the whole repo (a second model, working only from the committed docs and
    code) produced these. Each was verified against the source before being acted on.
  - **Unhandled exceptions no longer put `ex.getMessage()` on the wire** (both services). The
    catch-all handler returned the raw message, so a Postgres constraint violation would have handed
    the caller table, column and constraint names in a 500 body. Now a fixed `"Internal server
    error"`; the real exception is still logged. The specific handlers still return their message —
    safe, because those are our own constants.
  - **`GET /words/language/to/{language}` was missing `@SupportedLanguage`** while `/from/` had it, so
    one of a symmetric pair 400'd on a bad code and the other silently returned an empty list.
  - **`deleteAllByUserId` used `deleteAllById` for dictionaries** — which loads each entity and issues
    one DELETE per id, not a bulk `DELETE … WHERE IN`. Deleting a user with hundreds of dictionaries
    meant hundreds of round-trips holding locks in one transaction. Added
    `DictionaryRepository.deleteByDictionaryIdIn`, mirroring what `WordRepository` already had. The
    existing `InOrder` test caught the change, which is what it was for.
  - **Event `eventTimestamp` is now `OffsetDateTime`**, not zoneless `LocalDateTime` — the same
    ambiguity P0-19/P0-20 removed from entity timestamps, left behind on the event side. Harmless
    today, misleading the moment publisher and consumer run in containers with different zones, which
    is exactly when you are reading a DLQ. Wire format is still ISO-8601, now with an offset.
    **Note:** an event published *before* this change and replayed from the DLQ afterwards will fail
    to deserialize (no offset to parse). The DLQ is empty and this is dev-only, so nothing was lost.
  - **Two doc/code drifts fixed**, both of which would have misled someone trusting the prose:
    1. `GET /users/{userId}/vault` 404s for an unknown/unowned profile since P3-05, but
       `ms_user/CLAUDE.md`, the P2-07 note **and a code comment inside the method** all still claimed
       "empty list for an unknown user".
    2. Integration §3.1 said a client-supplied `userId` "is ignored by the backend". It never was —
       it is required (`@NotBlank`) and a mismatch is a 403.
  - **Web-layer smoke tests added** (`DictionaryControllerWebTest`, `UserControllerWebTest`, 14 cases).
    `contextLoads` was the only automated proof the HTTP layer worked; everything else was manual
    curl. These cover what service-level tests structurally cannot see: the filter chain (401),
    controller-level `requireSelf` (403), `@Valid` (400), exception→status mapping, and that the
    500 body does not leak. Suite total: 112.
  - Reviewed and explicitly left alone: the no-FK-for-words vs real-FK-for-tags split, the fanout DLX,
    the `INFERRED` type mapper, the cross-service identity model, the 403/404/filter ownership
    pattern, and the existing unit tests (judged to test behaviour rather than mirror the
    implementation).
  - 2026-07-23: `docs/integration/client-login-guide.md` §9 now carries the full breaking-change
    notice — 401 without a token, plus the P3-05/P3-08 ownership rules (403 on a wrong owner id, 404
    on another user's resource, filtered batch results) and the hint that a 403 most likely means the
    guest UUID is still being sent. **Left open deliberately: this task is a message, not a file.**
    It closes when the client teams have actually been told.
- [x] `P3-04` **Add Spring Security to ms_user**
  - Same pattern as ms_dictionary
  - Add Keycloak Admin Client for user registration flows
  - Done when: ms_user endpoints are protected and user registration works end-to-end
  - The security half was already done — ms_user shipped with `SecurityConfig` from P2-01 and its
    role mapping was fixed at P2-11.
  - **Rescoped 2026-07-23 (your call): the Admin Client deletes identities, it does not create
    them.** Registration is Keycloak-hosted (P3-02 follow-up), so an Admin API create path would
    duplicate a working flow and split identity ownership. What did need building is the reverse:
    `DELETE /users/{userId}` removed the profile but left the Keycloak account alive, so the person
    could still log in and simply re-register.
  - `KeycloakUserService` / `KeycloakUserServiceImpl` (client-credentials Admin API call), invoked
    from `UserServiceImpl.deleteUser` **after** the DB delete and the `user.deleted` publish — a
    failure there must not roll back a deletion already announced to the other services.
  - **Non-throwing by contract.** The profile and its cascade are gone by the time it runs, so
    failing the caller would imply nothing happened. A failure logs ERROR with the id; that line is
    the record that a manual cleanup is owed. 404 from Keycloak is treated as success (already gone).
  - With no `KEYCLOAK_ADMIN_CLIENT_SECRET` set it logs a WARN and skips, so local dev without a
    secret still works — the identity just outlives the profile, as before.
  - The realm import now grants the `verborum-backend` service account `manage-users` + `view-users`
    on `realm-management`. Re-importing the realm requires the volume wipe documented in
    `security.md`.
  - Verified live 2026-07-23: service account obtains a token and shows both roles; a throwaway
    Keycloak identity plus its profile, deleted via `DELETE /users/{userId}`, left the account
    **404 in Keycloak** and 0 profile rows. Re-run without the secret: delete still returns 200, the
    identity survives, and the WARN names the id.
  - Known limitation: a failed identity deletion is only a log line. If account deletion becomes a
    compliance requirement, this wants an outbox/retry rather than best-effort.
- [x] `P3-05` **Extract userId from JWT in controllers (stop trusting client-provided userId)**
  - Create `common/utils/SecurityUtils.java` in each secured service
  - Update create/mutate endpoints to use `SecurityUtils.getCurrentUserId()`
  - Done when: userId in Dictionary and Word always comes from the token, not request body
  - Done 2026-07-23. **A mismatch is a 403, not a silent substitution** (your call): a client sending
    the wrong id has a bug, and quietly rewriting it would let that bug ship looking healthy.
  - Controllers pass the token subject into the services as an explicit argument rather than the
    services reading the security context — the services stay plain objects, unit-testable without a
    SecurityContextHolder.
  - ms_dictionary: `saveDictionary`/`saveWords` take an `ownerId`; the stored `userId` always comes
    from the token. Because the **client supplies the ids**, an existing row is checked too —
    otherwise a POST carrying someone else's `dictionaryId` would take that row over, and a word
    bundle could be written into another user's dictionary. `GET /dictionaries/{userId}` and
    `GET /words/user/{userId}` keep the path variable for compatibility but must name the caller.
  - ms_user: the subject is the profile's **`keycloakId`**, not ms_user's `userId`, so ownership
    compares against that column. `saveUser` refuses to claim another subject (keycloak_id is the
    cross-service join key — claiming it would hand over that user's dictionaries too) and refuses to
    overwrite an existing profile that is not the caller's. Vault methods are guarded;
    `importDictionary` deliberately is not, since its actor is ms_marketplace.
  - Verified live with two real Keycloak users: claiming another subject, overwriting another user's
    dictionary, listing their dictionaries, reading their words and writing into their dictionary are
    all 403, with the victim's data intact.
- [x] `P3-08` **Close the id-addressed authorisation holes (IDOR)** (added 2026-07-23 while verifying
    P3-05 — scope beyond the original Phase 3 plan, see the note below)
  - P3-05 fixed the endpoints where the client *names* a user, but every endpoint addressed by
    **resource id** was still unguarded. Demonstrated live before the fix: user B called
    `DELETE /dictionaries/{A's id}` and **A's dictionary and words were destroyed** (200 OK). B could
    also read A's dictionary by id, batch-fetch it, list its words, and
    `GET /words/language/from/EN` returned **every user's** words.
  - Fixed: `deleteDictionary`, `deleteWordsByDictionaryId` → 403 for a non-owner.
    `getDictionaryById` → **404**, not 403, so a caller cannot tell an existing dictionary from a
    missing one. Batch/list endpoints (`/dictionaries/batch`, `/words/batch`,
    `/words/dictionary/{id}`, both `/words/language/...`) **filter to the caller** rather than
    refusing, for the same reason. `deleteWords` silently skips ids the caller does not own.
  - Verified live after the fix, same two users: read-by-id 404, every batch/list empty, both deletes
    403, single-word delete 200 but nothing removed, and A's data intact.
  - Scope note: this was not in the Phase 3 plan. It is committed separately from P3-05 so it can be
    reviewed — or dropped — on its own. It was fixed rather than filed because Phase 3 is the
    security phase, and shipping it with a working "delete any user's data" call would have been
    worse than the scope creep.
  - Not covered: ms_user has no id-addressed endpoints beyond the profile and vault ones already
    guarded in P3-05.
- [x] `P3-06` **Lock down actuator exposure in all services** (added 2026-07-12 after full review)
  - `management.endpoints.web.exposure.include=*` combined with `permitAll` on `/actuator/**`
    exposes `/actuator/env`, heapdump, etc. publicly — in ms_user this can leak
    `KEYCLOAK_ADMIN_CLIENT_SECRET` via the env endpoint
  - Restrict to `health,info` (or secure the rest with a role)
  - Done when: `/actuator/env` is not publicly reachable in any service
  - 2026-07-23: confirmed live, no longer theoretical — with ms_dictionary secured, an
    **unauthenticated** `GET /actuator/env` still returns 200 and lists configuration keys including
    `spring.datasource.password`. `permitAll` on `/actuator/**` plus `exposure.include=*` is doing
    exactly what it says. Both services are affected; ms_user additionally has
    `KEYCLOAK_ADMIN_CLIENT_SECRET` in its environment.
  - Done 2026-07-23: `management.endpoints.web.exposure.include=health,info` in both services.
    `/actuator/**` stays `permitAll` — health and info are meant to be reachable, and restricting
    the *set* is the fix rather than authenticating a set that should not exist. Verified live:
    `env`, `beans`, `heapdump` and `configprops` all 404; `health` and `info` still 200.
  - Found while verifying: unmapped paths were returning **500**, not 404. Spring 6 raises
    `NoResourceFoundException`, which had no handler and fell into the generic `Exception` one — so
    hiding the actuator endpoints turned them into fake server errors, and any typo'd URL looked
    like a backend fault. Added a `NoResourceFoundException` handler (404, logged at WARN since an
    unknown URL is a client mistake) to **both** services' `GlobalExceptionHandler`. Same class of
    bug as P0-14 and P0-07.
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

## Phase 3B — OAuth2 Hardening (SSO, email verification, passwordless login)
> Goal: Social sign-in (Google + Facebook), required email verification, and a passwordless
> email-code login option — all federated behind Keycloak, so clients change nothing.
> Depends on: Phase 3 complete (Keycloak realm live, imported from git).
> Branch: `oauth2-hardening`. Scope agreed 2026-07-28. See `docs/agent/security.md`.
>
> **Design constraints that shaped this phase:**
> - Everything is Keycloak realm config — no new endpoints, no client app changes.
> - **Native `--import-realm` cannot reliably substitute `${ENV}` in the realm JSON**
>   (Keycloak #12069/#26275). So secrets + per-environment values are injected AFTER import by
>   `keycloak/bootstrap/configure.sh` via `kcadm.sh`. Never put a secret in the committed realm JSON.
> - Social providers federate behind Keycloak — never integrate a social SDK on the client.
> - Apple was considered and dropped (paid dev account + rotating signed-JWT secret not worth it).

- [x] `P3B-01` **Local SMTP via Mailpit + env-var scaffolding** (done 2026-07-28)
  - Added `mailpit` to the root compose (SMTP 1025, web UI http://localhost:8025, accepts anything).
  - Added `.env.example` documenting every env var (DB/RabbitMQ/Keycloak + new SMTP + Google/FB) and
    git-ignored `.env`. Local dev needs none of them — all have safe defaults.
  - Done when: mail sent by Keycloak locally lands in the Mailpit UI. (Verify once P3B-02 lands.)
- [x] `P3B-02` **Require email verification** (done 2026-07-28)
  - `verifyEmail: true` in the realm import; realm `smtpServer` points at the Mailpit container
    (non-secret, committed). A new hosted sign-up must confirm the address before obtaining tokens.
  - Done when: registering a new user through hosted sign-up receives a verification mail in Mailpit
    and cannot get a token until the link is clicked.
- [x] `P3B-03` **Env-driven secret bootstrap (kcadm.sh)** (done 2026-07-28)
  - `keycloak/bootstrap/configure.sh` + the short-lived `keycloak-bootstrap` compose service:
    idempotent, no-op on a laptop, injects Google/Facebook IdP credentials and (staging/prod) the
    real SMTP override from env vars after the realm is healthy.
  - Done when: `docker compose up` runs the bootstrap clean with an empty `.env` (configures nothing)
    and, with Google/FB env vars set, the providers appear on the login page.
- [x] `P3B-04` **Enable Google sign-in** — **done 2026-07-28, browser-verified**
  - Google OAuth client `verborum-keycloak` (project `verborum-503810`), redirect URI
    `http://localhost:8180/realms/verborum/broker/google/endpoint`. Id/secret live in git-ignored
    `.env`; the bootstrap creates the `google` IdP from them (`trustEmail=true`, scope
    `openid profile email`). Separate OAuth app per environment (redirect host differs).
  - Verified live via real Chrome: "Continue with Google" → Google consent → Keycloak broker →
    `302` + auth code, and a federated user (`verborum2026@gmail.com`) was provisioned in the realm.
  - Login-theme polish: hid Keycloak's default monochrome provider glyph so only the injected
    4-colour Google mark shows (was doubled).
- [x] `P3B-05` **Enable Facebook sign-in** — **wired + browser-verified 2026-07-28** (public access gated on Meta App Review)
  - Meta app "Verborum" (App ID `1778675863140525`), `providerId=facebook`, scope `email public_profile`,
    `trustEmail=true`. Id/secret in git-ignored `.env`; bootstrap creates the `facebook` IdP. Localhost
    redirect is auto-allowed in Meta dev mode, so no redirect URI to register locally.
  - Verified live: "Continue with Facebook" button renders (blue f mark) and Keycloak builds the correct
    OAuth request (right App ID, redirect, scopes). **Full consent round-trip not completed in-session.**
  - **Two gates remain for real users:** (a) app is in **Meta dev mode** → only app roles (admin/dev/
    tester) can log in until **App Review** approves the `email` scope; (b) each teammate's FB account
    must be added as a **Tester** on the Meta app.
- [x] `P3B-07` **Brand the hosted login page (Keycloak theme)** (done 2026-07-28)
  - `keycloak/themes/verborum/login/` — extends the stock `keycloak` theme, layers a stylesheet
    mirroring the Android design language (`core/theme/Color.kt`): crimson accent `#C41E3A`/`#E63946`,
    gold secondary, near-black/white surfaces, light **and** dark via `prefers-color-scheme`.
    Mounted into the container (dev) and applied via `loginTheme=verborum` in the realm import + the
    bootstrap. Prod bakes it into the image (COPY). **Zero client work** — the hosted page Android
    already opens is simply branded.
  - **Polish pass (2026-07-28), all browser-verified:** self-hosted **Roboto** (matches the app's
    Material type); **Verborum favicon** (crimson/gold "V"); real 4-colour Google + blue Facebook
    marks with Keycloak's default glyph hidden and even spacing; social buttons restyled as soft
    filled buttons (killed PatternFly's `::after` hover line + the lopsided border); crimson submit
    on the password screen; styled password reveal button; branded/themed **email** theme + logout
    page; colourless borderless "New user?/Register" footer; removed the near-white social divider.
  - Done when: the login/registration/verify-email pages render in Verborum colours in both schemes.
- [x] `P3B-06` **Passwordless email-code login** (hand-written SPI + custom flow) — **done 2026-07-28, browser-verified**
  - **Decided 2026-07-28: hand-write a minimal authenticator SPI** (not a community jar, not deferred)
    — keeps the auth path fully owned, no supply-chain risk, guaranteed KC23 compat; ~2 small classes.
  - Keycloak 23 has no native email-OTP authenticator: build the SPI into a custom Keycloak image +
    add a custom browser flow offering password OR email-code as alternatives, the code path gated on
    `emailVerified`. Full design + build steps + rollback in
    `keycloak/passwordless-email-code/README.md`. Staged last: a mis-bound flow risks lockout.
  - Done when: on a throwaway realm, a verified user can log in with a password OR an emailed code,
    the code option is hidden for an unverified email, and `testuser`/`testadmin` still log in — then
    the shared realm's `browserFlow` is switched over.
  - **Progress 2026-07-28 — SPI built and verified working, flow gated OFF pending browser QA:**
    - Hand-written authenticator SPI (`keycloak/passwordless-email-code/`, provider
      `verborum-email-code`) + custom Keycloak image (`verborum-keycloak:local`) that bakes it in.
      6-digit code, 5-min TTL, 3 attempts, resend; self-gates on a verified email.
    - Login form (`login-email-code.ftl`) + branded code email (`email-code.ftl`) + messages.
    - **Verified live end-to-end:** with email-code as the active first factor, username → emailed
      code (delivered to Mailpit) → validation → `302` with an OAuth authorization code. Password
      login also verified. Clean `docker compose up` reproduces the SPI + custom image.
    - **Browser-verified 2026-07-28 (via real Chrome):** username → password screen → "Try another
      way" lists **Password** and **"Email me a sign-in code"** → picking the code emails it (Mailpit)
      → entering it logs in (`302` + OAuth code). Password path also works. The earlier scripted-HTTP
      doubt about the selection listing was a headless-tooling artifact — the option lists fine in the
      real redirect-based UI. Polish applied: friendly selection labels (message keys), apostrophe
      escaping, crimson submit button on the password screen (input-scoping fix), styled password
      reveal button. **Enabled by default** now (`EMAIL_CODE_ENABLED=true`);
      `configure-email-code-flow.sh` builds + binds the flow on boot.
- [x] `P3B-08` **Auth hardening** (added 2026-07-28; closed 2026-09-27 — the three open items were
  split out, see below)
  - [x] **Brute-force detection** (done 2026-07-28): realm now has `bruteForceProtected: true`,
    `failureFactor: 5`, temporary lockout (`permanentLockout: false`), `waitIncrementSeconds: 60`,
    `maxFailureWaitSeconds: 900`. **Verified live:** 5 wrong passwords for `testuser` locked the account
    (even the correct password was refused with the generic "Invalid user credentials"); admin unlock
    via `attack-detection/brute-force/users/{id}` restored it.
  - [x] **Email-code resend cooldown** (done 2026-07-28): `EmailCodeAuthenticator` now enforces a
    30-second cooldown and a 3-per-session cap on "Send a new code". **Verified live:** an immediate
    resend shows "Please wait N seconds…" and sends no extra mail.
  - [x] **Password policy** (done 2026-07-28): `length(8) and notEmail`. NOTE: `notUsername` was left
    out because the throwaway dev users (`testuser`/`testadmin`) have password == username and would
    fail validation on import — **add `notUsername` (and stronger complexity) in the prod realm**,
    which has no such users.
  - **Split out 2026-09-27** (decided by the project owner). Development is local-only, where none of
    these three protect anything, and edge rate-limiting could not be done before Phase 5 anyway —
    left here, it would have blocked Phase 4 forever under the "never skip phases" rule:
    - Registration bot protection (reCAPTCHA) → `BL-02`, trigger: before public sign-up opens
    - Secret rotation before any shared/prod realm → `BL-03`, trigger: before any non-local realm
    - Edge rate-limiting + TLS → `P5-04`, lands with the gateway

---

## Phase 4 — Build ms_marketplace
> Goal: Public dictionary listings, stats, ratings.
> Depends on: Phase 3 complete (needs secured ms_dictionary events flowing via RabbitMQ)

- [x] `P4-01` **Scaffold ms_marketplace module**
  - Done 2026-09-27: module registered in the aggregator pom; `SecurityConfig` + `SecurityUtils` +
    `GlobalExceptionHandler` + response envelope copied from the existing services; empty Liquibase
    master changelog; `db_market` (5434, `vdbmarket`) added to the root compose and a per-module
    compose (Postgres 5434 + Adminer 8082). **Verified live:** `/actuator/health` 200 `UP`; no token
    and a garbage token both 401; a real Keycloak token passes security and gets a 404 (no endpoints
    yet); `/actuator/env` 404. `./mvnw -pl ms_marketplace test` — 6/6 green.
  - Deliberately **not** included: `spring-boot-starter-amqp` / `RabbitMQConfig` (arrive with the
    first consumer at P4-03) and `@SupportedLanguage` (arrives with the language filter at P4-06)
  - Port: 8087, base package: `de.coldtea.verborum.msmarketplace`
  - DB: `vdbmarket` on port 5434, Adminer on 8082
  - **Include Spring Security + Keycloak JWT from the start** — see `docs/agent/security.md`
  - Add `spring-boot-starter-security` + `oauth2-resource-server` to `pom.xml`
  - Create `common/config/SecurityConfig.java` alongside the initial scaffold
  - Done when: app starts on port 8087 AND unauthenticated requests return 401
- [x] `P4-02` **Design DictionaryStats entity + migration**
  - Fields: `dictionaryId`, `userId`, `name`, `fromLang`, `toLang`, `importCount`, `viewCount`, `rating`, `publishedAt`
  - Done when: table `dictionary_stats` created on startup
  - Done 2026-09-27: `DictionaryStats` entity + `DictionaryStatsRepository` in the `dictionarystats`
    package; migration `2026/09/27-01-changelog.json`. **Verified:** Liquibase applied it on boot;
    `\d dictionary_stats` shows the columns, PK and three indexes; module tests 6/6 green.
  - **Decisions (signed off by the project owner 2026-09-27):**
    1. **`rating` and `viewCount` left out.** Nothing records a view or accepts a rating, and the
       rating scale and one-rating-per-user rule are undecided. They get their own migration when
       designed — an always-zero column would hide the question rather than answer it.
    2. **Added `source_updated_at`** — ms_dictionary's `updatedAt` for the held values, the rule-4
       ordering key. Distinct from `update_dt` (when this row was written).
    3. **`dictionary_id` is the PK** (ms_dictionary's id, no DB FK), so consumers upsert on it.
    4. **`published_at` comes from the event**, not the insert, so a listing recreated by
       reconciliation keeps its original date.
    5. Indexes: `(from_lang, to_lang)` for the language filter, `import_count` for the popular sort,
       `published_at` for newest-first browse.
    6. **The reconciliation job moved to `P4-03`** — it needs RabbitMQ in ms_marketplace, which
       arrives with the first consumer there. Design recorded under `P4-03`.
- [x] `P4-03` **Consume `dictionary.visibility.public` event**
  - On event: create a `DictionaryStats` record for the dictionary
  - Done when: making a dictionary public creates a marketplace entry
  - Done 2026-09-27, with both obligations the 2026-07-23 decision attached — `dictionary.updated` and
    the reconciliation job:
    - **ms_dictionary:** publishes `dictionary.updated` when a public dictionary that stays public
      changes `name`/`fromLang`/`toLang` (old values captured *before* `saveAndFlush` merges onto the
      managed instance); publishes `dictionary.snapshot` via `DictionarySnapshotScheduler`
      (`DICTIONARY_SNAPSHOT_CRON`, nightly 03:00); `idx_dictionaries_is_public`
      (`2026/09/27-01-changelog.json`). Suite 86/86.
    - **ms_marketplace:** AMQP + `RabbitMQConfig` (three dead-lettered queues, `INFERRED` converter);
      `DictionaryEventListener` → `DictionaryStatsService`: `publishListing` (upsert),
      `updateListing` (update-only — never re-lists), `reconcile` (create / correct / remove-if-older-
      than-`takenAt`). All drop stale deliveries (rule 4). Suite 27/27.
    - **Verified live** (new ms_dictionary on :18085, snapshot cron every 30 s): public create → listing
      created (`import_count` 0, `published_at` = `source_updated_at`); rename + `toLang` change →
      listing updated in place, `published_at` kept; unchanged re-save → no `dictionary.updated`;
      snapshot recreated a deleted listing, removed a fake orphan, corrected a stale name; deleting the
      dictionary removed its listing at the next snapshot. DLQ empty before and after.
    - **Left for P4-04:** a dictionary made private between the snapshot query and `reconcile` got
      re-listed until the next snapshot. **Closed by P4-04** (hidden rows instead of deletes).
    - **Found:** `docs/agent/verborum.md`'s routing table and ms_dictionary's `deleteAllByUserId`
      both say ms_marketplace consumes `user.deleted`, but no task built it. **Added to `P4-05`**
      on 2026-09-27.
  - **The AFTER_COMMIT work is DONE (2026-07-23), in both services** — it was pulled forward out of
    this task because ms_dictionary already acts on `user.deleted` by deleting data, so the phantom-
    event window was live, not theoretical. Publishers raise an `OutboundEvent` and
    `OutboundEventPublisher` sends it after commit; ms_user does the same for the Keycloak identity
    deletion. `UserDeletedAfterCommitTest` asserts a rollback publishes nothing. **Nothing about
    publishing remains for P4-03 to do.**
  - **Still open, and it is an architecture call for the Phase 4 owner: the stale-listing problem.**
    A renamed public dictionary emits nothing, so a listing's `name`/`fromLang`/`toLang` drift.
    - The earlier recommendation here was "re-read from ms_dictionary on access". **That was
      withdrawn on 2026-07-23** after re-reading this phase's own spec: `P4-02` stores `name`,
      `fromLang`, `toLang`, and `P4-06` needs a *paginated* language filter and a popularity sort.
      You cannot filter, sort or page in the database on fields you do not store, and re-reading
      would also hit the P3-08 ownership filter, which returns nothing to a service account.
    - **DECIDED 2026-07-23 (signed off by the project owner): keep the local copy — it is a read
      model — and add a `dictionary.updated` event.** Marketplace serves browse entirely from its own
      table and never calls ms_dictionary at request time.
    - The deciding argument was **independent deployment**: the services run together today, but that
      is explicitly temporary (Phase 5), and a marketplace that cannot serve without ms_dictionary
      inherits every one of its restarts and outages the moment they are deployed separately.
      Alongside the two structural reasons: you cannot sort/filter/paginate on fields you do not
      store (P4-06 needs both), and a service-account read would be blocked by the P3-08 ownership
      filter.
    - **What this obliges, all of it required for the decision to actually work:**
      1. ms_dictionary publishes a new `dictionary.updated` when a **public** dictionary's
         marketplace-relevant fields (`name`, `fromLang`, `toLang`) change. Today's publisher fires
         only on a visibility *flip*, so a rename emits nothing — that is the gap being closed.
      2. The consumer **upserts on `dictionaryId`**, never inserts (rule 3).
      3. The consumer **honours `updatedAt`** and drops anything not newer than what it holds
         (rule 4). The field is already on `DictionaryVisibilityEvent` as of 2026-07-23; the new
         event must carry it too.
      4. The reconciliation job ships **with** the projection (rule 6), not after the first drift.
    - Reversible in one direction cheaply: if a detail view ever needs guaranteed-current data, read
      that single dictionary live for that screen. Browse stays on the local copy. Nothing about this
      decision has to be undone to do that.
  - **This task ships the reconciliation job** (rule 6; moved here from `P4-02` on 2026-09-27): a
    periodic re-sync of public dictionaries into the projection. It is the backstop both for a lost
    event (the window AFTER_COMMIT deliberately accepts) and for drift if an update is ever missed.
    - **DECIDED 2026-09-27 (signed off by the project owner): a `dictionary.snapshot` event, pushed
      by ms_dictionary.** On a schedule, ms_dictionary publishes ONE message carrying every public
      dictionary's listing payload (incl. `updatedAt`); ms_marketplace diffs it against
      `dictionary_stats` in one transaction — create missing, update where the snapshot's
      `updatedAt` is newer, delete listings absent from the snapshot.
    - **Rejected: an internal pull endpoint** (`GET /internal/dictionaries/public` + a service role +
      client credentials in ms_marketplace). Simpler diff, but it is the first synchronous
      service-to-service call (against `service-boundaries.md`), adds a role and a secret, and ties
      the job to ms_dictionary's uptime.
    - **Schedule: nightly**, configurable — e.g. `${DICTIONARY_SNAPSHOT_CRON:0 0 3 * * *}` in
      ms_dictionary. The snapshot only repairs *lost* events; normal listings still appear within
      seconds via `dictionary.visibility.public`. Nightly means a lost event can leave a listing
      wrong until the next run. Set the cron to every minute locally when testing.
    - Size: ~200 bytes per listing (~2 MB at 10k public dictionaries). Fine for RabbitMQ; chunk it
      if it grows — which also means the diff can no longer be one transaction, so revisit then.
    - ms_dictionary has **no index on `is_public`** — add one (new changeset) for the snapshot query.
    - When ms_dictionary runs more than one instance (Phase 5+), the scheduled publish needs a lock
      (e.g. ShedLock) so only one instance sends the snapshot.
  - Read the P1-03 notes first — this is the task where two known issues stop being theoretical:
    1. ms_dictionary publishes *before* its transaction commits, so a listener that calls back
       into ms_dictionary can beat the commit. Switch ms_dictionary to
       `@TransactionalEventListener(phase = AFTER_COMMIT)` as part of this task. The event
       already carries the full listing payload, so the consumer should not need a callback.
    2. Visibility events fire on change only — a renamed public dictionary emits nothing, so a
       listing's `name`/`fromLang`/`toLang` will go stale. Decide here: either add a
       `dictionary.updated` event or have the listing re-read on access.
  - Make the listener idempotent regardless: a redelivery must not create a second listing.
- [x] `P4-04` **Consume `dictionary.visibility.private` event**
  - On event: remove or deactivate the `DictionaryStats` record
  - Done when: making a dictionary private removes it from marketplace
  - Done 2026-09-27. **Decided (project owner): hide, don't delete.** New `is_listed` column
    (`2026/09/27-02-changelog.json`, NOT NULL DEFAULT true). Going private sets `is_listed = false`
    and keeps the row with its `source_updated_at`, so older "public" state arriving late — a delayed
    `visibility.public`, or a snapshot read just before the flip — compares as stale and cannot
    re-list it. This closes the gap P4-03 left open.
    - `hideListing`: newer event hides (keeps `import_count` and `published_at`); no row yet → creates
      a hidden row (the private event overtook the public one); stale/redelivered → no-op.
    - Every "public" path (`publishListing`, `updateListing`, snapshot entries) re-lists a hidden row
      when newer, resetting `published_at` — re-published counts as newly published. `updateListing`
      may re-list because `dictionary.updated` is only sent for public dictionaries.
    - The snapshot removes hidden rows older than `takenAt` (cleanup — otherwise one row per
      dictionary ever made private). Residual risk: a manual DLQ replay of an old public event after
      that cleanup re-lists until the next snapshot. Accepted.
    - **Verified live** against the IntelliJ-run services (devtools reloaded ms_marketplace):
      public → listed; private → row kept, `is_listed = f`; public again → listed, `published_at`
      reset, `import_count` kept (5); private, then a hand-published **older** `visibility.public`
      via the management API → stayed hidden, name unchanged. DLQ empty. Suite 39/39.
- [x] `P4-05` **Consume `dictionary.deleted` and `user.deleted` events**
  - On `dictionary.deleted`: remove the `DictionaryStats` record
  - On `user.deleted` (added 2026-09-27): remove every listing owned by that user
    - **Why here:** ms_dictionary's `user.deleted` cascade deliberately publishes no
      `dictionary.deleted` for the rows it removes, because the routing table has always said
      ms_marketplace consumes `user.deleted` itself — but no task built it. Without it, a deleted
      user's listings stay browsable until the next snapshot (up to a day), and importing one would
      point at a dictionary that no longer exists.
    - **Match on the event's `keycloakId`, not its `userId`.** `dictionary_stats.fk_user_id` is the
      JWT subject, which is ms_user's `keycloak_id`; `userId` is ms_user's own primary key and matches
      nothing here — the delete would affect zero rows and report success (messaging rule 3).
    - New durable queue `marketplace.user.deleted` with `x-dead-letter-exchange`, bound to
      `user.deleted`; copy `UserDeletedEvent` (`userId`, `keycloakId`, `eventTimestamp`) into
      ms_marketplace. Listener in `common/listener/UserEventListener` (one class per source service),
      delegating to one `DictionaryStatsService` method that bulk-deletes by `fk_user_id`. Add an
      index on `fk_user_id` in a new changeset for that delete.
    - Idempotent by construction: a redelivery finds no rows and does nothing.
    - Interaction with P4-04: if private dictionaries keep a hidden row, `user.deleted` removes
      those too — the user is gone, so nothing needs to be remembered.
  - Done when: deleting a dictionary removes its marketplace entry, and `DELETE /users/{userId}` on
    ms_user removes all of that user's listings without waiting for a snapshot
  - Done 2026-09-27:
    - **`dictionary.deleted` hides rather than deletes** — the same guard as P4-04, going slightly
      beyond "remove the record" (proposed before building; the snapshot deletes the hidden row
      later). The event has no `updatedAt`, so its `eventTimestamp` — ms_dictionary's clock, taken in
      the deleting transaction, later than any `updatedAt` the dictionary had — becomes the row's
      `source_updated_at`, and a late older public event cannot re-list a dictionary that no longer
      exists. With no row, nothing happens: the event lacks name/languages for a hidden row, so a late
      public event could list it until the next snapshot removes it.
    - **`user.deleted` deletes** every row of the user, listed or hidden, matched on **`keycloakId`**.
      Not hidden: the event is timed on ms_user's clock, which cannot be compared with ms_dictionary's
      `updatedAt`, and ms_dictionary deletes the same user's dictionaries on the same event, so no
      newer public state can follow. `idx_dictionary_stats_user` (`2026/09/27-03-changelog.json`).
    - Queues `marketplace.dictionary.deleted`, `marketplace.user.deleted` (both dead-lettered);
      `DictionaryEventListener.handleDictionaryDeleted`, new `UserEventListener`. Suite 49/49.
    - **Verified live** against the IntelliJ-run services: public → deleted through ms_dictionary →
      row hidden with the deletion time; a hand-published older `visibility.public` → stayed hidden.
      `user.deleted` hand-published for a made-up subject (a real account was not deleted — that would
      wipe `testuser`'s dev data) → both of its rows (one listed, one hidden) deleted, a bystander's row
      kept, redelivery a no-op. DLQ empty.
- [x] `P4-06` **Implement MarketplaceController**
  - **Every browse query must filter `is_listed = true`** (P4-04) — hidden rows are private
    dictionaries. Consider leading the browse indexes with `is_listed`, or partial indexes
    `WHERE is_listed`, once the query shapes are known
  - `GET /marketplace/dictionaries` — list all public dictionaries (paginated)
  - `GET /marketplace/dictionaries/popular` — sorted by import count
  - `GET /marketplace/dictionaries/language?from=EN&to=DE` — filter by language pair
  - `POST /marketplace/dictionaries/{dictionaryId}/import` — import dictionary to vault
  - Done when: all endpoints work and publish/consume events correctly
  - Done 2026-09-27 — the three browse endpoints plus a fourth. **Decisions (project owner):**
    1. **Own paging envelope** `PageResponse {items, page, size, totalElements, totalPages}`, not
       Spring's serialized `Page` (~15 fields, a shape Spring Data itself flags as unstable across
       versions). Zero-based `page` (default 0), `size` default 20, capped at 100. Stable sort with
       `dictionaryId` as the last key so ties cannot swap between pages. The first paged contract in
       Verborum — reuse it.
    2. **Import endpoint moved to P4-07**, where the event it publishes lives.
    3. **Listings carry `publisherId`** (the owner's subject) and a fourth endpoint,
       `GET /marketplace/dictionaries/publisher/{publisherId}`, serves "more from this publisher".
       Checked: the subject grants no access anywhere (ownership always comes from the token). Display
       names are a separate task (BL-04).
    4. **Language codes normalized to uppercase in ms_marketplace** — consumers uppercase on write
       (`Locale.ROOT`), `2026/09/27-04-changelog.json` uppercased existing rows, and the filter is
       validated with a working `@SupportedLanguage` and uppercased. ms_dictionary untouched.
    - Also: 400 handlers for `HandlerMethodValidationException`, `MissingServletRequestParameterException`
      and `MethodArgumentTypeMismatchException` (all three were falling through to the catch-all 500).
    - **Verified live** against the IntelliJ-run service: list / popular (7, 3, 0) / lowercase language
      filter / publisher with paging / unknown publisher → empty page; `XX`, `size=101`, `page=abc` →
      400; no token → 401. Suite 67/67 (11 new web-slice tests).
    - **Found, not fixed:** `@SupportedLanguage` and `@ValidUUID` are **inert in ms_dictionary and
      ms_user** — see `P4-09`.
- [x] `P4-07` **Import endpoint + publish `dictionary.imported` from ms_marketplace**
  - `POST /marketplace/dictionaries/{dictionaryId}/import` (moved here from P4-06 on 2026-09-27) —
    only a **listed** dictionary is importable (a hidden row is private or deleted → 404)
  - On import: publish event so ms_user can add to vault, and increment `import_count`
  - Done when: importing triggers a vault entry in ms_user
  - Done 2026-09-27. **Decisions (project owner):**
    1. **Import = a reference** (the P2 vault model) — the importer sees the owner's latest version.
       This needs public dictionaries to be readable in ms_dictionary, which today 404s any
       non-owner → **P4-10**. Until P4-10 lands, an imported dictionary cannot be opened.
    2. **`import_count` counts unique importers.** New `dictionary_imports` table
       (`2026/09/27-05-changelog.json`), UNIQUE (dictionary, user), real FK to `dictionary_stats` with
       ON DELETE CASCADE. The count rises only on a first import, via an atomic
       `UPDATE ... SET import_count = import_count + 1` (no lost updates between concurrent importers).
    3. **Importing your own dictionary → 400** (`SelfImportException`); the clients prevent it.
    - Hidden (private/deleted) or unknown → 404. `dictionary.imported` is published after commit on
      every successful call — ms_user's vault is idempotent, and a re-send repairs a lost first event.
      `OutboundEvent`/`OutboundEventPublisher` copied into ms_marketplace (its first publisher).
    - `user.deleted` now also deletes the user's import records (counts stay — history).
    - Known edge: a concurrent double-tap by the same user can make the second transaction fail on the
      UNIQUE constraint (500); a retry takes the idempotent path. Counts are never wrong.
    - **Verified live, all three services:** import → 201, `import_count` 0→1, one import row, ms_user
      vault entry created; re-import → 201, count still 1, vault unchanged; unknown → 404; own → 400;
      private → 404. DLQ empty. testuser had no ms_user profile, so a temporary one was created and
      removed afterwards directly in the database (not via `DELETE /users/`, which would cascade).
      Suite 79/79.
- [x] `P4-08` **Dictionary tags** (requested 2026-07-23; built ahead of the phase)
  - Built now rather than with the rest of Phase 4 because the clients can start attaching tags
    immediately, and the data is only useful once it has accumulated — the marketplace and the AI
    word-prediction work (Phase 6) both consume it later.
  - `dictionary_tags` table (`2026/07/23-01-changelog.json`): `tag_id` PK, `fk_dictionary_id`,
    `tag`, `creation_dt`. **`tag` is `TEXT` with no length cap** — it shipped as `VARCHAR(50)` and was
    widened the same day at your request (`2026/07/23-02-changelog.json`); only "not blank" is
    validated now. The one remaining ceiling is Postgres's btree index row limit (~2704 bytes) via the
    unique constraint, so a pathological tag fails at the index instead of at validation. Plus the `tag` slice: entity, repository, DTOs, MapStruct
    mapper, service + impl, controller. 9 new tests (ms_dictionary suite now 65).
  - Endpoints, deliberately separate from the dictionary payload so tagging does not require
    re-sending or racing with the whole dictionary:
    `GET`/`POST /dictionaries/{dictionaryId}/tags`, `DELETE /dictionaries/{dictionaryId}/tags/{tag}`.
  - **Real DB FK with `ON DELETE CASCADE`** — as requested, and the first one in this service. It does
    not contradict the `word → dictionary` "no FK" rule: words are split-ready, a tag is a
    same-service satellite with no life of its own. Deleting a dictionary (directly or via the
    `user.deleted` cascade) removes its tags at the database.
  - **Decisions worth reviewing:**
    1. `tag_id` is **server-generated**, not client-supplied like `dictionaryId`/`wordId` — clients
       send a string, they do not track tag identity. Same call as `VaultEntry`.
    2. Tags are **normalised (trimmed + lower-cased)** on write and on delete. They are grouping keys
       for marketplace browse and AI aggregation, so `Food`/`food `/`FOOD` must be one tag. Reverse
       this if the product ever wants tags rendered exactly as typed — that would want a separate
       display column rather than dropping normalisation.
    3. `UNIQUE (fk_dictionary_id, tag)` makes adding idempotent; re-adding returns the existing row.
    4. Tags are **not** included in `DictionaryResponseDTO`. Keeping the read contract untouched
       avoids a join on every dictionary read and a client-contract change — but it means the sync
       engine needs a second call per dictionary. Revisit if that becomes a measured problem.
  - Ownership follows the dictionary (P3-05/P3-08): writes on someone else's dictionary 403, reads 404.
  - Verified live 2026-07-23: table created with the FK (`ON DELETE CASCADE`), unique constraint and a
    `tag` index; adding `"Travel"` then `"  FOOD "` stored `travel`/`food`; re-adding `"travel"`
    returned the existing row (still 2 rows); `DELETE .../tags/Travel` removed `travel`; a blank tag
    is 400; a non-owner got 404/403/403 on list/add/delete; and **deleting the dictionary left 0 tag
    rows**.
  - Not built (say the word if wanted): a "find dictionaries by tag" lookup. That is really a
    marketplace query and belongs with P4-06, not in ms_dictionary.
- [x] `P4-09` **Make `@SupportedLanguage` and `@ValidUUID` actually validate** (found 2026-09-27 at P4-06)
  - **The bug:** in ms_dictionary and ms_user both annotations are plain annotations with no
    `@Constraint(validatedBy = …)`, so Bean Validation never runs their validators. Verified live on
    ms_dictionary: `POST /dictionaries/` with `fromLang: "XX"` → 201, with `dictionaryId: "not-a-uuid"`
    → 201, `GET /words/language/from/XX` → 200. The P3-09 note claiming one of a symmetric pair 400'd
    was not accurate. Affects `DictionaryRequestDTO`, `WordRequestDTO`, `WordBundleRequestDTO`,
    `WordController` (ms_dictionary) and `UserRequestDTO`, `VaultEntryRequestDTO` (ms_user).
  - **Why it matters now:** invalid codes flow into marketplace listings, where they can never match a
    (validated) language filter; non-UUID ids are stored as primary keys.
  - **The fix is more than adding `@Constraint`:** both validators *throw* from `isValid`. Once they
    actually run, the framework wraps that in a `ValidationException`, which no handler catches → 500.
    They must return false instead (as ms_marketplace's does), and the path-variable uses need the
    `HandlerMethodValidationException` 400 handler ms_marketplace now has. Then clean up any invalid
    rows already stored. ms_marketplace's `SupportedLanguage`/`SupportedLanguageValidator` is the
    working template.
  - Needs care with clients: requests they send today that succeed will start returning 400.
  - Done 2026-09-27, in **all three** services (ms_marketplace had an unused inert `@ValidUUID` copy
    too). `@Constraint` + `message`/`groups`/`payload` on both annotations; validators return false;
    `UUIDValidator` checks the canonical form by regex (`UUID.fromString` accepts `1-1-1-1-1`); the
    redundant `fieldName` attribute and its constants removed (errors name the field);
    `InvalidUUIDException`, `InvalidLanguageCodeException` and their handlers deleted (dead).
  - **Also found and fixed:** ms_dictionary had no `HandlerMethodValidationException` handler, so a
    constraint failure inside the `POST /words` **list** body — e.g. a blank word — was a **500**. Now
    a 400 naming the nested field (`bundles.words[0].word`).
  - **Root cause was in the skill:** `web-api/references/validation-and-errors.md` prescribed
    "validators throw a specific exception rather than returning false" and never mentioned
    `@Constraint`. Rewritten with both rules and a "prove it with a 400 test" requirement.
  - **Data:** checked the dev databases first — no invalid language codes and no non-UUID ids in 24
    dictionaries, 114 words or ms_user, so no cleanup migration was needed.
  - **Verified live** against the IntelliJ-run services: `fromLang: XX`, non-UUID `dictionaryId`,
    `/words/language/from/XX`, `DELETE /words/not-a-uuid`, a blank word and a non-UUID `wordId` inside
    a bundle, and a non-UUID `keycloakId` on ms_user — all 400 with the field named; lowercase `en`
    still accepted. Suites: ms_dictionary 109/109 (12 new, incl. a new `WordControllerWebTest`),
    ms_user 44/44, ms_marketplace 79/79. Two existing web tests used non-UUID fixtures (`"d1"`,
    `"kc-someone-else"`) and were given real UUIDs.
  - Client integration doc updated — clients must send canonical UUIDs and supported codes.
- [x] `P4-10` **Public dictionaries readable by any authenticated user in ms_dictionary** (added 2026-09-27 at P4-07)
  - **Why:** an import is a reference (P4-07 decision). The importer's client must read the
    dictionary and its words from ms_dictionary, but every read there 404s a non-owner (P3-08) — so an
    imported dictionary cannot be opened today.
  - Change the read rules for **public** dictionaries only: `GET /dictionaries/dictionary/{id}`,
    `GET /dictionaries/batch`, `GET /words/dictionary/{id}` (and tags) return a public dictionary to any
    authenticated caller; a private one still 404s a non-owner. Writes stay owner-only (403).
  - This amends the P3-08 ownership contract — update `docs/agent/security.md`,
    `.claude/skills/security/references/ownership-rules.md` and the client integration docs, and run
    the `security-auditor` agent.
  - When the owner makes it private or deletes it, it disappears from importers too (reference
    semantics, accepted). A "make my own copy" action would be a separate, later task.
  - Done 2026-09-27. One rule, `common/utils/DictionaryAccessUtils.isReadableBy` (owned **or**
    public), applied in `DictionaryServiceImpl` (by id, batch), `WordServiceImpl` (by dictionary, by
    word id) and `DictionaryTagServiceImpl` (tags). Writes untouched. **Also:** a word's `level` is
    returned as `null` to non-owners — the owner's personal mastery, which an importer's client would
    otherwise show as its own. `getWordsByIds` now resolves dictionaries in one query, not one per word.
  - Docs: `security.md` (the normative contract), `ownership-rules.md` (skill), the client integration
    doc, `ms_dictionary/CLAUDE.md`.
  - **Verified live** with two real users (testadmin publishes, testuser reads) against the
    IntelliJ-run ms_dictionary: public → dictionary 200, batch 1 item, words 1 with `level` null
    (owner still sees 4), tags visible; testuser adding a tag or deleting → 403; made private →
    dictionary 404, words `[]`, tags 404. Test data removed. Suite 97/97 (11 new).
  - **Clients:** the integration doc now says `level` is `null` on others' words — keep your own
    progress for imported words locally.

### Marketplace search (added 2026-10-03)
Users only learn some languages, so browse must filter. Agreed design, decisions and end state are in
`docs/status/marketplace-search-plan-2026-10-03.pdf`. Filters are optional; values of one filter are
ORed, different filters ANDed; both sorts (newest, popular) take them all. No GraphQL — it would add
an API stack without making a query cheaper. Ordered by cross-service dependency: P4-11 needs nothing
else, P4-12 needs ms_dictionary, P4-13 needs ms_user.

- [x] `P4-11` **Language-pair filter, slices, browse indexes** (ms_marketplace only)
  - `GET /marketplace/dictionaries` and `/popular` take `pair=EN-TR` (repeatable, or comma-separated;
    max 10). **Direction is ignored**: `EN-TR` returns EN→TR and TR→EN. Validated by `@LanguagePair`
    on each list element (two different supported codes, any case) → 400.
  - `lang_pair` column (`2026/10/04-01`): both codes in alphabetical order, derived on every write
    by `LanguagePairUtils`, backfilled with `COLLATE "C"` so SQL and Java order alike. The filter is
    one `lang_pair IN (...)`.
  - **`SliceResponse {items, page, size, hasNext}` replaces `PageResponse`** on every browse endpoint
    — infinite scroll, so no `COUNT(*)` per request. Spring Data JPA 3.2 has no count-free page for a
    Specification, so `DictionaryStatsSliceRepository` (custom fragment) fetches size + 1 rows.
  - Filters are `DictionaryStatsSpecifications` combined per request; the publisher endpoint uses the
    same path. `isListed()` renders a literal so the partial indexes apply.
  - Indexes (`2026/10/04-02`): partial `WHERE is_listed` on `(published_at DESC, dictionary_id)`,
    `(import_count DESC, published_at DESC, dictionary_id)` and `(lang_pair)`. The single-column sort
    indexes and `(from_lang, to_lang)` were dropped.
  - **Removed:** `GET /marketplace/dictionaries/language` (now 404).
  - Done 2026-10-04. Suite 96/96. **Verified live** on the local DB with a fresh build on :8097
    (listeners off): `pair=EN-TR&pair=fr-de` returned all four directions and not a hidden EN→FR row;
    `/popular` ordered by imports; duplicate pairs collapse; `size=2` paged with `hasNext`; `EN-XX`,
    `EN-EN` → 400; `/language` → 404; no token → 401. Hibernate renders `where ds1_0.is_listed and
    ds1_0.lang_pair in (...)`, and `EXPLAIN` uses `idx_dictionary_stats_listed_newest` /
    `_popular` with no sort node. Backfill checked on the three existing dev rows. Test rows removed.
  - **Restart any running ms_marketplace after pulling this:** `lang_pair` is NOT NULL, so an
    instance on the old code fails every listing write (those messages go to the DLQ).
- [x] `P4-12` **Tag filter** (ms_dictionary + ms_marketplace) — depends on P4-11
  - ms_dictionary: `tags` (sorted, never null — `[]` when untagged) on `dictionary.visibility.*`,
    `dictionary.updated` and every snapshot entry (full state, rule 2; the snapshot reads all tags in
    one query). A tag add/remove that **actually changes** a **public** dictionary bumps its `updatedAt`
    and raises `dictionary.updated` via `DictionaryService.publishTagChange` — without the bump the
    marketplace would drop the event as stale (rule 4). Re-adding an existing tag, deleting an absent
    one, or any tag change on a private dictionary announces nothing. `deleteByDictionaryIdAndTag` now
    returns the removed count to tell the cases apart.
  - ms_marketplace: `tags VARCHAR[]` + GIN index partial `WHERE is_listed` (`2026/10/04-03`).
    **varchar, not text:** Hibernate binds the `String[]` filter value as `varchar[]`, and Postgres has
    no `text[] && varchar[]` operator — a `text[]` column 500'd on the first live request (unit tests
    cannot see it). Consumers replace the array whole and normalise it again (trim, lowercase
    `Locale.ROOT`, dedupe, sort). **Null tags = a pre-P4-12 message: keep the held tags**; `[]` clears.
  - **Backfill:** existing listings start `{}`. `reconcile` repairs a row whose tags differ from a
    snapshot entry of the **same** `updatedAt` (same version, so the row is just missing data) — the
    one exception to "strictly newer only", and for tags only. The first snapshot after deploying
    counts these repairs in its "created or corrected" log figure; that is expected once.
  - `tag=` filter on `GET` and `/popular`, repeatable, max 10, each non-blank and ≤ 100 chars, any
    case; **any** match via Hibernate's `arrayOverlaps` (`tags && ?`). AND-ed with `pair`. `tags` on
    every listing. Known limit: Spring splits a single comma-separated value, so a tag that itself
    contains a comma cannot be searched for.
  - Done 2026-10-04. Suites: ms_dictionary 118/118, ms_marketplace 112/112. **Verified live:**
    ms_dictionary on :8095 publishing to a temp queue — create public (`tags []`), add " Food " →
    `[food]`, re-add → nothing, add travel → `[food, travel]`, delete FOOD → `[travel]`, delete absent
    → nothing; `updatedAt` rose each time and matches `update_dt`. ms_marketplace on :8097: `tag=FOOD`,
    `tag=food&tag=travel` (any), `tag+pair` (AND), unknown tag → empty, hidden rows excluded, blank /
    11 tags → 400; real `dictionary.updated`/`visibility.public` messages stored normalised, deduped
    tags; one real `dictionary.snapshot` repaired a same-version listing from `{}` to `{travel}`.
    `EXPLAIN` at 50k rows: rare tags use `idx_dictionary_stats_listed_tags` (GIN), common tags walk the
    newest-first index. Test data, temp queue and instances removed; DLQ stayed empty.
- [ ] `P4-13` **Publisher display names: filter and on listings** (ms_user + ms_marketplace; absorbs `BL-04`) — depends on P4-11
  - Display name, not Keycloak username (can be an email for SSO users). Not unique; that is fine.
    Clients require a display name and the marketplace T&C before marketplace use; ms_user keeps it
    optional for users who never use the marketplace.
  - ms_user: new `user.profile.updated` `{keycloakId, displayName, updatedAt, eventTimestamp}` when the
    name is set, changed or cleared, after commit. No profile snapshot for now.
  - ms_marketplace: `publishers (keycloak_id PK, display_name, source_updated_at)`, `pg_trgm` + trigram
    index; stale-event guard; `user.deleted` removes the row. `publisher=` filter: case-insensitive
    substring (`ann`/`nna` → "Anna Bauer"), min 3 chars, `%`/`_` escaped. **Listings whose publisher
    has no display name are hidden from every browse endpoint.** `publisherName` on listings via one
    batched lookup per page.
  - Agent: `spring-boot-architect`.

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
- [ ] `P5-04` **Edge rate-limiting + TLS** (split out of `P3B-08` on 2026-09-27)
  - Lands with the gateway / reverse proxy — see `docs/ops/dockerization-and-environments.md`
  - Rate-limit the Keycloak token/auth endpoints, enforce HTTPS, secure cookies
  - Done when: auth endpoints are rate-limited at the edge and all external traffic is HTTPS

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
- [ ] `P3-03a` **Tell the client teams ms_dictionary is closed** (added 2026-07-23; moved here from
  Phase 3 on 2026-09-27)
  - Android currently talks to `:8085` with no token and will start getting 401s the moment this is
    deployed to their dev machine. `docs/integration/client-login-guide.md` is updated, but a heads-up
    matters more than a doc edit here
  - Trigger: before a client developer pulls this backend and runs against it. Costs two minutes —
    do it sooner rather than later
  - Done when: the Android/iOS repos know they must attach a bearer token to ms_dictionary calls
  - **Message drafted 2026-09-27** in `docs/integration/marketplace-client-guide.md` §0 — it now also
    covers the P4-09 validation 400s, P4-10 public read, the marketplace and the web CORS gap. Still
    open until it has actually been sent to the client teams.
- [ ] `BL-04` **Publisher display names on marketplace listings** (added 2026-09-27 at P4-06) — **moved to `P4-13` on 2026-10-03**
  - Listings carry `publisherId` only; users will want "by Anna". The name lives in ms_user, so per
    rule 5 the marketplace stores it (`publisher_name`) and keeps it current from an ms_user event —
    e.g. `user.profile.updated` carrying `keycloakId` + `displayName` — which does not exist yet
  - Trigger: when the client teams build the marketplace screens
- [ ] `BL-02` **Registration bot protection** (split out of `P3B-08` on 2026-09-27)
  - Enable Keycloak's reCAPTCHA on the hosted registration form, or bots will create accounts
  - Trigger: before public sign-up opens
- [ ] `BL-03` **Secret rotation before any shared/prod realm** (split out of `P3B-08` on 2026-09-27)
  - Rotate `admin`/`admin`, the `verborum-backend` client secret, and the Google/Facebook client
    secrets (shared in chat + `.env`); delete `verborum-dev-cli` (password-grant) from non-local
    realms; add `notUsername` and stronger complexity to the prod password policy (see `P3B-08`)
  - Trigger: before any non-local realm exists. The Google/Facebook secrets were pasted into chat, so
    rotate those two earlier if that chat left this machine
