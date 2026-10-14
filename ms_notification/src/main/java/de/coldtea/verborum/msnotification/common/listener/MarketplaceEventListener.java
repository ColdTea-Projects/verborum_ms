package de.coldtea.verborum.msnotification.common.listener;

import de.coldtea.verborum.msnotification.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msnotification.common.event.DictionaryRatedEvent;
import de.coldtea.verborum.msnotification.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msnotification.common.config.RabbitMQConfig.QUEUE_DICTIONARY_IMPORTED;
import static de.coldtea.verborum.msnotification.common.config.RabbitMQConfig.QUEUE_DICTIONARY_RATED;

/**
 * Consumes events published by ms_marketplace.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketplaceEventListener {

    private final NotificationService notificationService;

    /** Someone imported a publisher's dictionary: tell the publisher (`publisherId`), not the importer. */
    @RabbitListener(queues = QUEUE_DICTIONARY_IMPORTED)
    public void handleDictionaryImported(DictionaryImportedEvent event) {
        log.info("Received dictionary.imported event for dictionaryId: {}", event.getDictionaryId());
        try {
            notificationService.refreshPublisher(event.getPublisherId(), event.getKeycloakId(), event.getDictionaryId());
        } catch (Exception e) {
            log.error("Failed to process dictionary.imported event for dictionaryId: {}", event.getDictionaryId(), e);
            throw e;
        }
    }

    /** Someone rated a publisher's dictionary (new or changed stars): tell the publisher, not the rater. */
    @RabbitListener(queues = QUEUE_DICTIONARY_RATED)
    public void handleDictionaryRated(DictionaryRatedEvent event) {
        log.info("Received dictionary.rated event for dictionaryId: {}", event.getDictionaryId());
        try {
            notificationService.refreshPublisher(event.getPublisherId(), event.getRaterKeycloakId(), event.getDictionaryId());
        } catch (Exception e) {
            log.error("Failed to process dictionary.rated event for dictionaryId: {}", event.getDictionaryId(), e);
            throw e;
        }
    }
}
