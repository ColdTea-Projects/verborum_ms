package de.coldtea.verborum.msmarketplace.dictionaryimport.service.impl;

import de.coldtea.verborum.msmarketplace.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msmarketplace.common.event.OutboundEvent;
import de.coldtea.verborum.msmarketplace.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msmarketplace.common.exception.SelfImportException;
import de.coldtea.verborum.msmarketplace.dictionaryimport.entity.DictionaryImport;
import de.coldtea.verborum.msmarketplace.dictionaryimport.repository.DictionaryImportRepository;
import de.coldtea.verborum.msmarketplace.dictionarystats.entity.DictionaryStats;
import de.coldtea.verborum.msmarketplace.dictionarystats.repository.DictionaryStatsRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static de.coldtea.verborum.msmarketplace.common.config.RabbitMQConfig.ROUTING_KEY_DICTIONARY_IMPORTED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class DictionaryImportServiceImplTest {

    private static final String DICTIONARY_ID = "dict1";
    private static final String PUBLISHER = "kc-publisher";
    private static final String IMPORTER = "kc-importer";

    @Mock
    private DictionaryImportRepository dictionaryImportRepository;

    @Mock
    private DictionaryStatsRepository dictionaryStatsRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DictionaryImportServiceImpl dictionaryImportService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static DictionaryStats listing(boolean listed) {
        return DictionaryStats.builder()
                .dictionaryId(DICTIONARY_ID)
                .userId(PUBLISHER)
                .isListed(listed)
                .importCount(4)
                .build();
    }

    private OutboundEvent capturedEvent() {
        ArgumentCaptor<OutboundEvent> captor = ArgumentCaptor.forClass(OutboundEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void importDictionary_FirstImport_RecordsCountsAndPublishes() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing(true)));
        when(dictionaryImportRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, IMPORTER)).thenReturn(Optional.empty());

        // Act
        dictionaryImportService.importDictionary(DICTIONARY_ID, IMPORTER);

        // Assert
        ArgumentCaptor<DictionaryImport> saved = ArgumentCaptor.forClass(DictionaryImport.class);
        verify(dictionaryImportRepository).saveAndFlush(saved.capture());
        assertEquals(DICTIONARY_ID, saved.getValue().getDictionaryId());
        assertEquals(IMPORTER, saved.getValue().getUserId());
        assertNotNull(saved.getValue().getImportId());
        verify(dictionaryStatsRepository).incrementImportCount(DICTIONARY_ID);

        OutboundEvent outbound = capturedEvent();
        assertEquals(ROUTING_KEY_DICTIONARY_IMPORTED, outbound.routingKey());
        DictionaryImportedEvent event = (DictionaryImportedEvent) outbound.payload();
        assertEquals(DICTIONARY_ID, event.getDictionaryId());
        // ms_user matches on keycloakId — the importer's subject
        assertEquals(IMPORTER, event.getKeycloakId());
        assertNotNull(event.getEventTimestamp());
    }

    @Test
    void importDictionary_RepeatImport_DoesNotCountAgainButStillPublishes() {
        // Arrange — counting once per user is what stops popularity being inflated by repeat calls
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing(true)));
        when(dictionaryImportRepository.findByDictionaryIdAndUserId(DICTIONARY_ID, IMPORTER))
                .thenReturn(Optional.of(DictionaryImport.builder().importId("i1").build()));

        // Act
        dictionaryImportService.importDictionary(DICTIONARY_ID, IMPORTER);

        // Assert — the re-send is harmless (ms_user's vault is idempotent) and repairs a lost first event
        verify(dictionaryImportRepository, never()).saveAndFlush(any());
        verify(dictionaryStatsRepository, never()).incrementImportCount(anyString());
        assertEquals(ROUTING_KEY_DICTIONARY_IMPORTED, capturedEvent().routingKey());
    }

    @Test
    void importDictionary_HiddenListing_Is404AndChangesNothing() {
        // Arrange — private or deleted: not importable, and indistinguishable from absent
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing(false)));

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryImportService.importDictionary(DICTIONARY_ID, IMPORTER));
        verifyNoInteractions(dictionaryImportRepository, eventPublisher);
        verify(dictionaryStatsRepository, never()).incrementImportCount(anyString());
    }

    @Test
    void importDictionary_UnknownListing_Is404() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(RecordNotFoundException.class, () -> dictionaryImportService.importDictionary(DICTIONARY_ID, IMPORTER));
        verifyNoInteractions(dictionaryImportRepository, eventPublisher);
    }

    @Test
    void importDictionary_OwnDictionary_IsRejectedAndChangesNothing() {
        // Arrange
        when(dictionaryStatsRepository.findById(DICTIONARY_ID)).thenReturn(Optional.of(listing(true)));

        // Act & Assert
        assertThrows(SelfImportException.class, () -> dictionaryImportService.importDictionary(DICTIONARY_ID, PUBLISHER));
        verifyNoInteractions(dictionaryImportRepository, eventPublisher);
        verify(dictionaryStatsRepository, never()).incrementImportCount(anyString());
    }
}
