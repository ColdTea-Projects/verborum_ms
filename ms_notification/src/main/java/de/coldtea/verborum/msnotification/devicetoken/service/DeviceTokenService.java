package de.coldtea.verborum.msnotification.devicetoken.service;

import de.coldtea.verborum.msnotification.devicetoken.dto.DeviceRequestDTO;

/**
 * Device registration (P4N-03). Every method takes the caller explicitly — the JWT subject, never an
 * id from the request — so the class stays testable without a security context.
 */
public interface DeviceTokenService {

    /**
     * Registers or refreshes the caller's app install. Idempotent: the same device again only moves
     * `lastSeenAt` (and a refreshed token). An existing device id that belongs to someone else is
     * refused (403) unless the request carries that device's current token — possession of the token
     * is what proves it is the same physical install, e.g. a second account on the same phone. A token
     * already stored under a different device id is moved to this one. A user keeps at most
     * {@code MAX_DEVICES_PER_USER}; the least recently seen are evicted.
     */
    void registerDevice(DeviceRequestDTO request, String ownerId);

    /** Removes the caller's device (logout). A no-op when it is absent or belongs to someone else. */
    void removeDevice(String deviceId, String ownerId);

    /**
     * Removes every device of a user — the `user.deleted` cascade (P4N-07). `keycloakId` is the JWT
     * subject stored in `fk_user_id`; event-driven, so there is no caller to check. Idempotent.
     */
    void removeAllDevices(String keycloakId);
}
