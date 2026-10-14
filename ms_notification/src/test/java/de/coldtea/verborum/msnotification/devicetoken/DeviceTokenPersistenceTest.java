package de.coldtea.verborum.msnotification.devicetoken;

import de.coldtea.verborum.msnotification.devicetoken.dto.DeviceRequestDTO;
import de.coldtea.verborum.msnotification.devicetoken.entity.DevicePlatform;
import de.coldtea.verborum.msnotification.devicetoken.entity.DeviceToken;
import de.coldtea.verborum.msnotification.devicetoken.repository.DeviceTokenRepository;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The device writes against the real database (needs the local stack, like the context test): the
 * changeset, the UNIQUE token and the owner-scoped delete cannot be seen by a mocked repository.
 * Rolled back at the end.
 */
@SpringBootTest
@Transactional
class DeviceTokenPersistenceTest {

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceTokenRepository deviceTokenRepository;

    private static String id() {
        return UUID.randomUUID().toString();
    }

    private static DeviceRequestDTO request(String deviceId, String token) {
        return DeviceRequestDTO.builder().deviceId(deviceId).token(token).platform(DevicePlatform.ANDROID).build();
    }

    @Test
    void register_PersistsAndRefreshesInPlace() {
        // Arrange
        String owner = id();
        String deviceId = id();

        // Act
        deviceTokenService.registerDevice(request(deviceId, "token-a-" + deviceId), owner);
        deviceTokenService.registerDevice(request(deviceId, "token-b-" + deviceId), owner);

        // Assert — one row, the refreshed token, owned by the caller
        DeviceToken row = deviceTokenRepository.findById(deviceId).orElseThrow();
        assertEquals("token-b-" + deviceId, row.getToken());
        assertEquals(owner, row.getUserId());
        assertEquals(1, deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(owner).size());
    }

    @Test
    void uniqueToken_IsEnforcedByTheDatabase() {
        // Arrange
        String token = "shared-" + id();
        deviceTokenRepository.saveAndFlush(DeviceToken.builder().deviceId(id()).userId(id()).token(token)
                .platform(DevicePlatform.IOS).lastSeenAt(OffsetDateTime.now()).build());

        // Act & Assert — a second row with the same token cannot exist
        assertThrows(DataIntegrityViolationException.class, () ->
                deviceTokenRepository.saveAndFlush(DeviceToken.builder().deviceId(id()).userId(id()).token(token)
                        .platform(DevicePlatform.IOS).lastSeenAt(OffsetDateTime.now()).build()));
    }

    @Test
    void register_SameTokenUnderANewDeviceId_LeavesOneRow() {
        // Arrange
        String owner = id();
        String token = "moved-" + id();
        String oldId = id();
        String newId = id();
        deviceTokenService.registerDevice(request(oldId, token), owner);

        // Act
        deviceTokenService.registerDevice(request(newId, token), owner);

        // Assert
        assertFalse(deviceTokenRepository.findById(oldId).isPresent());
        assertTrue(deviceTokenRepository.findById(newId).isPresent());
    }

    @Test
    void remove_OnlyDeletesTheCallersOwnDevice() {
        // Arrange
        String owner = id();
        String stranger = id();
        String deviceId = id();
        deviceTokenService.registerDevice(request(deviceId, "token-" + deviceId), owner);

        // Act — a stranger names the id: nothing happens
        deviceTokenService.removeDevice(deviceId, stranger);
        assertTrue(deviceTokenRepository.findById(deviceId).isPresent());

        // the owner removes it; removing it again is a no-op
        deviceTokenService.removeDevice(deviceId, owner);
        deviceTokenService.removeDevice(deviceId, owner);
        assertFalse(deviceTokenRepository.findById(deviceId).isPresent());
    }
}
