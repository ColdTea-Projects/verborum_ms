package de.coldtea.verborum.msnotification.devicetoken.service.impl;

import de.coldtea.verborum.msnotification.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msnotification.devicetoken.dto.DeviceRequestDTO;
import de.coldtea.verborum.msnotification.devicetoken.entity.DevicePlatform;
import de.coldtea.verborum.msnotification.devicetoken.entity.DeviceToken;
import de.coldtea.verborum.msnotification.devicetoken.repository.DeviceTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static de.coldtea.verborum.msnotification.common.constants.DTOMessageConstants.MAX_DEVICES_PER_USER;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceTokenServiceImplTest {

    private static final String OWNER = "kc-owner";
    private static final String OTHER = "kc-other";
    private static final String DEVICE_ID = "3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10";
    private static final String TOKEN = "fcm-token-1";

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @InjectMocks
    private DeviceTokenServiceImpl deviceTokenService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(OWNER)).thenReturn(List.of());
    }

    private static DeviceRequestDTO request(String deviceId, String token) {
        return DeviceRequestDTO.builder().deviceId(deviceId).token(token).platform(DevicePlatform.ANDROID)
                .locale("de-DE").appVersion("1.0.0").build();
    }

    private static DeviceToken stored(String deviceId, String userId, String token) {
        return DeviceToken.builder().deviceId(deviceId).userId(userId).token(token)
                .platform(DevicePlatform.ANDROID).lastSeenAt(OffsetDateTime.now().minusDays(3)).build();
    }

    @Test
    void registerDevice_NewDevice_IsCreatedForTheCaller() {
        // Arrange
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        // Act
        deviceTokenService.registerDevice(request(DEVICE_ID, TOKEN), OWNER);

        // Assert
        ArgumentCaptor<DeviceToken> saved = ArgumentCaptor.forClass(DeviceToken.class);
        verify(deviceTokenRepository).saveAndFlush(saved.capture());
        assertEquals(DEVICE_ID, saved.getValue().getDeviceId());
        assertEquals(OWNER, saved.getValue().getUserId());
        assertEquals(TOKEN, saved.getValue().getToken());
        assertEquals(DevicePlatform.ANDROID, saved.getValue().getPlatform());
        assertEquals("de-DE", saved.getValue().getLocale());
        assertNotNull(saved.getValue().getLastSeenAt());
    }

    @Test
    void registerDevice_OwnDeviceAgain_RefreshesTokenAndLastSeen() {
        // Arrange — the token rotated since the last launch
        DeviceToken existing = stored(DEVICE_ID, OWNER, "old-token");
        OffsetDateTime before = existing.getLastSeenAt();
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());

        // Act
        deviceTokenService.registerDevice(request(DEVICE_ID, TOKEN), OWNER);

        // Assert — the same row is updated in place, not duplicated
        verify(deviceTokenRepository).saveAndFlush(existing);
        assertEquals(TOKEN, existing.getToken());
        assertEquals(true, existing.getLastSeenAt().isAfter(before));
        verify(deviceTokenRepository, never()).delete(any());
    }

    @Test
    void registerDevice_AnotherUsersDeviceId_WithADifferentToken_IsForbidden() {
        // Arrange — the takeover case: the id is known, the token is not
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.of(stored(DEVICE_ID, OTHER, "victim-token")));

        // Act & Assert
        assertThrows(ForbiddenOperationException.class,
                () -> deviceTokenService.registerDevice(request(DEVICE_ID, "attacker-token"), OWNER));
        verify(deviceTokenRepository, never()).saveAndFlush(any());
        verify(deviceTokenRepository, never()).delete(any());
    }

    @Test
    void registerDevice_AnotherUsersDeviceId_WithTheSameToken_MovesToTheCaller() {
        // Arrange — a second account signs in on the same phone; holding the token proves it is the device
        DeviceToken existing = stored(DEVICE_ID, OTHER, TOKEN);
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.of(existing));
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(existing));

        // Act
        deviceTokenService.registerDevice(request(DEVICE_ID, TOKEN), OWNER);

        // Assert
        verify(deviceTokenRepository).saveAndFlush(existing);
        assertEquals(OWNER, existing.getUserId());
        verify(deviceTokenRepository, never()).delete(any());
    }

    @Test
    void registerDevice_TokenStoredUnderAnotherDeviceId_RemovesTheOldRow() {
        // Arrange — the same install came back under a new device id
        DeviceToken old = stored("11111111-1111-4111-8111-111111111111", OWNER, TOKEN);
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.of(old));

        // Act
        deviceTokenService.registerDevice(request(DEVICE_ID, TOKEN), OWNER);

        // Assert — removed and flushed before the insert, so the UNIQUE token holds
        verify(deviceTokenRepository).delete(old);
        verify(deviceTokenRepository).flush();
        verify(deviceTokenRepository).saveAndFlush(any(DeviceToken.class));
    }

    @Test
    void registerDevice_BeyondTheQuota_EvictsTheLeastRecentlySeen() {
        // Arrange — the caller already holds MAX devices; the new one makes MAX + 1
        List<DeviceToken> devices = new ArrayList<>();
        for (int i = 0; i <= MAX_DEVICES_PER_USER; i++) {
            devices.add(stored("device-" + i, OWNER, "token-" + i));
        }
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(OWNER)).thenReturn(devices);

        // Act
        deviceTokenService.registerDevice(request(DEVICE_ID, TOKEN), OWNER);

        // Assert — exactly the one surplus device, from the front of the list, goes
        verify(deviceTokenRepository).deleteAll(List.of(devices.get(0)));
    }

    @Test
    void registerDevice_AtTheQuota_EvictsNothing() {
        // Arrange
        List<DeviceToken> devices = new ArrayList<>();
        for (int i = 0; i < MAX_DEVICES_PER_USER; i++) {
            devices.add(stored("device-" + i, OWNER, "token-" + i));
        }
        when(deviceTokenRepository.findById(DEVICE_ID)).thenReturn(Optional.empty());
        when(deviceTokenRepository.findByToken(TOKEN)).thenReturn(Optional.empty());
        when(deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(OWNER)).thenReturn(devices);

        // Act
        deviceTokenService.registerDevice(request(DEVICE_ID, TOKEN), OWNER);

        // Assert
        verify(deviceTokenRepository, never()).deleteAll(any());
    }

    @Test
    void removeDevice_IsScopedToTheCaller() {
        // Act
        deviceTokenService.removeDevice(DEVICE_ID, OWNER);

        // Assert — the owner is in the delete itself, so another user's id matches nothing
        verify(deviceTokenRepository).deleteByDeviceIdAndUserId(DEVICE_ID, OWNER);
    }
}
