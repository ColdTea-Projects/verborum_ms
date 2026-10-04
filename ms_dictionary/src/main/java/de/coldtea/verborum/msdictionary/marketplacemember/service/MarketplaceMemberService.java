package de.coldtea.verborum.msdictionary.marketplacemember.service;

import de.coldtea.verborum.msdictionary.common.event.UserProfileUpdatedEvent;

/**
 * Marketplace membership as this service knows it (P4-16), and what joining and leaving do to the
 * user's dictionaries. Event-driven; the actor is ms_user, not a logged-in caller.
 */
public interface MarketplaceMemberService {

    /**
     * `user.profile.updated` — records the user's membership if the event is newer than what is held
     * (rule 4), and acts on a transition: joining makes all the user's dictionaries public, leaving
     * makes them all private. Anything else (a rename, a redelivery, an older event, a pre-P4-14 event
     * without the flag) changes no dictionary.
     */
    void applyProfileUpdate(UserProfileUpdatedEvent event);
}
