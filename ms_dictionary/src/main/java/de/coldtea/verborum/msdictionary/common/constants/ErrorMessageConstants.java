package de.coldtea.verborum.msdictionary.common.constants;

public final class ErrorMessageConstants {
    public static final String DICTIONARY_WAS_NOT_FOUND_ID = "Dictionary was not found. ID: ";

    //Security (P3-05) — kept vague on purpose: the message must not reveal whether the
    //resource exists or who owns it
    // P4-16: a marketplace member who has dictionaries keeps at least one shared
    public static final String MEMBER_MUST_KEEP_ONE_SHARED =
            "A marketplace member must keep at least one dictionary shared; leave the marketplace to make all of them private";
    public static final String NOT_THE_OWNER = "This resource does not belong to the authenticated user";
    // SEC-01: an existing wordId named under a different dictionary — never upserted across dictionaries
    // SEC-07: quotas — 400, the request is valid in shape but would exceed the owner's allowance
    public static final String DICTIONARY_QUOTA_EXCEEDED = "An account can have at most " + DTOMessageConstants.DICTIONARIES_PER_USER_MAX + " dictionaries";
    public static final String WORD_QUOTA_EXCEEDED = "A dictionary can have at most " + DTOMessageConstants.WORDS_PER_DICTIONARY_MAX + " words";
    public static final String WORD_IN_ANOTHER_DICTIONARY =
            "A word cannot be saved into a different dictionary than the one it belongs to";
    // Returned instead of an unhandled exception's own message, which would leak internals
    public static final String INTERNAL_SERVER_ERROR = "Internal server error";
    public static final String METHOD_NOT_ALLOWED = "This HTTP method is not supported on this path";
    // SEC-07: RequestBodyLimitFilter — the limit in bytes is appended
    public static final String REQUEST_BODY_TOO_LARGE = "The request body must not exceed ";
    public static final String REQUEST_BODY_LENGTH_REQUIRED = "A request body must declare its Content-Length";

    //OutboundEventPublisher — log message ({} placeholder)
    public static final String EVENT_PUBLISH_FAILED =
            "Failed to publish {} after commit. The write succeeded but the event never went out; "
                    + "consumers will not see it without a re-publish or a reconciliation run.";
    public static final String NO_AUTHENTICATED_USER = "No authenticated user found";

    //DictionarySnapshotScheduler — log message
    public static final String SNAPSHOT_PUBLISH_FAILED =
            "Failed to build dictionary.snapshot. Marketplace listings are not reconciled until the next run.";

}
