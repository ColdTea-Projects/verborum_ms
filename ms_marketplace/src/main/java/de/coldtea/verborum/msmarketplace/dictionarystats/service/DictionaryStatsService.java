package de.coldtea.verborum.msmarketplace.dictionarystats.service;

import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;

/**
 * Keeps the `dictionary_stats` read model in step with ms_dictionary (P4-03).
 * <p>
 * Every method is event-driven and takes no caller: the actor is ms_dictionary, not a logged-in
 * user, so there is no ownership check. Every method is idempotent and drops anything not newer
 * than what the listing already holds (rules 3 and 4), so redeliveries and out-of-order deliveries
 * are harmless.
 */
public interface DictionaryStatsService {

    /**
     * `dictionary.visibility.public` — creates the listing, or updates it if the event is newer.
     */
    void publishListing(DictionaryVisibilityEvent event);

    /**
     * `dictionary.updated` — updates an existing listing if the event is newer. Never creates one:
     * an update for a listing that is not here may belong to a dictionary already made private or
     * deleted, and recreating it would re-list it. A genuinely missed listing is restored by the
     * snapshot.
     */
    void updateListing(DictionaryUpdatedEvent event);

    /**
     * `dictionary.snapshot` — reconciles every listing against ms_dictionary's full set of public
     * dictionaries (rule 6): creates missing listings, updates stale ones, and removes listings that
     * are no longer public.
     */
    void reconcile(DictionarySnapshotEvent event);
}
