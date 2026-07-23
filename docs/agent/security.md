# Security — Keycloak & Spring OAuth2

## Architecture

```
Mobile Client
    │  (Bearer JWT token)
    ▼
API Gateway  ──validates token──►  Keycloak
    │
    ▼
ms_dictionary / ms_marketplace   (Resource Servers — validate JWT)

Web App  ──sign up/login──►  ms_user  ──►  Keycloak
                Google Sign In ──SSO──►  Keycloak
```

- **Keycloak** is the authorization server and identity provider
- **Google Sign In** is federated into Keycloak as an identity provider
- **ms_user** handles user registration and profile — it is the only service that writes to Keycloak
- All other services are **resource servers** — they only validate the JWT token, never issue one
- The mobile client gets a JWT from Keycloak and attaches it as `Authorization: Bearer <token>`

---

## Maven Dependencies

Add to any service that needs to validate JWT tokens:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

Add to ms_user only (for Keycloak admin API integration):
```xml
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>23.0.0</version>
</dependency>
```

---

## Keycloak Docker Setup

Add to root `docker-compose.yml`:
```yaml
keycloak:
  image: quay.io/keycloak/keycloak:23.0.0
  command: start-dev --import-realm
  ports:
    - "8180:8080"
  environment:
    KEYCLOAK_ADMIN: ${KEYCLOAK_ADMIN:-admin}
    KEYCLOAK_ADMIN_PASSWORD: ${KEYCLOAK_ADMIN_PASSWORD:-admin}
  volumes:
    - ./keycloak/import:/opt/keycloak/data/import:ro
    - keycloak_data:/opt/keycloak/data
```
This is live in the root `docker-compose.yml` (roadmap P3-01).

**The realm is NOT configured by hand.** `keycloak/import/verborum-realm.json` is the source of
truth: realm, clients, roles and the local dev users, versioned in git and imported by
`--import-realm` (roadmap P3-02). Consequences worth knowing:
- The import runs **only on first start of an empty data volume**. After editing the JSON:
  `docker compose down && docker volume rm verborum_ms_keycloak_data && docker compose up -d keycloak`
- Changes made in the admin console are **not** written back to the file. Anything meant to last
  goes into the JSON.
- Admin console: http://localhost:8180 (`admin`/`admin` by default, overridable via
  `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD`).

**Still to do by hand:** Google as an identity provider — it needs real Google OAuth2 credentials,
which cannot be committed. Add it in the console (or via env-substituted config) when those exist.

---

## Resource Server Configuration (ms_dictionary, ms_marketplace)

```java
// common/config/SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 ->
                oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            );
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractRealmRoles);
        return converter;
    }

    // Keycloak nests realm roles: { "realm_access": { "roles": ["user", "admin"] } }
    static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return List.of();
        }
        if (!(realmAccess.get("roles") instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
    }
}
```

> **Do not use `JwtGrantedAuthoritiesConverter` with
> `setAuthoritiesClaimName("realm_access.roles")` here.** That setter takes a flat claim name, not a
> path expression: it looks for a top-level claim literally named `realm_access.roles`, finds
> nothing, and grants no authorities at all. Authentication still succeeds, so nothing looks broken
> until every `hasRole(...)` check silently denies. This template carried that bug until it was
> fixed in ms_user at roadmap P2-11 — ms_dictionary (P3-03) and ms_marketplace (P4-01) must use the
> hand-written extraction above.

**application.properties for resource servers:**
```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8180/realms/verborum
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:8180/realms/verborum/protocol/openid-connect/certs
```

---

## Extracting User ID from JWT in Controllers

The `userId` inside a JWT can be extracted from `SecurityContext`:

```java
// common/utils/SecurityUtils.java
public class SecurityUtils {

    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject(); // Keycloak subject = userId
        }
        throw new IllegalStateException("No authenticated user found");
    }
}
```

