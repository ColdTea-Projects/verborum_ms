package de.coldtea.verborum.msgateway.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdentityHeaderFilterTest {

    private final IdentityHeaderFilter filter = new IdentityHeaderFilter();

    @Test
    void apply_RemovesTheWholeIdentityFamily_AnyCase() {
        // Arrange
        HttpHeaders input = new HttpHeaders();
        for (String name : new String[]{"X-User-Id", "x-user-email", "X-USER-ROLES", "X-Forwarded-User", "X-Forwarded-Email",
                "X-Forwarded-Preferred-Username", "X-Auth-Request-User", "X-Authenticated-UserId", "X-Remote-User",
                "Remote-User", "X-JWT-Sub", "X-Keycloak-Id", "X-Verborum-User", "X-Claim-Sub"}) {
            input.add(name, "attacker");
        }

        // Act
        HttpHeaders result = filter.apply(input, null);

        // Assert
        assertTrue(result.isEmpty(), result.toString());
    }

    @Test
    void apply_KeepsTheHeadersTheServicesNeed() {
        // Arrange
        HttpHeaders input = new HttpHeaders();
        input.add("Authorization", "Bearer a.b.c");
        input.add("X-Device-Id", "dev-1");
        input.add("Content-Type", "application/json");
        input.add("Accept-Language", "de");
        input.add("X-User-Id", "attacker");

        // Act
        HttpHeaders result = filter.apply(input, null);

        // Assert
        assertEquals("Bearer a.b.c", result.getFirst("Authorization"));
        assertEquals("dev-1", result.getFirst("X-Device-Id"));
        assertEquals("application/json", result.getFirst("Content-Type"));
        assertEquals("de", result.getFirst("Accept-Language"));
        assertFalse(result.containsKey("X-User-Id"));
    }
}
