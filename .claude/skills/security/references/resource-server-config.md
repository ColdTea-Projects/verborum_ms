# Resource-Server Configuration

`common/config/SecurityConfig.java`. Stateless, CSRF disabled — there are no cookies and no
sessions, so there is nothing for CSRF to protect.

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String AUTHORITY_PREFIX = "ROLE_";

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                    .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 ->
                    oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter())));
        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(SecurityConfig::extractRealmRoles);
        return converter;
    }

    static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return List.of();
        }
        if (!(realmAccess.get(ROLES_CLAIM) instanceof Collection<?> roles)) {
            return List.of();
        }
        return roles.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority(AUTHORITY_PREFIX + role))
                .toList();
    }
}
```

## Why the role extraction is hand-written

Keycloak nests realm roles:

```json
{ "realm_access": { "roles": ["user", "admin"] } }
```

`JwtGrantedAuthoritiesConverter.setAuthoritiesClaimName("realm_access.roles")` takes a **flat claim
name, not a path expression**. It looks for a top-level claim literally called `realm_access.roles`,
finds nothing, and grants no authorities at all. Authentication still succeeds, so nothing looks
broken — until every `hasRole(...)` check silently denies.

That bug shipped once and was fixed at roadmap P2-11. **Do not simplify this back to the built-in
converter.**

## Why the guards are defensive

A token whose `realm_access.roles` is missing or is not a list must yield **no authorities**, never
an exception. A failure on the authentication path turns a malformed token into a 500 — converting
a bad request into a service fault.

Non-string entries are filtered rather than crashing the stream.

## The actuator pairing

`/actuator/**` is `permitAll`, which is only safe because
`management.endpoints.web.exposure.include=health,info`. With `*` the same matcher served
`/actuator/env`, `/actuator/configprops` and a heap dump to any anonymous caller, listing config
keys including the datasource password. The two settings are a pair — see `spring-boot`.

## Properties

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=${KEYCLOAK_ISSUER_URI:http://localhost:8180/realms/verborum}
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${KEYCLOAK_JWK_SET_URI:http://localhost:8180/realms/verborum/protocol/openid-connect/certs}
spring.security.oauth2.resourceserver.jwt.audiences=${VERBORUM_JWT_AUDIENCE:verborum-api}
```

`jwk-set-uri` alongside `issuer-uri` is deliberate: with only the issuer, Boot fetches the discovery
document at startup and the service refuses to boot when Keycloak is down.

`audiences` (SEC-05) makes Boot add an `aud` validator next to the issuer check. Without it any token
the realm signs is accepted, including a client-credentials token of another client such as the
`verborum-backend` service account. The realm puts `verborum-api` into user tokens with an
`oidc-audience-mapper` on `verborum-app` and `verborum-dev-cli` (realm import plus
`keycloak/bootstrap/configure.sh`). A new service copies all three lines; a new user-facing client needs
the mapper. Web slices mock `JwtDecoder`, so they cannot prove the audience check. Verify it live: a
service-account token must get 401.

## Role-based matchers

When a design calls for one:

```java
.requestMatchers(HttpMethod.DELETE, "/dictionaries/**").hasAnyRole("user", "admin")
.requestMatchers(HttpMethod.GET, "/dictionaries/public/**").permitAll()
.anyRequest().authenticated()
```

`hasRole("user")` matches the `ROLE_user` authority produced by the extractor above.

## Testing it

`SecurityConfigTest` unit-tests `extractRealmRoles` directly against synthetic `Jwt` objects —
nested roles, no claim, empty claim, malformed claim, non-string entries. The filter chain itself is
covered by the web-slice tests, which must `@Import(SecurityConfig.class)` or their 401 and 403
assertions prove nothing. See `integration-testing`.
