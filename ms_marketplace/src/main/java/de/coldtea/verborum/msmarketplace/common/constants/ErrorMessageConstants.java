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
    public static final String METHOD_NOT_ALLOWED = "This HTTP method is not supported on this path";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";
    //Import (P4-07)
    public static final String LISTING_WAS_NOT_FOUND_ID = "Listing was not found. ID: ";
    public static final String CANNOT_IMPORT_OWN_DICTIONARY = "A dictionary cannot be imported by its own publisher";
    // The Forum gate (403): browse and import are for marketplace members only
    public static final String MARKETPLACE_MEMBERSHIP_REQUIRED =
            "Join the marketplace first: set a display name and accept the marketplace terms in your profile";

    //OutboundEventPublisher — log message ({} placeholder)
    public static final String EVENT_PUBLISH_FAILED =
            "Failed to publish {} after commit. The write succeeded but the event never went out; "
                    + "consumers will not see it without a re-publish or a reconciliation run.";

    // Request parameter that does not convert to its type; the parameter name is appended
    public static final String INVALID_PARAMETER = "Invalid value for parameter: ";

    private ErrorMessageConstants() {
    }
}
