package de.coldtea.verborum.msmarketplace.common.listener;

import de.coldtea.verborum.msmarketplace.common.event.UserDeletedEvent;
import de.coldtea.verborum.msmarketplace.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msmarketplace.dictionaryrating.service.DictionaryRatingService;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import de.coldtea.verborum.msmarketplace.publisher.service.PublisherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_USER_DELETED;
import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_USER_PROFILE_UPDATED;

/**
 * Consumes events published by ms_user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DictionaryStatsService dictionaryStatsService;

    // P4-22: a deleted user's ratings go before their listings, so other listings' averages are corrected
    private final DictionaryRatingService dictionaryRatingService;

    private final PublisherService publisherService;

    /**
     * Deletes every marketplace row of a deleted user (P4-05).
     * <p>
     * <b>Keyed on `keycloakId`.</b> This service's `fk_user_id` is the JWT subject, which equals
     * ms_user's `keycloak_id`; the event's `userId` is ms_user's own primary key and matches nothing
     * here. Passing the wrong one deletes zero rows and looks like success.
     */
    @RabbitListener(queues = QUEUE_USER_DELETED)
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted event for keycloakId: {} (ms_user userId: {})",
                event.getKeycloakId(), event.getUserId());
        try {
            dictionaryRatingService.deleteRatingsByUser(event.getKeycloakId());
            dictionaryStatsService.deleteListingsByUser(event.getKeycloakId());
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than acknowledged
            // with the user's listings still browsable
            log.error("Failed to process user.deleted event for keycloakId: {}", event.getKeycloakId(), e);
            throw e;
        }
    }

    /**
     * A publisher's display name was set, changed or cleared (P4-13). Keyed on `keycloakId`, like
     * user.deleted.
     */
    @RabbitListener(queues = QUEUE_USER_PROFILE_UPDATED)
    public void handleUserProfileUpdated(UserProfileUpdatedEvent event) {
        log.info("Received user.profile.updated event for keycloakId: {}", event.getKeycloakId());
        try {
            publisherService.updateDisplayName(event);
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than lost
            log.error("Failed to process user.profile.updated event for keycloakId: {}", event.getKeycloakId(), e);
            throw e;
        }
    }
}
