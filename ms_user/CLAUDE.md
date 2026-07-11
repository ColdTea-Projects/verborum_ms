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
- **Status:** Empty, secured scaffold only. No entities, DTOs, or endpoints yet (roadmap P2-01 done;
  P2-02 through P2-09 remain).

## Planned entities (not yet implemented — see roadmap Phase 2)
- `User` (`users`) — `userId`, `keycloakId`, `email`, `displayName`, timestamps
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
- None yet — this is a fresh scaffold. Follow the `new-entity` skill to add `User` next
  (roadmap task `P2-03`).
