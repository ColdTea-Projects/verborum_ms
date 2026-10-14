package de.coldtea.verborum.msnotification.notification.service.impl;

import de.coldtea.verborum.msnotification.push.PushMessage;
import de.coldtea.verborum.msnotification.push.service.PushDispatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotificationServiceImplTest {

    private static final String OWNER = "kc-owner";
    private static final String DEVICE = "3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10";

    @Mock
    private PushDispatchService pushDispatchService;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void syncUserDevices_SendsASilentSyncAndSkipsTheWritingDevice() {
        // Act
        notificationService.syncUserDevices(OWNER, "dict-1", DEVICE);

        // Assert — SYNC, ids only, one collapse key per user; the writing device is the excluded one
        verify(pushDispatchService).sendToUser(OWNER,
                new PushMessage(PushMessage.TYPE_SYNC, "dict-1", NotificationServiceImpl.COLLAPSE_KEY_SYNC), DEVICE);
    }

    @Test
    void syncUserDevices_NoWritingDevice_NudgesEveryDevice() {
        // Act — e.g. the server flipped isPublic on a Forum join, or a client sent no X-Device-Id
        notificationService.syncUserDevices(OWNER, "dict-1", null);

        // Assert
        verify(pushDispatchService).sendToUser(OWNER,
                new PushMessage(PushMessage.TYPE_SYNC, "dict-1", NotificationServiceImpl.COLLAPSE_KEY_SYNC), null);
    }

    @Test
    void syncUserDevices_MissingOwner_IsIgnoredNotRetried() {
        // Act & Assert — throwing would retry and dead-letter a message that can never succeed
        notificationService.syncUserDevices(null, "dict-1", DEVICE);
        notificationService.syncUserDevices("  ", "dict-1", DEVICE);
        verifyNoInteractions(pushDispatchService);
    }

    @Test
    void refreshPublisher_SendsASilentRefreshToThePublisher() {
        // Act
        notificationService.refreshPublisher("kc-publisher", "kc-importer", "dict-1");

        // Assert — to the publisher, to every one of their devices (nothing to exclude)
        verify(pushDispatchService).sendToUser("kc-publisher",
                new PushMessage(PushMessage.TYPE_REFRESH, "dict-1", NotificationServiceImpl.COLLAPSE_KEY_REFRESH), null);
    }

    @Test
    void refreshPublisher_ActorIsThePublisher_SendsNothing() {
        // Act — a publisher is never told about their own action
        notificationService.refreshPublisher("kc-publisher", "kc-publisher", "dict-1");

        // Assert
        verify(pushDispatchService, never()).sendToUser(any(), any(), any());
    }

    @Test
    void refreshPublisher_EventWithoutPublisher_IsIgnoredNotRetried() {
        // Act — a dictionary.imported from before P4N-06 has no publisherId
        notificationService.refreshPublisher(null, "kc-importer", "dict-1");

        // Assert
        verifyNoInteractions(pushDispatchService);
    }
}
