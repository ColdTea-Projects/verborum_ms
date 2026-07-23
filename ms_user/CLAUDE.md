# ms_user — Service Guide

This file supplements the root `CLAUDE.md` and `docs/agent/` skills. It covers only what is
specific to this service. All conventions are inherited from the root — do not duplicate them.

## What this service does
User profile management, auth integration with Keycloak (the only service that writes to
Keycloak), Dictionary Vault (imported public dictionaries), and User Stats.

## Facts
- **Port:** 8086
- **DB:** `vdbprofile` (PostgreSQL) — docker-compose in this module (Postgres on 5433 + Adminer on 8081)
- **Base package:** `de.coldtea.verborum.msuser`
- **Status:** All three Phase-2 entities exist (`User`, `UserStats`, `VaultEntry`; P2-03/04/05 done)
  and both REST APIs are implemented — User (P2-06) and Vault (P2-07). RabbitMQ is fully wired:
  publishes `user.deleted` (P2-08) and consumes `dictionary.imported` (P2-09), and realm-role
  mapping is fixed (P2-11). **Phase 2 is complete for this service.** Its REST endpoints have never
  been exercised over HTTP, though — they all require a JWT and Keycloak arrives at P3-01/P3-02.

## Entities
- `User` (`users`) — `userId` (PK, client UUID), `keycloakId`, `email`, `displayName`,
  `createdAt`/`updatedAt`. `keycloak_id` is NOT NULL + UNIQUE (the 1:1 link to the Keycloak subject,
  and the value the other services store as `fk_user_id`). `email` is NOT NULL + UNIQUE (product
  rule: one profile per email; Keycloak stays the identity authority). Migration:
  `2026/07/21-01-changelog.json`.
- `UserStats` (`user_stats`) — `userId` (PK), `totalWords`, `totalDictionaries`, `updatedAt`.
  1:1 with `User`: `user_id` is both PK and a real FK to `users(user_id)` with **ON DELETE CASCADE**
  (deleting a user removes its stats row). This intra-service FK is intentional — unlike the
  cross-service / split-ready `word → dictionary` case, `UserStats` is a same-DB satellite of `User`.
  Migration: `2026/07/21-02-changelog.json`.
- `VaultEntry` (`vault_entries`) — `vaultEntryId` (PK), `userId`, `dictionaryId`, `importedAt`.
  `fk_user_id` is a real FK to `users(user_id)` with **ON DELETE CASCADE** (same intra-service
  satellite case). `fk_dictionary_id` is a **cross-service** ref to ms_dictionary — plain String,
  **no DB FK** (this is the genuine cross-service case the "no FK" convention is for). A composite
  **UNIQUE (fk_user_id, fk_dictionary_id)** keeps a vault a set (no duplicate imports) and backs
  P2-09 import idempotency. Migration: `2026/07/21-03-changelog.json`.

All timestamps are zone-aware: `OffsetDateTime` over `timestamptz` columns (`2026/07/21-04-changelog.json`),
serialized as ISO-8601 UTC (`...Z`), matching ms_dictionary. `User` exposes JSON keys
`createdAt`/`updatedAt`; `VaultEntry` keeps the semantic name `importedAt`. DB column names are
unchanged (`creation_dt`/`update_dt`/`imported_at`).

## API — UserController (`/users`)
- `POST /users/` create profile · `PUT /users/` update · `GET /users/{userId}` (404 if missing) ·
  `DELETE /users/{userId}`. Mirrors DictionaryController: `Response` envelope on mutations, DTO on
  read, `saveUser` backs both POST and PUT. `userId` is still client-supplied here — switching to the
  JWT subject is P3-05. DELETE relies on the DB cascade to clear stats/vault; the `user.deleted`
  event is P2-08.

## API — VaultController (`/users/{userId}/vault`)
- `GET` list the user's imported dictionaries (empty list for an unknown user) ·
  `POST` add one (body: `{"dictionaryId": "..."}`) · `DELETE /{dictionaryId}` remove one.
- `vaultEntryId` is **server-generated** (`UUID.randomUUID()`), unlike every other entity's
  client-supplied id — a vault entry is a system-owned row, and P2-09 creates identical rows from a
  `dictionary.imported` event that carries no client id.
