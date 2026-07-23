package de.coldtea.verborum.msuser.common.constants;

/**
 * Error messages used in service/exception logic.
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class ErrorMessageConstants {

    public static final String USER_WAS_NOT_FOUND_ID = "User was not found. ID: ";
    public static final String USER_WAS_NOT_FOUND_KEYCLOAK_ID = "User was not found. Keycloak ID: ";

    private ErrorMessageConstants() {
    }
}
