package de.coldtea.verborum.msmarketplace.common.constants;

/**
 * Validation error messages and request limits used on request parameters and DTOs.
 * Populated as endpoints are added — see the `web-api` skill.
 */
public final class DTOMessageConstants {

    //@ValidUUID (P4-09) — the field name is prepended by the error handler
    public static final String INVALID_UUID = "must be a valid UUID";

    //Marketplace browse (P4-06) — paging. The maximum is a server-side cap: a client asking for
    //size=100000 must not turn one request into a full-table read
    // Strings: @RequestParam(defaultValue = ...) takes a String constant
    public static final String PAGE_DEFAULT = "0";
    public static final String PAGE_SIZE_DEFAULT = "20";
    public static final int PAGE_SIZE_MAX = 100;
    public static final String PAGE_NEGATIVE = "page must not be negative";
    public static final String PAGE_SIZE_OUT_OF_RANGE = "size must be between 1 and " + PAGE_SIZE_MAX;

    //Marketplace browse (P4-06) — language filter
    public static final String INVALID_LANGUAGE_CODE = "unsupported language code";

    private DTOMessageConstants() {
    }
}
