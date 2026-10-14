package de.coldtea.verborum.msnotification.notification.service.impl;

import de.coldtea.verborum.msnotification.notification.service.NotificationService;
import de.coldtea.verborum.msnotification.push.PushMessage;
import de.coldtea.verborum.msnotification.push.service.PushDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    /** One pending sync per user: FCM and APNs keep only the latest message with the same key. */
    static final String COLLAPSE_KEY_SYNC = "sync";
    static final String COLLAPSE_KEY_REFRESH = "refresh";

    private final PushDispatchService pushDispatchService;

    @Override
    public void syncUserDevices(String keycloakId, String dictionaryId, String writingDeviceId) {
        if (isBlank(keycloakId)) {
            // Nothing to address. Throwing would retry and dead-letter a message that can never succeed
            log.warn("dictionary.changed without an owner was ignored (dictionaryId: {})", dictionaryId);
            return;
        }

        pushDispatchService.sendToUser(keycloakId,
                new PushMessage(PushMessage.TYPE_SYNC, dictionaryId, COLLAPSE_KEY_SYNC), writingDeviceId);
    }

    @Override
    public void refreshPublisher(String publisherId, String actorKeycloakId, String dictionaryId) {
        if (isBlank(publisherId)) {
            log.warn("Event without a publisher was ignored (dictionaryId: {})", dictionaryId);
            return;
        }
        // Self import and self rating are refused upstream (400), so this should not happen; the guard
        // keeps the rule true here too, whatever a publisher of the event does
        if (publisherId.equals(actorKeycloakId)) {
            return;
        }

        // The publisher is a Forum member by construction: ms_marketplace only publishes these events for a
        // listing whose publisher is one (anything else is a 404 there), so membership is not mirrored here
        pushDispatchService.sendToUser(publisherId,
                new PushMessage(PushMessage.TYPE_REFRESH, dictionaryId, COLLAPSE_KEY_REFRESH), null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
