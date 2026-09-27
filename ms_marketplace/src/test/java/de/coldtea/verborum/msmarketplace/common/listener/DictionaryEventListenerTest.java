package de.coldtea.verborum.msmarketplace.common.listener;

import de.coldtea.verborum.msmarketplace.common.event.DictionarySnapshotEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryUpdatedEvent;
import de.coldtea.verborum.msmarketplace.common.event.DictionaryVisibilityEvent;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class DictionaryEventListenerTest {

    @Mock
    private DictionaryStatsService dictionaryStatsService;

    @InjectMocks
    private DictionaryEventListener dictionaryEventListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void handleDictionaryPublic_DelegatesToPublishListing() {
        // Arrange
        DictionaryVisibilityEvent event = DictionaryVisibilityEvent.builder().dictionaryId("dict1").build();

        // Act
        dictionaryEventListener.handleDictionaryPublic(event);

        // Assert
        verify(dictionaryStatsService).publishListing(event);
    }

    @Test
    void handleDictionaryPublic_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        DictionaryVisibilityEvent event = DictionaryVisibilityEvent.builder().dictionaryId("dict1").build();
        doThrow(new RuntimeException("db down")).when(dictionaryStatsService).publishListing(event);

        // Act & Assert — swallowing would ack the message with the listing never written
        assertThrows(RuntimeException.class, () -> dictionaryEventListener.handleDictionaryPublic(event));
    }

    @Test
    void handleDictionaryUpdated_DelegatesToUpdateListing() {
        // Arrange
        DictionaryUpdatedEvent event = DictionaryUpdatedEvent.builder().dictionaryId("dict1").build();

        // Act
        dictionaryEventListener.handleDictionaryUpdated(event);

        // Assert
        verify(dictionaryStatsService).updateListing(event);
    }

    @Test
    void handleDictionaryUpdated_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        DictionaryUpdatedEvent event = DictionaryUpdatedEvent.builder().dictionaryId("dict1").build();
        doThrow(new RuntimeException("db down")).when(dictionaryStatsService).updateListing(event);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> dictionaryEventListener.handleDictionaryUpdated(event));
    }

    @Test
    void handleDictionarySnapshot_DelegatesToReconcile() {
        // Arrange
        DictionarySnapshotEvent event = DictionarySnapshotEvent.builder()
                .takenAt(OffsetDateTime.now())
                .dictionaries(List.of())
                .build();

        // Act
        dictionaryEventListener.handleDictionarySnapshot(event);

        // Assert
        verify(dictionaryStatsService).reconcile(event);
    }

    @Test
    void handleDictionarySnapshot_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        DictionarySnapshotEvent event = DictionarySnapshotEvent.builder().takenAt(OffsetDateTime.now()).build();
        doThrow(new RuntimeException("db down")).when(dictionaryStatsService).reconcile(event);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> dictionaryEventListener.handleDictionarySnapshot(event));
    }
}
