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
- **Status:** Secured scaffold with the first entity. `User` entity + migration exist (roadmap P2-03
  done); no DTOs, repository, or endpoints yet. P2-04, P2-05 (UserStats, VaultEntry) and P2-06+ remain.

## Entities
- `User` (`users`) — `userId` (PK, client UUID), `keycloakId`, `email`, `displayName`, timestamps.
  `keycloak_id` is NOT NULL + UNIQUE (the 1:1 link to the Keycloak subject, and the value the other
  services store as `fk_user_id`). `email` is NOT NULL + UNIQUE (product rule: one profile per email;
  Keycloak stays the identity authority). Migration: `2026/07/21-01-changelog.json`.

## Planned entities (not yet implemented — see roadmap Phase 2)
- `UserStats` (`user_stats`) — `userId` (PK, FK to user), `totalWords`, `totalDictionaries`, `updatedAt`
- `VaultEntry` (`vault_entries`) — `vaultEntryId`, `userId`, `dictionaryId`, `importedAt`
  (tracks imported public dictionaries per user)

## Events (planned — requires RabbitMQ, see roadmap Phase 1 and `docs/agent/rabbitmq.md`)
- **Publishes:** `user.deleted` (consumed by ms_dictionary, ms_marketplace to cascade-delete)
- **Consumes:** `dictionary.imported` (published by ms_marketplace on import → creates a VaultEntry)

## Security
- `common/config/SecurityConfig.java` is already in place: stateless JWT resource server,
  `/actuator/**` and Swagger permitted, everything else requires authentication.
- ms_user is unique among services: besides being a JWT resource server, it also acts as a
  Keycloak Admin API client (via `keycloak-admin-client`) to create/link users during
  registration. See `docs/agent/security.md` for the `KeycloakUserService` pattern.
- `keycloak.admin.client-secret` is intentionally left blank in `application.properties` and
  sourced from the `KEYCLOAK_ADMIN_CLIENT_SECRET` environment variable — never hardcode it.

## Service-specific quirks
- The cross-service user key is `keycloak_id`, not `user_id`. Dictionaries/words store the JWT
  subject in `fk_user_id`, which equals a User's `keycloak_id` — so joins from other services'
  data land on `keycloak_id`. Keep it NOT NULL + UNIQUE.
