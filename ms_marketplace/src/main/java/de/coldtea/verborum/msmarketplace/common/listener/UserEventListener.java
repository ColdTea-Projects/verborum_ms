package de.coldtea.verborum.msmarketplace.common.listener;

import de.coldtea.verborum.msmarketplace.common.event.UserDeletedEvent;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_USER_DELETED;

/**
 * Consumes events published by ms_user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DictionaryStatsService dictionaryStatsService;

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
            dictionaryStatsService.deleteListingsByUser(event.getKeycloakId());
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than acknowledged
            // with the user's listings still browsable
            log.error("Failed to process user.deleted event for keycloakId: {}", event.getKeycloakId(), e);
            throw e;
        }
    }
}
