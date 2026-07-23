package de.coldtea.verborum.msuser.common.constants;

/**
 * Error messages used in service/exception logic.
 * Populated as entities and DTOs are added — see the `new-entity` / `new-endpoint` skills.
 */
public final class ErrorMessageConstants {

    public static final String USER_WAS_NOT_FOUND_ID = "User was not found. ID: ";
    public static final String USER_WAS_NOT_FOUND_KEYCLOAK_ID = "User was not found. Keycloak ID: ";

    //Security (P3-05) — vague on purpose: must not reveal whether the profile exists or who owns it
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

    //OutboundEventPublisher — log message ({} placeholder)
    public static final String EVENT_PUBLISH_FAILED =
            "Failed to publish {} after commit. The write succeeded but the event never went out; "
                    + "consumers will not see it without a re-publish or a reconciliation run.";

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
