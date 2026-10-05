package de.coldtea.verborum.msmarketplace.common.constants;

/**
 * Success response messages used in controllers.
 * Populated as endpoints are added — see the `web-api` skill. Keep the trailing space on each:
 * buildResponse concatenates message + detail.
 */
public final class ResponseMessageConstants {

    //MarketplaceController
    public static final String DICTIONARY_IMPORTED_SUCCESSFULLY = "Imported successfully dictionary ";
    public static final String DICTIONARY_RATED_SUCCESSFULLY = "Rated successfully dictionary ";
    public static final String RATING_REMOVED_SUCCESSFULLY = "Removed rating of dictionary ";

    private ResponseMessageConstants() {
    }
}