Use in controllers where the caller's userId should be the authenticated user (don't trust client-provided userId for ownership operations):
```java
@PostMapping("/")
public ResponseEntity<Response> createDictionary(
        @Valid @RequestBody DictionaryRequestDTO dto,
        WebRequest request) {
    // Override userId with authenticated user — never trust the client for this
    dto.setUserId(SecurityUtils.getCurrentUserId());
    ...
}
```

---

## ms_user — Keycloak Integration

ms_user is the only service that communicates with Keycloak Admin API.
It handles user registration by:
1. Receiving sign-up request from Web App (with Google SSO info)
2. Creating/linking user in Keycloak via Admin Client
3. Saving user profile to `DB_Profile`

```java
// Example: creating a user in Keycloak via Admin Client
@Service
@RequiredArgsConstructor
public class KeycloakUserService {

    private final Keycloak keycloak;

    @Value("${keycloak.realm}")
    private String realm;

    public String createUser(String email, String displayName) {
        UserRepresentation user = new UserRepresentation();
        user.setEnabled(true);
        user.setEmail(email);
        user.setUsername(email);
        user.setFirstName(displayName);

        Response response = keycloak.realm(realm)
                .users()
                .create(user);

        String userId = CreatedResponseUtil.getCreatedId(response);
        return userId; // This is the Keycloak subject (sub claim in JWT)
    }
}
```

**application.properties for ms_user:**
```properties
keycloak.realm=verborum
keycloak.auth-server-url=http://localhost:8180
keycloak.admin.client-id=verborum-backend
keycloak.admin.client-secret=<secret-from-keycloak-console>
```

---

## The Auth Contract (normative — must match Integration §6)

`docs/integration/frontend-backend-integration.md` §6 is the cross-client auth spec. This section
mirrors it so the backend and the clients cannot drift on realm names, client ids or token
lifetimes. **If the two ever disagree, that is a bug — fix both.** Everything here is what
`keycloak/import/verborum-realm.json` actually configures (roadmap P3-02/P3-07).

**Realm:** `verborum` · issuer `http://localhost:8180/realms/verborum` locally.
**Flow:** Authorization Code + PKCE (S256) for every platform. No implicit flow anywhere.

| Client id | Type | Flow | Used by |
|---|---|---|---|
| `verborum-app` | public | Auth Code + PKCE | Android, iOS |
| `verborum-web` | public | Auth Code + PKCE | Web (may become a BFF confidential client — Integration §6.3 is undecided; the backend accepts `Authorization: Bearer` either way) |
| `verborum-backend` | confidential | client credentials / service account | ms_user → Keycloak Admin API |
| `verborum-dev-cli` | public | **direct access grants (password)** | **LOCAL DEV ONLY** |

**`verborum-dev-cli` is not part of the contract.** It exists so a developer can get a user token
with one `curl` (`grant_type=password`, `testuser`/`testuser`) instead of driving a browser through
PKCE. It must never exist in a shared or production realm — enabling password grant on `verborum-app`
instead would have contradicted the PKCE-only spec, which is why it is a separate throwaway client.
Delete it from any realm export that leaves a developer machine.

**Token policy:** access tokens 5 min (`accessTokenLifespan: 300`), SSO idle 30 min, offline session
idle 60 days so a device offline for days resumes sync without re-login (Integration §6.2). Clients
send `Authorization: Bearer <access>`, refresh once on 401, then surface login.

**Redirect URIs:** `verborum-app` → `de.coldtea.verborum://oauth2redirect/*` and `http://localhost:*`
(emulator only); `verborum-web` → `http://localhost:3000/*`. New URIs go in the realm import file;
an unregistered one is rejected before the login page renders.

**PKCE is enforced.** Both public clients set `pkce.code.challenge.method=S256`; an authorization
request without `code_challenge` fails with `invalid_request: Missing parameter:
code_challenge_method`. Do not treat PKCE as advisory.

**Sign-up is Keycloak-hosted** (decided 2026-07-23): clients send users to
`/protocol/openid-connect/registrations` with the same PKCE parameters as login — no native
registration form. A native form would need ms_user to create the Keycloak identity through the
Admin API (P3-04, unbuilt), and would split identity ownership. After first login the client calls
`POST /users/` once with `keycloakId` = JWT `sub` to create the profile row. Password reset is the
hosted "Forgot Password" link. **No SMTP is configured**, so reset/verification mail does not send in
local dev.

