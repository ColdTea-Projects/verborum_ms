package de.coldtea.verborum.msdictionary.common.listener;

import de.coldtea.verborum.msdictionary.common.event.UserDeletedEvent;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msdictionary.common.config.RabbitMQConfig.QUEUE_USER_DELETED;

/**
 * Consumes events published by ms_user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DictionaryService dictionaryService;

    /**
     * Deletes every dictionary and word belonging to a deleted user.
     * <p>
     * <b>Keyed on `keycloakId`.</b> This service's `fk_user_id` is the JWT subject, which equals
     * ms_user's `keycloak_id`; the event's `userId` is ms_user's own primary key and matches nothing
     * here. Passing the wrong one deletes zero rows and looks like success.
     * <p>
     * Idempotent: deleteAllByUserId is a no-op when the user has no dictionaries left, so a
     * redelivery cannot fail or double-delete.
     */
    @RabbitListener(queues = QUEUE_USER_DELETED)
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted event for keycloakId: {} (ms_user userId: {})",
                event.getKeycloakId(), event.getUserId());
        try {
            dictionaryService.deleteAllByUserId(event.getKeycloakId());
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than
            // acknowledged as handled — a half-cascaded user deletion must not disappear silently
            log.error("Failed to process user.deleted event for keycloakId: {}",
                    event.getKeycloakId(), e);
            throw e;
        }
    }
}
