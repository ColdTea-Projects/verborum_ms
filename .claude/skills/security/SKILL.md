---
name: security
description: Authentication and authorization for Verborum — Keycloak as identity provider, the stateless JWT resource-server configuration, realm-role mapping, caller identity, ownership rules, and secrets handling. Use when touching auth, Keycloak, JWT, endpoint protection, ownership, or anything credential-shaped.
---

# Security

Every service is secured **from its first commit**. The normative auth contract lives in
`docs/agent/security.md`, mirrored in `docs/integration/frontend-backend-integration.md` §6 — if
those two ever disagree, that is a bug in both.

## Model

```
Client (Android / iOS / web)
   │ Authorization: Bearer <access token>
   ▼
ms_dictionary / ms_user / ms_marketplace   ← resource servers: validate JWTs, never issue them
   ▲
Keycloak (realm `verborum`)                ← the only issuer; Google and Facebook federate behind it
```

- **ms_user is the only service that calls the Keycloak Admin API**, and only to *delete* an
  identity — it never creates one.
- **Sign-up is Keycloak-hosted.** After first login the client calls `POST /users/` once with
  `keycloakId` = the JWT `sub`. Flow is Authorization Code + PKCE (S256) everywhere; no implicit
  flow. Realm, clients, token policy, social sign-in and the realm-import mechanics are in
  [references/keycloak-setup.md](references/keycloak-setup.md).

## Caller identity

```java
public static String getCurrentUserId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
        return jwtAuth.getToken().getSubject();      // Keycloak subject
    }
    throw new IllegalStateException(NO_AUTHENTICATED_USER);
}

/** Rejects a request naming someone other than the caller — 403, not a silent substitution. */
public static void requireSelf(String claimedUserId) {
    if (!getCurrentUserId().equals(claimedUserId)) {
        throw new ForbiddenOperationException(NOT_THE_OWNER);
    }
}
```

**Identity rule.** The token subject is what ms_dictionary and ms_marketplace store in
`fk_user_id`. In ms_user that value is the `keycloak_id` column, **not** its `user_id` — ownership
checks and event consumers must use the Keycloak id.

## Ownership — authentication is not authorization

**Never trust an id from the body or the path**; clients generate ids. The controller takes the
caller from the token and passes it in as an explicit `ownerId` argument; services never read the
security context themselves.

| Situation | Result | Why |
|---|---|---|
| Write on another user's resource | **403** | the client has a bug; say so |
| Read of another user's resource **by id** | **404** | a 403 would confirm the id exists |
| Batch or list endpoint | **filter to the caller** | same reason, without breaking clients |
| Event-driven path (cascade, import) | **unguarded** | the actor is another service |

Worked examples and the read/write helper split: [references/ownership-rules.md](references/ownership-rules.md).

## Resource-server configuration

Stateless, CSRF disabled, `permitAll` only on `/actuator/**` and Swagger, everything else
`authenticated()`, with **hand-written** realm-role extraction. The full class — and why
`JwtGrantedAuthoritiesConverter` with a dotted claim name silently grants zero authorities — is in
[references/resource-server-config.md](references/resource-server-config.md).

## Secrets

- **Nothing credential-shaped in Java source.** Properties or environment variables only, and
  committed defaults are local-dev placeholders.
- **Never put a secret, or an `${ENV}` placeholder for one, into the realm JSON** — the native
  import path does not substitute it and stores the literal string, silently breaking login.
- Do not log tokens, secrets, or full request bodies. The catch-all exception handler must not echo
  `ex.getMessage()` (`web-api`).

## Workflow: securing a service

1. Both security starters in the pom; `common/config/SecurityConfig.java` copied, not re-derived
2. `issuer-uri`, `jwk-set-uri` **and** `audiences` (SEC-05) in properties, plus actuator exposure `health,info`
3. `common/utils/SecurityUtils.java`, and every endpoint `authenticated()` except actuator,
   Swagger and deliberately public reads
4. Every ownership-sensitive service method takes an explicit `ownerId`; apply the table above
5. `ForbiddenOperationException` and its 403 handler
6. Web-slice tests proving 401, 403 and 404 (`integration-testing`)
7. Run the `security-auditor` agent before exposing the service

## Pitfalls

- Trusting a `userId` from the request body or path
- `JwtGrantedAuthoritiesConverter` with a dotted claim name; actuator exposure `*`; dropping `jwk-set-uri` or `audiences`
- 403 on an id-addressed read, or a service scaffolded without `SecurityConfig`
- Identity fields from the body, irreversible actions on a stale login, realm settings in one place only — [references/account-and-session-rules.md](references/account-and-session-rules.md)
