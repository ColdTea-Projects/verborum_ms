---
name: verborum-security
description: Authentication and authorization patterns for Verborum. Use when adding security to a service, configuring Keycloak or JWT, protecting endpoints, or handling user identity. Every service must be secured — this is a hard requirement.
---

# Verborum Security

Full setup and code live in `docs/agent/security.md`. Read it for the SecurityConfig,
Keycloak integration, and JWT extraction patterns.

## Core model

- **Keycloak** is the auth server; **Google Sign In** is federated into it.
- **ms_user** is the only service that writes to Keycloak (registration).
- All other services are **resource servers** — they validate JWTs, never issue them.
- Mobile client sends `Authorization: Bearer <token>`.

## Hard requirements

- **Every service ships with security from the start.** ms_dictionary was built without it
  (a known gap being closed in Phase 3); do not repeat that mistake for any new service.
- **Never trust a client-provided `userId`** for ownership operations. The user identity
  comes from the JWT subject via `SecurityUtils.getCurrentUserId()`, not the request body.
- **No hardcoded secrets** — credentials and URLs go in `application.properties` or env vars.
- Permit only actuator, Swagger, and explicitly-public endpoints; everything else is
  `authenticated()`.

## Standard SecurityConfig

Stateless JWT resource server. Add `spring-boot-starter-security` +
`spring-boot-starter-oauth2-resource-server`, create `common/config/SecurityConfig.java`,
configure `issuer-uri` and `jwk-set-uri`. See `docs/agent/security.md` for the full class.

## Auditing

To check a service's security posture, delegate to the `security-auditor` subagent.
