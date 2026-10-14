package de.coldtea.verborum.msnotification.devicetoken.repository;

import de.coldtea.verborum.msnotification.devicetoken.entity.DeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, String> {

    // Backed by uq_device_tokens_token
    Optional<DeviceToken> findByToken(String token);

    // A user's devices, least recently seen first — the per-user quota evicts from the front.
    // Backed by idx_device_tokens_user
    List<DeviceToken> findByUserIdOrderByLastSeenAtAsc(String userId);

    // The logout call: scoped to the owner, so another user's deviceId matches nothing
    void deleteByDeviceIdAndUserId(String deviceId, String userId);

    // The user.deleted cascade (P4N-07), matched on the JWT subject
    void deleteByUserId(String userId);

    // Tokens FCM reported as no longer valid. Its own transaction on purpose: the push service holds
    // none while it talks to FCM, and a derived delete needs one
    @Transactional
    void deleteByTokenIn(Collection<String> tokens);
}
