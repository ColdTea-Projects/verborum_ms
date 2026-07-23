package de.coldtea.verborum.msuser.common.listener;

import de.coldtea.verborum.msuser.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.vault.service.VaultService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MarketplaceEventListenerTest {

    private static final String KEYCLOAK_ID = "kc-1";
    private static final String DICTIONARY_ID = "2";

    @Mock
    private VaultService vaultService;

    @InjectMocks
    private MarketplaceEventListener marketplaceEventListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void handleDictionaryImported_Success() {
        // Arrange
        DictionaryImportedEvent event = DictionaryImportedEvent.builder()
                .dictionaryId(DICTIONARY_ID)
                .keycloakId(KEYCLOAK_ID)
                .eventTimestamp(OffsetDateTime.now())
                .build();

        // Act
        marketplaceEventListener.handleDictionaryImported(event);

        // Assert
        verify(vaultService).importDictionary(KEYCLOAK_ID, DICTIONARY_ID);
    }

    @Test
    void handleDictionaryImported_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        DictionaryImportedEvent event = DictionaryImportedEvent.builder()
                .dictionaryId(DICTIONARY_ID)
                .keycloakId(KEYCLOAK_ID)
                .eventTimestamp(OffsetDateTime.now())
                .build();

        when(vaultService.importDictionary(KEYCLOAK_ID, DICTIONARY_ID))
                .thenThrow(new RecordNotFoundException("User was not found"));

        // Act & Assert — swallowing here would ack the message and lose the import silently
        assertThrows(RecordNotFoundException.class, () -> marketplaceEventListener.handleDictionaryImported(event));
    }
}
