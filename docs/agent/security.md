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
  command: start-dev
  ports:
    - "8180:8080"
  environment:
    KEYCLOAK_ADMIN: admin
    KEYCLOAK_ADMIN_PASSWORD: admin
  volumes:
    - keycloak_data:/opt/keycloak/data
```

**Keycloak setup steps (do once manually):**
1. Open http://localhost:8180 → Admin console (admin/admin)
2. Create realm: `verborum`
3. Create client: `verborum-app` (type: public, for mobile)
4. Create client: `verborum-backend` (type: confidential, for service-to-service)
5. Add Google as identity provider (requires Google OAuth2 credentials)
6. Configure realm roles: `user`, `admin`

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
        JwtGrantedAuthoritiesConverter authoritiesConverter =
                new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthorityPrefix("ROLE_");
        authoritiesConverter.setAuthoritiesClaimName("realm_access.roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}
```

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
