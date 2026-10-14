package de.coldtea.verborum.msnotification.common.listener;

import de.coldtea.verborum.msnotification.common.event.DictionaryChangedEvent;
import de.coldtea.verborum.msnotification.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msnotification.common.config.RabbitMQConfig.QUEUE_DICTIONARY_CHANGED;

/**
 * Consumes events published by ms_dictionary.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DictionaryEventListener {

    private final NotificationService notificationService;

    /**
     * A user's dictionary, words or tags really changed (P4N-06): nudge their other devices. Keyed on the
     * owner's `keycloakId`; the event's `deviceId` (the writing install) is skipped.
     */
    @RabbitListener(queues = QUEUE_DICTIONARY_CHANGED)
    public void handleDictionaryChanged(DictionaryChangedEvent event) {
        log.info("Received dictionary.changed event for keycloakId: {} (dictionaryId: {})",
                event.getKeycloakId(), event.getDictionaryId());
        try {
            notificationService.syncUserDevices(event.getKeycloakId(), event.getDictionaryId(), event.getDeviceId());
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than acknowledged
            log.error("Failed to process dictionary.changed event for keycloakId: {}", event.getKeycloakId(), e);
            throw e;
        }
    }
}