- **POST is idempotent**: an already-imported `(userId, dictionaryId)` returns the existing entry
  rather than violating the composite UNIQUE. P2-09's listener should call `addVaultEntry` and get
  redelivery-safety for free.
- POST 404s on an unknown user (`fk_user_id` is a real FK — the DB would otherwise 500). DELETE of an
  entry that is not there is a silent 200, matching `deleteUser`/`deleteDictionary`.

## Events (see `docs/agent/rabbitmq.md`)
- **Publishes:** `user.deleted` from `UserServiceImpl.deleteUser()` (P2-08 done). `common/config/
  RabbitMQConfig` mirrors ms_dictionary's — same exchange, fanout DLX + DLQ, ISO-8601-pinned message
  converter. Publisher-only until P2-09, so no consumer queue is declared yet.
  - The payload carries **both `userId` and `keycloakId`**. Consumers in other services must match on
    **`keycloakId`** — their `fk_user_id` is the JWT subject, which is this service's `keycloak_id`
    (see the quirk below). Matching on `userId` silently deletes nothing.
  - A delete of an unknown id publishes nothing and still returns 200.
- **Consumes:** `dictionary.imported` on the durable queue `user.dictionary.imported`
  (`common/listener/MarketplaceEventListener` → `VaultService.importDictionary`, P2-09 done).
  Nothing publishes it until ms_marketplace ships (P4-07), but the queue is bound already, so
  imports are captured rather than discarded.
  - The event is `{dictionaryId, keycloakId, eventTimestamp}` — **`keycloakId`, not `userId`**, for
    the same reason as `user.deleted`. `importDictionary` resolves it to `user_id` before writing,
    because `vault_entries.fk_user_id` is a real FK.
  - Idempotent: it delegates to `addVaultEntry`, so a redelivery returns the existing entry.
    An unknown `keycloakId` throws; the listener re-throws so the message is dead-lettered.
- **Cross-service JSON:** `RabbitMQConfig`'s converter uses a `DefaultJackson2JavaTypeMapper` with
  `INFERRED` type precedence. Without it the publisher's `__TypeId__` header (a class that does not
  exist here) makes every inbound message fail as ClassNotFound. Any consuming service needs this.

## Security
- `common/config/SecurityConfig.java` is already in place: stateless JWT resource server,
  `/actuator/**` and Swagger permitted, everything else requires authentication.
- Realm roles are mapped by `SecurityConfig.extractRealmRoles(Jwt)`, written by hand (P2-11).
  `JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles")` takes a flat claim
  name, not a path, so it mapped nothing while authentication still succeeded — every `hasRole(...)`
  silently denied. Do not revert to the built-in converter. Realm roles only; client roles under
  `resource_access` are unused.
- ms_user is unique among services: besides being a JWT resource server, it also acts as a Keycloak
  Admin API client (via `keycloak-admin-client`). **It deletes identities, it does not create them**
  — sign-up is Keycloak-hosted, so `KeycloakUserService` exists only so that deleting a profile also
  deletes the account (P3-04). Without it the person could log back in and re-register.
  - Called from `UserServiceImpl.deleteUser` *after* the DB delete and the `user.deleted` publish,
    and **never throws**: the data is already gone, so a failure is an ERROR log naming the id, not
    a failed request. Keycloak 404 counts as success.
  - No `KEYCLOAK_ADMIN_CLIENT_SECRET` → WARN and skip, so local dev works without a secret.
  - Requires the `verborum-backend` service account to hold `realm-management` `manage-users`; the
    realm import grants it.
- `keycloak.admin.client-secret` is intentionally left blank in `application.properties` and
  sourced from the `KEYCLOAK_ADMIN_CLIENT_SECRET` environment variable — never hardcode it.

## Service-specific quirks
- The cross-service user key is `keycloak_id`, not `user_id`. Dictionaries/words store the JWT
  subject in `fk_user_id`, which equals a User's `keycloak_id` — so joins from other services'
  data land on `keycloak_id`. Keep it NOT NULL + UNIQUE.
