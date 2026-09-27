package de.coldtea.verborum.msmarketplace.common.listener;

import de.coldtea.verborum.msmarketplace.common.event.UserDeletedEvent;
import de.coldtea.verborum.msmarketplace.dictionarystats.service.DictionaryStatsService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class UserEventListenerTest {

    private static final String USER_ID = "u-1";
    private static final String KEYCLOAK_ID = "kc-1";

    @Mock
    private DictionaryStatsService dictionaryStatsService;

    @InjectMocks
    private UserEventListener userEventListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static UserDeletedEvent event() {
        return UserDeletedEvent.builder()
                .userId(USER_ID)
                .keycloakId(KEYCLOAK_ID)
                .eventTimestamp(OffsetDateTime.now())
                .build();
    }

    @Test
    void handleUserDeleted_DeletesOnKeycloakId() {
        // Act
        userEventListener.handleUserDeleted(event());

        // Assert — fk_user_id holds the JWT subject; ms_user's userId would match nothing and quietly
        // report success
        verify(dictionaryStatsService).deleteListingsByUser(KEYCLOAK_ID);
        verify(dictionaryStatsService, never()).deleteListingsByUser(USER_ID);
    }

    @Test
    void handleUserDeleted_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        doThrow(new RuntimeException("db down")).when(dictionaryStatsService).deleteListingsByUser(KEYCLOAK_ID);

        // Act & Assert — swallowing would ack the event with the user's listings still browsable
        assertThrows(RuntimeException.class, () -> userEventListener.handleUserDeleted(event()));
    }
}
