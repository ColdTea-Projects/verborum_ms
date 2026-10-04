package de.coldtea.verborum.msdictionary.common.listener;

import de.coldtea.verborum.msdictionary.common.event.UserDeletedEvent;
import de.coldtea.verborum.msdictionary.common.event.UserProfileUpdatedEvent;
import de.coldtea.verborum.msdictionary.marketplacemember.service.MarketplaceMemberService;
import de.coldtea.verborum.msdictionary.dictionary.service.DictionaryService;

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
    private DictionaryService dictionaryService;

    @Mock
    private MarketplaceMemberService marketplaceMemberService;

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
    void handleUserDeleted_CascadesOnKeycloakId() {
        // Act
        userEventListener.handleUserDeleted(event());

        // Assert — fk_user_id holds the JWT subject; cascading on ms_user's userId would match
        // nothing and quietly report success
        verify(dictionaryService).deleteAllByUserId(KEYCLOAK_ID);
        verify(dictionaryService, never()).deleteAllByUserId(USER_ID);
    }

    @Test
    void handleUserDeleted_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        doThrow(new RuntimeException("db down")).when(dictionaryService).deleteAllByUserId(KEYCLOAK_ID);

        // Act & Assert — swallowing would ack a half-finished cascade
        assertThrows(RuntimeException.class, () -> userEventListener.handleUserDeleted(event()));
    }

    // ---- user.profile.updated (P4-16) ----

    @Test
    void handleUserProfileUpdated_Delegates() {
        // Arrange
        UserProfileUpdatedEvent event = UserProfileUpdatedEvent.builder()
                .keycloakId(KEYCLOAK_ID).marketplaceAgreementAccepted(true).updatedAt(OffsetDateTime.now()).build();

        // Act
        userEventListener.handleUserProfileUpdated(event);

        // Assert
        verify(marketplaceMemberService).applyProfileUpdate(event);
    }

    @Test
    void handleUserProfileUpdated_RethrowsSoTheMessageIsDeadLettered() {
        // Arrange
        UserProfileUpdatedEvent event = UserProfileUpdatedEvent.builder().keycloakId(KEYCLOAK_ID).build();
        doThrow(new RuntimeException("db down")).when(marketplaceMemberService).applyProfileUpdate(event);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> userEventListener.handleUserProfileUpdated(event));
    }
}
