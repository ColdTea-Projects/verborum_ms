package de.coldtea.verborum.msnotification.devicetoken.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;

/**
 * One registered app install that can receive a push (P4N-03). The FCM token is the delivery address
 * and the only thing that proves the caller holds the device, so it is never returned by the API and
 * excluded from toString.
 */
@Getter
@Setter
@ToString(exclude = "token")
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "device_tokens")
public class DeviceToken {

    // Client-generated UUID of the app install; persists across token refreshes
    @Id
    @Column(name = "device_id", updatable = false, nullable = false)
    private String deviceId;

    // The owner's JWT subject (ms_user's keycloak_id) — cross-service ref, no database FK
    @Column(name = "fk_user_id", nullable = false)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false)
    private DevicePlatform platform;

    // UNIQUE: a token addresses exactly one device
    @Column(name = "token", nullable = false)
    private String token;

    @Column(name = "locale")
    private String locale;

    @Column(name = "app_version")
    private String appVersion;

    // Set by the service on every register call; drives stale-token cleanup (P4N-11)
    @Column(name = "last_seen_at", nullable = false)
    private OffsetDateTime lastSeenAt;

    @CreationTimestamp
    @Column(name = "creation_dt", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_dt")
    private OffsetDateTime updatedAt;
}
