package de.coldtea.verborum.msnotification.push;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

/** Used when no Firebase credentials are configured: logs what would have been sent and delivers nothing. */
@Slf4j
public class NoOpPushSender implements PushSender {

    @Override
    public PushResult send(PushMessage message, Collection<String> tokens) {
        log.info("Push not sent (no Firebase credentials configured): type={} dictionaryId={} devices={}",
                message.type(), message.dictionaryId(), tokens.size());
        return PushResult.none();
    }
}
