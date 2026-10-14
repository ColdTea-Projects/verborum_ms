package de.coldtea.verborum.msnotification.common.exception;

/**
 * A push could not be handed to FCM for a reason a retry can fix (quota, outage, rejected credentials).
 * Thrown out of the event listener so the broker redelivers and, after the configured retries, dead-letters
 * the message instead of dropping it as handled. Never reaches a client: there is no endpoint on this path.
 */
public class PushDeliveryException extends RuntimeException {

    public PushDeliveryException(String message) {
        super(message);
    }
}
