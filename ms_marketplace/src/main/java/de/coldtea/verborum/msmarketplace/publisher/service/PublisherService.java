package de.coldtea.verborum.msmarketplace.publisher.service;

import de.coldtea.verborum.msmarketplace.common.event.UserProfileUpdatedEvent;

/**
 * Publisher display names (P4-13), fed by ms_user. Event-driven only; browse reads them through
 * `DictionaryStatsService`.
 */
public interface PublisherService {

    /**
     * `user.profile.updated` — creates the publisher, or updates the name and marketplace agreement if
     * the event is newer than what is held (rule 4). Idempotent: a redelivery is not newer and changes nothing. A null or
     * blank name is stored as null, which hides that publisher's listings.
     */
    void updateDisplayName(UserProfileUpdatedEvent event);
}
