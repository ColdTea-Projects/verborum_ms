package de.coldtea.verborum.msnotification.devicetoken.service.impl;

import de.coldtea.verborum.msnotification.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msnotification.devicetoken.dto.DeviceRequestDTO;
import de.coldtea.verborum.msnotification.devicetoken.entity.DeviceToken;
import de.coldtea.verborum.msnotification.devicetoken.repository.DeviceTokenRepository;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

import static de.coldtea.verborum.msnotification.common.constants.DTOMessageConstants.MAX_DEVICES_PER_USER;
import static de.coldtea.verborum.msnotification.common.constants.ErrorMessageConstants.NOT_THE_OWNER;

@Service
@RequiredArgsConstructor
public class DeviceTokenServiceImpl implements DeviceTokenService {

    private final DeviceTokenRepository deviceTokenRepository;

    @Transactional
    @Override
    public void registerDevice(DeviceRequestDTO request, String ownerId) {
        String deviceId = request.getDeviceId();
        String token = request.getToken();

        DeviceToken device = deviceTokenRepository.findById(deviceId).orElse(null);

        // A client chooses the device id, so an upsert must check who owns the stored row, not only the
        // request. Someone else's id with a token they do not hold is a takeover attempt; the same token
        // proves the same physical device (a second account on one phone), which may move to the caller
        if (device != null && !ownerId.equals(device.getUserId()) && !token.equals(device.getToken())) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }

        // The same physical device re-registered under a new device id (reinstall that kept the token,
        // or an id rotated at logout): the old row goes, so the UNIQUE token constraint holds
        deviceTokenRepository.findByToken(token)
                .filter(other -> !other.getDeviceId().equals(deviceId))
                .ifPresent(other -> {
                    deviceTokenRepository.delete(other);
                    deviceTokenRepository.flush();
                });

        if (device == null) {
            device = DeviceToken.builder().deviceId(deviceId).build();
        }
        device.setUserId(ownerId);
        device.setToken(token);
        device.setPlatform(request.getPlatform());
        device.setLocale(request.getLocale());
        device.setAppVersion(request.getAppVersion());
        device.setLastSeenAt(OffsetDateTime.now());
        deviceTokenRepository.saveAndFlush(device);

        evictSurplusDevices(ownerId);
    }

    @Transactional
    @Override
    public void removeDevice(String deviceId, String ownerId) {
        // Scoped to the owner in the query itself: another user's id is a silent no-op (200), matching
        // the other delete endpoints, and confirms nothing about whether the id exists
        deviceTokenRepository.deleteByDeviceIdAndUserId(deviceId, ownerId);
    }

    @Transactional
    @Override
    public void removeAllDevices(String keycloakId) {
        deviceTokenRepository.deleteByUserId(keycloakId);
    }

    /** A per-user quota (SEC-07 lesson): abandoned installs must not pile up. The freshest are kept. */
    private void evictSurplusDevices(String ownerId) {
        List<DeviceToken> devices = deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(ownerId);
        int surplus = devices.size() - MAX_DEVICES_PER_USER;
        if (surplus > 0) {
            deviceTokenRepository.deleteAll(devices.subList(0, surplus));
        }
    }
}
