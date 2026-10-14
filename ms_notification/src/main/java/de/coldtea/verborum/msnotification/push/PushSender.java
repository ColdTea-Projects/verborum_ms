package de.coldtea.verborum.msnotification.push;

import java.util.Collection;

/**
 * The delivery channel behind the service (P4N-04). One implementation talks to FCM; another does
 * nothing but log, so the service runs locally without a Firebase key. Never reached through a
 * transaction: a send is a network call and must not hold a database connection.
 */
public interface PushSender {

    /**
     * Sends one data message to up to {@link #MAX_TOKENS_PER_CALL} tokens. Never throws for a bad
     * token — those come back in the result; an exception means the whole call failed (e.g. no
     * credentials accepted) and is worth a retry.
     */
    PushResult send(PushMessage message, Collection<String> tokens);

    /** FCM's limit for one batch of messages. */
    int MAX_TOKENS_PER_CALL = 500;
}
