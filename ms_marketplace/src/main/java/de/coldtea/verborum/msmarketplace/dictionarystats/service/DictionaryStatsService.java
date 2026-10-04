package de.coldtea.verborum.msmarketplace.dictionarystats.service;

import de.coldtea.verborum.msmarketplace.common.response.SliceResponse;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.DictionaryListingResponseDTO;
import de.coldtea.verborum.msmarketplace.dictionarystats.dto.ListingFilter;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryDeletedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;

/**
 * The marketplace listings: browse reads (P4-06), and keeping the `dictionary_stats` read model in
 * step with ms_dictionary (P4-03..P4-05).
 * <p>
 * Browse reads take no caller either: every listed dictionary is public, so there is nothing to
 * filter by owner. Every event-driven method takes no caller: the actor is ms_dictionary, not a logged-in
 * user, so there is no ownership check. Every method is idempotent and drops anything not newer
 * than what the listing already holds (rules 3 and 4), so redeliveries and out-of-order deliveries
 * are harmless.
 */
public interface DictionaryStatsService {

    // ---- Browse (P4-06; filters and slices P4-11..P4-13). Listed rows of member publishers only, for
    // member callers only: callerId is the token subject, and a non-member gets 403 (the Forum gate) ----

    /** Newest first, narrowed by whichever filters are set. */
    SliceResponse<DictionaryListingResponseDTO> getListings(ListingFilter filter, int page, int size, String callerId);

    /** Most imported first, newest first among equals, narrowed by whichever filters are set. */
    SliceResponse<DictionaryListingResponseDTO> getPopularListings(ListingFilter filter, int page, int size, String callerId);

    /** One publisher's listings, newest first — "more from this publisher". */
    SliceResponse<DictionaryListingResponseDTO> getListingsByPublisher(String publisherId, int page, int size, String callerId);

    // ---- Event-driven projection (P4-03..P4-05) ----

    /**
     * `dictionary.visibility.public` — creates the listing, or updates it if the event is newer,
     * re-listing a hidden row.
     */
    void publishListing(DictionaryVisibilityEvent event);

    /**
     * `dictionary.visibility.private` — hides the listing if the event is newer (P4-04). The row is
     * kept, not deleted, so its `sourceUpdatedAt` rejects older public state arriving late. With no
     * row yet (the private event overtook the public one), a hidden row is created for the same reason.
     */
    void hideListing(DictionaryVisibilityEvent event);

    /**
     * `dictionary.updated` — updates an existing listing if the event is newer, re-listing it if it
     * was hidden (the event is only sent for public dictionaries). Never creates one:
     * an update for a listing that is not here may belong to a dictionary already made private or
     * deleted, and recreating it would re-list it. A genuinely missed listing is restored by the
     * snapshot.
     */
    void updateListing(DictionaryUpdatedEvent event);

    /**
     * `dictionary.deleted` — hides the listing if the event is newer (P4-05). Hidden rather than
     * deleted for the same reason as `hideListing`: the row's timestamp keeps a late, older public
     * event from re-listing a dictionary that no longer exists. The snapshot removes it afterwards.
     */
    void hideDeletedListing(DictionaryDeletedEvent event);

    /**
     * `user.deleted` — deletes every row owned by that user, listed or hidden (P4-05), the records
     * of what they imported (P4-07), and their display name (P4-13).
     *
     * @param keycloakId the event's `keycloakId` — the JWT subject stored in `fk_user_id`. Never the
     *                   event's `userId`, which is ms_user's own key and matches nothing here
     */
    void deleteListingsByUser(String keycloakId);

    /**
     * `dictionary.snapshot` — reconciles every listing against ms_dictionary's full set of public
     * dictionaries (rule 6): creates missing listings, updates stale ones, and removes listings that
     * are no longer public.
     */
    void reconcile(DictionarySnapshotEvent event);
}
