package de.coldtea.verborum.msgateway.common.constants;

/**
 * Error messages used by the gateway's own handlers. Upstream services keep their own.
 */
public final class ErrorMessageConstants {

    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String METHOD_NOT_ALLOWED = "This HTTP method is not supported on this path";

    // RequestBodyLimitFilter (SEC-07)
    public static final String REQUEST_BODY_TOO_LARGE = "The request body must not exceed ";
    public static final String REQUEST_BODY_LENGTH_REQUIRED = "A request body must declare its Content-Length";

    private ErrorMessageConstants() {
    }
}
