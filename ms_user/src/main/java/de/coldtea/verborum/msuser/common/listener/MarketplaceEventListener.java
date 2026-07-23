package de.coldtea.verborum.msuser.common.listener;

import de.coldtea.verborum.msuser.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msuser.vault.service.VaultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.QUEUE_DICTIONARY_IMPORTED;

/**
 * Consumes events published by ms_marketplace. Nothing publishes them until P4-07 — the queue is
 * durable and bound, so imports are captured from the moment that service ships.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MarketplaceEventListener {

    private final VaultService vaultService;

    /**
     * Adds the imported dictionary to the user's vault. The event identifies the user by
     * `keycloakId` (the only user id ms_marketplace sees); VaultService resolves it to ms_user's own
     * user_id.
     * <p>
     * Idempotent: importDictionary delegates to addVaultEntry, which returns the existing entry for
     * an already-imported (user, dictionary) pair, so a redelivery cannot create a duplicate or
     * trip the composite UNIQUE.
     */
    @RabbitListener(queues = QUEUE_DICTIONARY_IMPORTED)
    public void handleDictionaryImported(DictionaryImportedEvent event) {
        log.info("Received dictionary.imported event for keycloakId: {}, dictionaryId: {}",
                event.getKeycloakId(), event.getDictionaryId());
        try {
            vaultService.importDictionary(event.getKeycloakId(), event.getDictionaryId());
        } catch (Exception e) {
            // Re-thrown on purpose: the message is retried and finally dead-lettered rather than
            // acknowledged as handled. An unknown keycloakId lands here — a real inconsistency
            // worth seeing in the DLQ, not swallowing.
            log.error("Failed to process dictionary.imported event for keycloakId: {}, dictionaryId: {}",
                    event.getKeycloakId(), event.getDictionaryId(), e);
            throw e;
        }
    }
}
