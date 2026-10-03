package de.coldtea.verborum.msmarketplace.dictionaryimport.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msmarketplace.common.event.OutboundEvent;
import de.coldtea.verborum.msmarketplace.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msmarketplace.common.exception.SelfImportException;
import de.coldtea.verborum.msmarketplace.dictionaryimport.entity.DictionaryImport;
import de.coldtea.verborum.msmarketplace.dictionaryimport.repository.DictionaryImportRepository;
import de.coldtea.verborum.msmarketplace.dictionaryimport.service.DictionaryImportService;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_IMPORTED;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.CANNOT_IMPORT_OWN_DICTIONARY;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.LISTING_WAS_NOT_FOUND_ID;

@Service
@RequiredArgsConstructor
public class DictionaryImportServiceImpl implements DictionaryImportService {

    private final DictionaryImportRepository dictionaryImportRepository;

    private final DictionaryStatsRepository dictionaryStatsRepository;

    // Not RabbitTemplate: OutboundEventPublisher sends after this transaction commits (rule 1)
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    @Override
    public void importDictionary(String dictionaryId, String importerId) {
        // Hidden is the same as absent: a private or deleted dictionary must not be importable, and
        // saying "exists but hidden" would leak that it exists
        DictionaryStats listing = dictionaryStatsRepository.findById(dictionaryId)
                .filter(stats -> Boolean.TRUE.equals(stats.getIsListed()))
                .orElseThrow(() -> new RecordNotFoundException(LISTING_WAS_NOT_FOUND_ID + dictionaryId));

        if (importerId.equals(listing.getUserId())) {
            throw new SelfImportException(CANNOT_IMPORT_OWN_DICTIONARY);
        }

        // Count unique importers only. A concurrent double-tap by the same user can still race past
        // this check: the second transaction then fails on the UNIQUE constraint and rolls back whole —
        // no second count, no event — and the caller gets a 500. There is no server-side retry; the
        // client's retry finds this row and takes the idempotent path. The count is never wrong
        if (dictionaryImportRepository.findByDictionaryIdAndUserId(dictionaryId, importerId).isEmpty()) {
            dictionaryImportRepository.saveAndFlush(DictionaryImport.builder()
                    .importId(UUID.randomUUID().toString())
                    .dictionaryId(dictionaryId)
                    .userId(importerId)
                    .build());
            dictionaryStatsRepository.incrementImportCount(dictionaryId);
        }

        // Every successful call publishes, first import or not — see the interface
        eventPublisher.publishEvent(new OutboundEvent(
                ROUTING_KEY_DICTIONARY_IMPORTED,
                DictionaryImportedEvent.builder()
                        .dictionaryId(dictionaryId)
                        // The importer's JWT subject — ms_user matches on keycloakId, never its userId
                        .keycloakId(importerId)
                        .eventTimestamp(OffsetDateTime.now())
                        .build()));
    }
}
