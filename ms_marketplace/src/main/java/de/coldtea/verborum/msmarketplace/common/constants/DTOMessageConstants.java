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

    //Marketplace browse (P4-11) — language-pair filter. The cap keeps one request from expanding into
    //an arbitrarily long IN list; a learner's own dictionaries rarely span more than a handful of pairs
    public static final int LANGUAGE_PAIRS_MAX = 10;
    public static final String INVALID_LANGUAGE_PAIR = "must be two different supported language codes, e.g. EN-TR";
    public static final String TOO_MANY_LANGUAGE_PAIRS = "at most " + LANGUAGE_PAIRS_MAX + " language pairs";

    //Marketplace browse (P4-12) — tag filter. ms_dictionary sets no length limit on a tag, but a
    //filter value is a search term: the cap stops one request from carrying an arbitrarily large array
    public static final int TAGS_MAX = 10;
    public static final int TAG_MAX_LENGTH = 100;
    public static final String TOO_MANY_TAGS = "at most " + TAGS_MAX + " tags";
    public static final String TAG_BLANK = "must not be blank";
    public static final String TAG_TOO_LONG = "must be at most " + TAG_MAX_LENGTH + " characters";

    // Ratings (P4-19): whole stars only
    public static final int RATING_STARS_MIN = 1;
    public static final int RATING_STARS_MAX = 5;
    public static final String RATING_STARS_REQUIRED = "stars is mandatory";
    public static final String RATING_STARS_OUT_OF_RANGE = "stars must be between " + RATING_STARS_MIN + " and " + RATING_STARS_MAX;

    //Marketplace browse (P4-13) — publisher display-name filter. Three characters is the shortest search
    //a trigram index can serve; the maximum matches ms_user's display_name column
    public static final int PUBLISHER_NAME_MIN = 3;
    public static final int PUBLISHER_NAME_MAX = 255;
    public static final String PUBLISHER_NAME_LENGTH = "must be between " + PUBLISHER_NAME_MIN + " and " + PUBLISHER_NAME_MAX + " characters";

    private DTOMessageConstants() {
    }
}
