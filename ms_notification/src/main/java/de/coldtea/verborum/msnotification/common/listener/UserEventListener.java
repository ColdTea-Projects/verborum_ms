package de.coldtea.verborum.msnotification.common.listener;

import de.coldtea.verborum.msnotification.common.event.UserDeletedEvent;
import de.coldtea.verborum.msnotification.common.event.VaultChangedEvent;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import de.coldtea.verborum.msnotification.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msnotification.common.config.RabbitMQConfig.QUEUE_USER_DELETED;
import static de.coldtea.verborum.msnotification.common.config.RabbitMQConfig.QUEUE_VAULT_CHANGED;

/**
 * Consumes events published by ms_user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DeviceTokenService deviceTokenService;

    private final NotificationService notificationService;

    /**
     * A deleted user's devices must stop receiving anything (P4N-07). <b>Keyed on `keycloakId`</b>: this
     * service's `fk_user_id` is the JWT subject, which equals ms_user's `keycloak_id`; the event's `userId`
     * matches nothing here and would delete zero rows while looking like success. Idempotent: a user
     * with no devices is a no-op.
     */
    @RabbitListener(queues = QUEUE_USER_DELETED)
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user.deleted event for keycloakId: {} (ms_user userId: {})",
                event.getKeycloakId(), event.getUserId());
        try {
            deviceTokenService.removeAllDevices(event.getKeycloakId());
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than acknowledged with
            // a deleted user's devices still addressable
            log.error("Failed to process user.deleted event for keycloakId: {}", event.getKeycloakId(), e);
            throw e;
        }
    }

    /**
     * The user's vault really changed — a Forum download or an unsubscribe (P4N-14). Their other
     * devices get the same silent `SYNC` as for a dictionary edit, because a sync mirrors the vault; the
     * install that did it is skipped. Keyed on `keycloakId`, like everything here.
     */
    @RabbitListener(queues = QUEUE_VAULT_CHANGED)
    public void handleVaultChanged(VaultChangedEvent event) {
        log.info("Received vault.changed event for keycloakId: {} (dictionaryId: {})",
                event.getKeycloakId(), event.getDictionaryId());
        try {
            notificationService.syncUserDevices(event.getKeycloakId(), event.getDictionaryId(), event.getDeviceId());
        } catch (Exception e) {
            // Re-thrown so the message is retried and finally dead-lettered rather than acknowledged
            log.error("Failed to process vault.changed event for keycloakId: {}", event.getKeycloakId(), e);
            throw e;
        }
    }
}
