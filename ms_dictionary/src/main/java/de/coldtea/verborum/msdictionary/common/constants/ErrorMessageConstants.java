package de.coldtea.verborum.msdictionary.common.constants;

public final class ErrorMessageConstants {
    public static final String DICTIONARY_WAS_NOT_FOUND_ID = "Dictionary was not found. ID: ";

    //Security (P3-05) — kept vague on purpose: the message must not reveal whether the
    //resource exists or who owns it
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";

    //OutboundEventPublisher — log message ({} placeholder)
    public static final String EVENT_PUBLISH_FAILED =
            "Failed to publish {} after commit. The write succeeded but the event never went out; "
                    + "consumers will not see it without a re-publish or a reconciliation run.";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

}
