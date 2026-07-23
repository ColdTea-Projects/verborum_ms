package de.coldtea.verborum.msuser.common.config;

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
 * Stateless JWT resource server configuration.
 * ms_user is also a resource server for its own endpoints — Keycloak issues the tokens,
 * this service only validates them (except for the Keycloak Admin Client calls used during
 * registration, which are a separate, service-to-service concern).
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
     * Written by hand because `JwtGrantedAuthoritiesConverter` cannot do it: its
     * `setAuthoritiesClaimName("realm_access.roles")` is a flat claim name, not a path expression,
     * so it looks for a top-level claim literally called `realm_access.roles`, finds nothing, and
     * silently grants no authorities. Keycloak nests realm roles one level down:
     * <pre>
     * { "realm_access": { "roles": ["user", "admin"] } }
     * </pre>
     * The failure mode is the dangerous kind — authentication still succeeds and every
     * `hasRole(...)` check just quietly fails — so do not "simplify" this back to the built-in
     * converter.
     * <p>
     * Realm roles only. Client roles live under `resource_access.{clientId}.roles` and are not used
     * in Verborum; add a second extraction here if that changes.
     */
    static Collection<GrantedAuthority> extractRealmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap(REALM_ACCESS_CLAIM);
        if (realmAccess == null) {
            return List.of();
        }

        // Defensive: the claim is attacker-adjacent input. A token whose realm_access.roles is
        // missing or not a list must yield no authorities, never an exception — a 500 on the
        // authentication path would turn a malformed token into a service error
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
