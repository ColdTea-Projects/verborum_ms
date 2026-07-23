package de.coldtea.verborum.msuser.common.constants;

/**
 * Error messages used in service/exception logic.
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class ErrorMessageConstants {

    public static final String USER_WAS_NOT_FOUND_ID = "User was not found. ID: ";
    public static final String USER_WAS_NOT_FOUND_KEYCLOAK_ID = "User was not found. Keycloak ID: ";

    //KeycloakUserService — log messages ({} placeholders, not concatenated)
    public static final String KEYCLOAK_ADMIN_NOT_CONFIGURED =
            "keycloak.admin.client-secret is not set - the Keycloak identity for {} was NOT deleted. "
                    + "Set KEYCLOAK_ADMIN_CLIENT_SECRET to enable account deletion.";
    public static final String KEYCLOAK_USER_DELETE_FAILED =
            "Failed to delete the Keycloak identity {} (status {}). The profile and its data are gone; "
                    + "this account must be removed manually.";

    private ErrorMessageConstants() {
    }
}
