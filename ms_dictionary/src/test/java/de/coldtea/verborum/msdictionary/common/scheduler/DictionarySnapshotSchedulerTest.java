package de.coldtea.verborum.msdictionary.common.scheduler;

import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class DictionarySnapshotSchedulerTest {

    @Mock
    private DictionaryService dictionaryService;

    @InjectMocks
    private DictionarySnapshotScheduler dictionarySnapshotScheduler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void publishSnapshot_DelegatesToTheService() {
        // Act
        dictionarySnapshotScheduler.publishSnapshot();

        // Assert
        verify(dictionaryService).publishPublicSnapshot();
    }

    @Test
    void publishSnapshot_ServiceFails_DoesNotThrow() {
        // Arrange
        doThrow(new RuntimeException("db down")).when(dictionaryService).publishPublicSnapshot();

        // Act & Assert — nothing upstream can handle it; the next scheduled run is the retry
        assertDoesNotThrow(() -> dictionarySnapshotScheduler.publishSnapshot());
    }
}
