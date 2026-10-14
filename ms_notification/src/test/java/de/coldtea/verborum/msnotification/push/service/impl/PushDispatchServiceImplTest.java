package de.coldtea.verborum.msnotification.push.service.impl;

import de.coldtea.verborum.msnotification.common.exception.PushDeliveryException;
import de.coldtea.verborum.msnotification.devicetoken.entity.DevicePlatform;
import de.coldtea.verborum.msnotification.devicetoken.entity.DeviceToken;
import de.coldtea.verborum.msnotification.devicetoken.repository.DeviceTokenRepository;
import de.coldtea.verborum.msnotification.push.PushMessage;
import de.coldtea.verborum.msnotification.push.PushResult;
import de.coldtea.verborum.msnotification.push.PushSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushDispatchServiceImplTest {

    private static final String USER = "kc-user";
    private static final PushMessage SYNC = new PushMessage(PushMessage.TYPE_SYNC, "dict-1", "sync");

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private PushSender pushSender;

    @InjectMocks
    private PushDispatchServiceImpl pushDispatchService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    private static DeviceToken device(String deviceId, String token) {
        return DeviceToken.builder().deviceId(deviceId).userId(USER).token(token).platform(DevicePlatform.ANDROID).build();
    }

    @Test
    void sendToUser_SendsToEveryDevice() {
        // Arrange
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER))
                .thenReturn(List.of(device("d1", "t1"), device("d2", "t2")));
        when(pushSender.send(any(), anyCollection())).thenReturn(PushResult.none());

        // Act
        pushDispatchService.sendToUser(USER, SYNC, null);

        // Assert
        verify(pushSender).send(SYNC, List.of("t1", "t2"));
        verify(deviceTokenRepository, never()).deleteByTokenIn(any());
    }

    @Test
    void sendToUser_SkipsTheWritingDevice() {
        // Arrange — the originating device must not hear its own change
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER))
                .thenReturn(List.of(device("d1", "t1"), device("d2", "t2")));
        when(pushSender.send(any(), anyCollection())).thenReturn(PushResult.none());

        // Act
        pushDispatchService.sendToUser(USER, SYNC, "d1");

        // Assert
        verify(pushSender).send(SYNC, List.of("t2"));
    }

    @Test
    void sendToUser_OnlyDeviceIsTheWriter_SendsNothing() {
        // Arrange
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER)).thenReturn(List.of(device("d1", "t1")));

        // Act
        pushDispatchService.sendToUser(USER, SYNC, "d1");

        // Assert
        verifyNoInteractions(pushSender);
    }

    @Test
    void sendToUser_NoDevices_IsANoOp() {
        // Arrange
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER)).thenReturn(List.of());

        // Act
        pushDispatchService.sendToUser(USER, SYNC, null);

        // Assert
        verifyNoInteractions(pushSender);
    }

    @Test
    void sendToUser_DeletesTokensFcmReportedDead() {
        // Arrange
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER))
                .thenReturn(List.of(device("d1", "t1"), device("d2", "t2")));
        when(pushSender.send(any(), anyCollection())).thenReturn(new PushResult(List.of("t2"), 0));

        // Act
        pushDispatchService.sendToUser(USER, SYNC, null);

        // Assert
        verify(deviceTokenRepository).deleteByTokenIn(List.of("t2"));
    }

    @Test
    void sendToUser_TransientFailure_Throws_ButDeadTokensAreStillDeleted() {
        // Arrange — one token dead, one failed for a reason a retry can fix
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER))
                .thenReturn(List.of(device("d1", "t1"), device("d2", "t2")));
        when(pushSender.send(any(), anyCollection())).thenReturn(new PushResult(List.of("t1"), 1));

        // Act & Assert — thrown so the broker redelivers; the dead token goes first regardless
        assertThrows(PushDeliveryException.class, () -> pushDispatchService.sendToUser(USER, SYNC, null));
        verify(deviceTokenRepository).deleteByTokenIn(List.of("t1"));
    }

    @Test
    void sendToUser_SplitsIntoBatchesOfAtMost500() {
        // Arrange — 1,200 devices
        List<DeviceToken> devices = new ArrayList<>();
        for (int i = 0; i < 1200; i++) {
            devices.add(device("d" + i, "t" + i));
        }
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(USER)).thenReturn(devices);
        when(pushSender.send(any(), anyCollection())).thenReturn(PushResult.none());

        // Act
        pushDispatchService.sendToUser(USER, SYNC, null);

        // Assert
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> batch = ArgumentCaptor.forClass(Collection.class);
        verify(pushSender, times(3)).send(any(), batch.capture());
        assertEquals(List.of(500, 500, 200), batch.getAllValues().stream().map(Collection::size).toList());
    }
}
