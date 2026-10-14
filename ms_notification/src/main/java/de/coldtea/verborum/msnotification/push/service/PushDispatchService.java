package de.coldtea.verborum.msnotification.push.service;

import de.coldtea.verborum.msnotification.push.PushMessage;

/**
 * Turns "tell this user" into pushes to their devices (P4N-04). Called by the event listeners
 * (P4N-07); there is no HTTP entry point. The recipient is a JWT subject, never an id from a request.
 */
public interface PushDispatchService {

    /**
     * Sends a silent message to every device of a user, except the one that caused it. A user with no
     * devices is a no-op. Tokens FCM reports as dead are deleted. A failure a retry can fix throws
     * {@code PushDeliveryException} <b>after</b> the dead tokens are gone, so the broker redelivers.
     *
     * @param keycloakId      the recipient's JWT subject
     * @param excludeDeviceId the writing device, which must not hear its own change; may be null
     */
    void sendToUser(String keycloakId, PushMessage message, String excludeDeviceId);
}
