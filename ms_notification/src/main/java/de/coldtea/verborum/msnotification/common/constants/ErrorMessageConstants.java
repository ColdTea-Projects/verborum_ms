package de.coldtea.verborum.msnotification.common.constants;

/**
 * Error messages used in service/exception logic.
 * Populated as entities and endpoints are added — see the `persistence` / `web-api` skills.
 */
public final class ErrorMessageConstants {

    //Security — vague on purpose: must not reveal whether the resource exists or who owns it
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String METHOD_NOT_ALLOWED = "This HTTP method is not supported on this path";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

    // Push delivery (P4N-04) — log message, never sent to a client
    public static final String PUSH_SEND_FAILED = "FCM could not be reached or refused the request";

    // A unique constraint tripped by a concurrent write — a fixed message, never the constraint text
    public static final String DATA_CONFLICT = "The request conflicts with existing data";
    // RequestBodyLimitFilter (SEC-07)
    public static final String REQUEST_BODY_TOO_LARGE = "The request body must not exceed ";
    public static final String REQUEST_BODY_LENGTH_REQUIRED = "A request body must declare its Content-Length";

    // Request parameter that does not convert to its type; the parameter name is appended
    public static final String INVALID_PARAMETER = "Invalid value for parameter: ";

    private ErrorMessageConstants() {
    }
}
