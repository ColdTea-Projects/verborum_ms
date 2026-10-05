package de.coldtea.verborum.msmarketplace.dictionaryrating.service;

import de.coldtea.verborum.msmarketplace.dictionaryrating.dto.RatingResponseDTO;

/**
 * Ratings (P4-20). Every method takes the caller explicitly — the JWT subject, never an id from the
 * request — so the class stays testable without a security context.
 */
public interface DictionaryRatingService {

    /**
     * Sets the caller's stars for a listing, creating or changing their one rating. Forum members only
     * (403); the listing must be listed with an active publisher (404, the same rule as import); never
     * the caller's own (400); only after importing it (403). Idempotent: the same stars again changes nothing.
     */
    RatingResponseDTO rateDictionary(String dictionaryId, String raterId, int stars);

    /** The caller's rating of a listing; 404 when they have none. */
    RatingResponseDTO getMyRating(String dictionaryId, String raterId);

    /** Removes the caller's rating; a no-op when they have none. Allowed even if the listing is hidden. */
    void removeRating(String dictionaryId, String raterId);

    /**
     * The user.deleted cleanup (P4-22): a deleted user's ratings are their opinions, so they go and the
     * listings' aggregates are recomputed — unlike import counts, which are history. Idempotent.
     */
    void deleteRatingsByUser(String keycloakId);
}
