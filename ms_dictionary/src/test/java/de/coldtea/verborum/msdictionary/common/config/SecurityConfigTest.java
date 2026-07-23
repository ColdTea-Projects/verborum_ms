package de.coldtea.verborum.msdictionary.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("sub", "kc-1")
                .claim(name, value)
                .build();
    }

    @Test
    void extractRealmRoles_MapsNestedKeycloakRoles() {
        // Arrange — the shape Keycloak actually issues
        Jwt jwt = jwtWithClaim("realm_access", Map.of("roles", List.of("user", "admin")));

        // Act
        Collection<GrantedAuthority> authorities = SecurityConfig.extractRealmRoles(jwt);

        // Assert
        List<String> names = authorities.stream().map(GrantedAuthority::getAuthority).sorted().toList();
        assertEquals(List.of("ROLE_admin", "ROLE_user"), names);
    }

    @Test
    void extractRealmRoles_NoRealmAccessClaim() {
        // Act & Assert
        assertTrue(SecurityConfig.extractRealmRoles(jwtWithClaim("scope", "openid")).isEmpty());
    }

    @Test
    void extractRealmRoles_RealmAccessWithoutRoles() {
        // Act & Assert
        assertTrue(SecurityConfig.extractRealmRoles(jwtWithClaim("realm_access", Map.of())).isEmpty());
    }

    @Test
    void extractRealmRoles_MalformedRolesClaim() {
        // Arrange — roles is not a list; must not blow up the authentication path
        Jwt jwt = jwtWithClaim("realm_access", Map.of("roles", "user"));

        // Act & Assert
        assertTrue(SecurityConfig.extractRealmRoles(jwt).isEmpty());
    }

    @Test
    void extractRealmRoles_SkipsNonStringEntries() {
        // Arrange
        Jwt jwt = jwtWithClaim("realm_access", Map.of("roles", List.of("user", 42)));

        // Act
        Collection<GrantedAuthority> authorities = SecurityConfig.extractRealmRoles(jwt);

        // Assert
        assertEquals(List.of("ROLE_user"), authorities.stream().map(GrantedAuthority::getAuthority).toList());
    }
}
