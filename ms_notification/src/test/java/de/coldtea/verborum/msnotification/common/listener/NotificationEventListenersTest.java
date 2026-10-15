package de.coldtea.verborum.msnotification.common.listener;

import de.coldtea.verborum.msnotification.common.event.DictionaryChangedEvent;
import de.coldtea.verborum.msnotification.common.event.DictionaryImportedEvent;
import de.coldtea.verborum.msnotification.common.event.DictionaryRatedEvent;
import de.coldtea.verborum.msnotification.common.event.UserDeletedEvent;
import de.coldtea.verborum.msnotification.common.event.VaultChangedEvent;
import de.coldtea.verborum.msnotification.common.exception.PushDeliveryException;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import de.coldtea.verborum.msnotification.notification.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/** The three listeners are thin: delegate on the right id, re-throw so retry and the DLQ take over. */
class NotificationEventListenersTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private DeviceTokenService deviceTokenService;

    private DictionaryEventListener dictionaryEventListener;
    private MarketplaceEventListener marketplaceEventListener;
    private UserEventListener userEventListener;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dictionaryEventListener = new DictionaryEventListener(notificationService);
        marketplaceEventListener = new MarketplaceEventListener(notificationService);
        userEventListener = new UserEventListener(deviceTokenService, notificationService);
    }

    @Test
    void handleDictionaryChanged_DelegatesWithTheOwnerAndTheWritingDevice() {
        // Arrange
        DictionaryChangedEvent event = DictionaryChangedEvent.builder().keycloakId("kc-owner")
                .dictionaryId("dict-1").deviceId("dev-1").eventTimestamp(OffsetDateTime.now()).build();

        // Act
        dictionaryEventListener.handleDictionaryChanged(event);

        // Assert
        verify(notificationService).syncUserDevices("kc-owner", "dict-1", "dev-1");
    }

    @Test
    void handleDictionaryChanged_Failure_IsRethrownForRetry() {
        // Arrange — a transient FCM failure surfaces as PushDeliveryException
        doThrow(new PushDeliveryException("fcm down")).when(notificationService).syncUserDevices("kc-owner", "dict-1", null);

        // Act & Assert
        assertThrows(PushDeliveryException.class, () -> dictionaryEventListener.handleDictionaryChanged(
                DictionaryChangedEvent.builder().keycloakId("kc-owner").dictionaryId("dict-1").build()));
    }

    @Test
    void handleDictionaryImported_TellsThePublisherNotTheImporter() {
        // Arrange
        DictionaryImportedEvent event = DictionaryImportedEvent.builder().dictionaryId("dict-1")
                .keycloakId("kc-importer").publisherId("kc-publisher").build();

        // Act
        marketplaceEventListener.handleDictionaryImported(event);

        // Assert — recipient first, actor second
        verify(notificationService).refreshPublisher("kc-publisher", "kc-importer", "dict-1");
    }

    @Test
    void handleDictionaryRated_TellsThePublisherNotTheRater() {
        // Arrange
        DictionaryRatedEvent event = DictionaryRatedEvent.builder().dictionaryId("dict-1")
                .publisherId("kc-publisher").raterKeycloakId("kc-rater").stars(4).build();

        // Act
        marketplaceEventListener.handleDictionaryRated(event);

        // Assert
        verify(notificationService).refreshPublisher("kc-publisher", "kc-rater", "dict-1");
    }

    @Test
    void handleDictionaryImported_Failure_IsRethrownForRetry() {
        // Arrange
        doThrow(new PushDeliveryException("fcm down")).when(notificationService).refreshPublisher("kc-publisher", "kc-importer", "dict-1");

        // Act & Assert
        assertThrows(PushDeliveryException.class, () -> marketplaceEventListener.handleDictionaryImported(
                DictionaryImportedEvent.builder().dictionaryId("dict-1").keycloakId("kc-importer").publisherId("kc-publisher").build()));
    }

    @Test
    void handleUserDeleted_RemovesDevicesOnKeycloakId() {
        // Arrange
        UserDeletedEvent event = UserDeletedEvent.builder().userId("u-1").keycloakId("kc-1").build();

        // Act
        userEventListener.handleUserDeleted(event);

        // Assert — fk_user_id holds the JWT subject; ms_user's userId would delete nothing and look fine
        verify(deviceTokenService).removeAllDevices("kc-1");
        verify(deviceTokenService, never()).removeAllDevices("u-1");
    }

    @Test
    void handleVaultChanged_NudgesTheOwnersOtherDevices() {
        // Arrange
        VaultChangedEvent event = VaultChangedEvent.builder().keycloakId("kc-owner")
                .dictionaryId("dict-1").deviceId("dev-1").build();

        // Act
        userEventListener.handleVaultChanged(event);

        // Assert — a Forum download or unsubscribe is a SYNC like any edit; the installing device is skipped
        verify(notificationService).syncUserDevices("kc-owner", "dict-1", "dev-1");
    }

    @Test
    void handleVaultChanged_Failure_IsRethrownForRetry() {
        // Arrange
        doThrow(new PushDeliveryException("fcm down")).when(notificationService).syncUserDevices("kc-owner", "dict-1", null);

        // Act & Assert
        assertThrows(PushDeliveryException.class, () -> userEventListener.handleVaultChanged(
                VaultChangedEvent.builder().keycloakId("kc-owner").dictionaryId("dict-1").build()));
    }

    @Test
    void handleUserDeleted_Failure_IsRethrownForRetry() {
        // Arrange
        doThrow(new IllegalStateException("db down")).when(deviceTokenService).removeAllDevices("kc-1");

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> userEventListener.handleUserDeleted(
                UserDeletedEvent.builder().userId("u-1").keycloakId("kc-1").build()));
    }
}
