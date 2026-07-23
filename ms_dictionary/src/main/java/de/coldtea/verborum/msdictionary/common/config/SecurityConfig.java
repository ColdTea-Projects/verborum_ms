package de.coldtea.verborum.msdictionary.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Stateless JWT resource server configuration (roadmap P3-03). Mirrors ms_user's SecurityConfig —
 * Keycloak issues the tokens, this service only validates them.
 * <p>
 * Until this existed, every ms_dictionary endpoint was open. Note that endpoints still trust the
 * `userId` in the request body rather than the token subject; moving to the subject is P3-05, so
 * authentication is enforced here but ownership is not yet.
 */
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

    /**
     * Maps Keycloak realm roles to Spring authorities.
     * <p>
     * Written by hand because `JwtGrantedAuthoritiesConverter`'s
     * `setAuthoritiesClaimName("realm_access.roles")` is a flat claim name, not a path expression:
     * it looks for a top-level claim literally called `realm_access.roles`, finds nothing, and
     * silently grants no authorities while authentication still succeeds. Keycloak nests them:
     * <pre>
     * { "realm_access": { "roles": ["user", "admin"] } }
     * </pre>
     * That bug shipped in ms_user and was fixed at P2-11; this service was written against the
     * corrected template. Do not "simplify" it back to the built-in converter.
     * <p>
     * Realm roles only — client roles under `resource_access.{clientId}.roles` are unused here.
     */
    static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return List.of();
        }

        // Defensive: a token whose realm_access.roles is missing or not a list must yield no
        // authorities, never an exception — a 500 on the authentication path would turn a malformed
        // token into a service error
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
