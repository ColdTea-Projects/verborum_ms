package de.coldtea.verborum.msmarketplace.common.constants;

/**
 * Error messages used in service/exception logic.
 * Populated as entities and endpoints are added — see the `persistence` / `web-api` skills.
 */
public final class ErrorMessageConstants {

    //Security — vague on purpose: must not reveal whether the resource exists or who owns it
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

    private ErrorMessageConstants() {
    }
}
