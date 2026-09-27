package de.coldtea.verborum.msmarketplace.common.listener;

import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_DICTIONARY_SNAPSHOT;
import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_DICTIONARY_UPDATED;
import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_DICTIONARY_VISIBILITY_PRIVATE;
import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.QUEUE_DICTIONARY_VISIBILITY_PUBLIC;

/**
 * Consumes events published by ms_dictionary (P4-03, P4-04). Each handler logs, delegates to one service
 * method, and re-throws on failure so the message is retried and finally dead-lettered rather than
 * acknowledged with the listing left wrong.
 * <p>
 * Every service method is idempotent and drops stale deliveries, so redeliveries — including a DLQ
 * replay — are safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DictionaryEventListener {

    private final DictionaryStatsService dictionaryStatsService;

    @RabbitListener(queues = QUEUE_DICTIONARY_VISIBILITY_PUBLIC)
    public void handleDictionaryPublic(DictionaryVisibilityEvent event) {
        log.info("Received dictionary.visibility.public for dictionaryId: {}", event.getDictionaryId());
        try {
            dictionaryStatsService.publishListing(event);
        } catch (Exception e) {
            log.error("Failed to process dictionary.visibility.public for dictionaryId: {}",
                    event.getDictionaryId(), e);
            throw e;
        }
    }

    @RabbitListener(queues = QUEUE_DICTIONARY_VISIBILITY_PRIVATE)
    public void handleDictionaryPrivate(DictionaryVisibilityEvent event) {
        log.info("Received dictionary.visibility.private for dictionaryId: {}", event.getDictionaryId());
        try {
            dictionaryStatsService.hideListing(event);
        } catch (Exception e) {
            log.error("Failed to process dictionary.visibility.private for dictionaryId: {}",
                    event.getDictionaryId(), e);
            throw e;
        }
    }

    @RabbitListener(queues = QUEUE_DICTIONARY_UPDATED)
    public void handleDictionaryUpdated(DictionaryUpdatedEvent event) {
        log.info("Received dictionary.updated for dictionaryId: {}", event.getDictionaryId());
        try {
            dictionaryStatsService.updateListing(event);
        } catch (Exception e) {
            log.error("Failed to process dictionary.updated for dictionaryId: {}", event.getDictionaryId(), e);
            throw e;
        }
    }

    @RabbitListener(queues = QUEUE_DICTIONARY_SNAPSHOT)
    public void handleDictionarySnapshot(DictionarySnapshotEvent event) {
        log.info("Received dictionary.snapshot taken at {}", event.getTakenAt());
        try {
            dictionaryStatsService.reconcile(event);
        } catch (Exception e) {
            log.error("Failed to process dictionary.snapshot taken at {}", event.getTakenAt(), e);
            throw e;
        }
    }
}
