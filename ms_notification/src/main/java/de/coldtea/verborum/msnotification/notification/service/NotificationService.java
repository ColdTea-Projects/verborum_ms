package de.coldtea.verborum.msnotification.notification.service;

/**
 * Turns the events this service consumes into silent pushes (P4N-07). Called by the listeners; there is
 * no HTTP entry point. Every recipient is a JWT subject taken from a trusted event, never from a client.
 */
public interface NotificationService {

    /**
     * A user's data changed: tell their <b>other</b> devices to sync. The writing device is skipped when
     * the event names it; without a device id every device gets the nudge. A missing owner is ignored.
     */
    void syncUserDevices(String keycloakId, String dictionaryId, String writingDeviceId);

    /**
     * Something happened to a publisher's dictionary (an import or a rating): tell them to refresh. Never
     * sent to the actor — a publisher is not told about their own action — and ignored when the event
     * names no publisher (an import event from before P4N-06).
     */
    void refreshPublisher(String publisherId, String actorKeycloakId, String dictionaryId);
}
