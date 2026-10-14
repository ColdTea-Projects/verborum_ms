package de.coldtea.verborum.msnotification.common.listener;

import de.coldtea.verborum.msnotification.common.event.UserDeletedEvent;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static de.coldtea.verborum.msnotification.common.config.RabbitMQConfig.QUEUE_USER_DELETED;

/**
 * Consumes events published by ms_user.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final DeviceTokenService deviceTokenService;

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
}