**Logout:** end-session endpoint `{issuer}/protocol/openid-connect/logout` with `client_id` +
`refresh_token`, optional `revoke`, then delete local tokens. Skipping the end-session call leaves an
SSO session that logs the user straight back in. Full client-side rules in Integration §6.1a.

**Ownership (P3-05/P3-08).** Authentication is not authorization. Every service takes the acting user
from the JWT subject and passes it explicitly into the service layer; ids in the body or path are
never trusted, because clients generate them. Rules, applied consistently:
- a write naming another user → **403**
- a read of another user's resource *by id* → **404**, so a caller cannot probe which ids exist
- batch/list endpoints → **filter** to the caller instead of refusing, for the same reason
- event-driven paths (`user.deleted` cascade, `dictionary.imported`) take no caller and are
  deliberately unguarded — their actor is another service

**Realm roles:** `user` (every registered account), `admin`. Mapped to `ROLE_user` / `ROLE_admin` —
see "Roles & Authorization" below and the nested-claim warning in the resource-server config.

**Issuer pinning (`KC_HOSTNAME_URL`).** Keycloak stamps the issuer into every token; unpinned it
echoes the caller's Host header, so a phone on `http://<lan-ip>:8180` gets tokens no service will
accept — a 401 that reads like a bad token. The compose file pins it (default
`http://localhost:8180`). For device testing set `KEYCLOAK_HOSTNAME_URL` plus every service's
`KEYCLOAK_ISSUER_URI` / `KEYCLOAK_JWK_SET_URI` to the same LAN origin. Integration §6.2a has the
exact variables.

**Local dev users** (from the realm import, dev only): `testuser`/`testuser` with role `user`,
`testadmin`/`testadmin` with `user` + `admin`. Self-registration is open in this realm
(`registrationAllowed: true`) because sign-up is hosted — review that before any realm that is not a
developer laptop.

**Account deletion (P3-04).** `verborum-backend`'s service account holds `realm-management`
`manage-users` + `view-users`, granted by the realm import. ms_user uses it to delete the Keycloak
identity when a profile is deleted — otherwise the account survives and can simply re-register
through hosted sign-up. It never creates identities. Without `KEYCLOAK_ADMIN_CLIENT_SECRET` the call
is skipped with a WARN, so local dev still works.

**Secrets.** `verborum-backend`'s secret is `local-dev-only-change-me` in the committed realm file —
a placeholder for local dev, never a real credential. Services read it from
`KEYCLOAK_ADMIN_CLIENT_SECRET`; `ms_user/application.properties` deliberately leaves it blank. Any
non-local realm must have a generated secret supplied through the environment.

**Not configured yet:** Google as an identity provider (§6 assumes it; it needs real Google OAuth2
credentials that cannot be committed) and the §6.4 guest-data migration, which is client-side and
needs no backend endpoint.

---

## Roles & Authorization

Keycloak realm roles used in Verborum:
- `user` — standard authenticated user (default for all registered users)
- `admin` — platform admin (future use)

Role-based access example:
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers(HttpMethod.DELETE, "/dictionaries/**")
        .hasAnyRole("user", "admin")
    .requestMatchers(HttpMethod.GET, "/dictionaries/public/**")
        .permitAll()  // public dictionaries visible without auth
    .anyRequest().authenticated()
)
```

---

## Checklist — Adding Security to a Service

1. Add `spring-boot-starter-security` + `oauth2-resource-server` to `pom.xml`
2. Create `common/config/SecurityConfig.java` with stateless JWT config
3. Add `issuer-uri` and `jwk-set-uri` to `application.properties`
4. Add `common/utils/SecurityUtils.java` for extracting current user
5. Permit actuator and Swagger endpoints
6. Never trust client-provided `userId` for ownership — always use JWT subject
