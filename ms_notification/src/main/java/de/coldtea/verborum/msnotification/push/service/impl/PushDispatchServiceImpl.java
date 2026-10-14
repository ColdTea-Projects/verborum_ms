package de.coldtea.verborum.msnotification.push.service.impl;

import de.coldtea.verborum.msnotification.common.exception.PushDeliveryException;
import de.coldtea.verborum.msnotification.devicetoken.entity.DeviceToken;
import de.coldtea.verborum.msnotification.devicetoken.repository.DeviceTokenRepository;
import de.coldtea.verborum.msnotification.push.PushMessage;
import de.coldtea.verborum.msnotification.push.PushResult;
import de.coldtea.verborum.msnotification.push.PushSender;
import de.coldtea.verborum.msnotification.push.service.PushDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static de.coldtea.verborum.msnotification.common.constants.ErrorMessageConstants.PUSH_SEND_FAILED;

/**
 * Deliberately not `@Transactional`: the send is a network call, and holding a database connection
 * across it would tie the pool to FCM's latency. The one write (deleting dead tokens) is its own
 * short transaction on the repository.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PushDispatchServiceImpl implements PushDispatchService {

    private final DeviceTokenRepository deviceTokenRepository;

    private final PushSender pushSender;

    @Override
    public void sendToUser(String keycloakId, PushMessage message, String excludeDeviceId) {
        List<String> tokens = deviceTokenRepository.findByUserIdOrderByLastSeenAtAsc(keycloakId).stream()
                .filter(device -> !device.getDeviceId().equals(excludeDeviceId))
                .map(DeviceToken::getToken)
                .toList();
        if (tokens.isEmpty()) {
            return;
        }

        List<String> invalidTokens = new ArrayList<>();
        int transientFailures = 0;
        for (int from = 0; from < tokens.size(); from += PushSender.MAX_TOKENS_PER_CALL) {
            List<String> chunk = tokens.subList(from, Math.min(from + PushSender.MAX_TOKENS_PER_CALL, tokens.size()));
            PushResult result = pushSender.send(message, chunk);
            invalidTokens.addAll(result.invalidTokens());
            transientFailures += result.transientFailures();
        }

        // Dead tokens go first and even when something else failed: they will never work, and a
        // redelivery should not try them again
        if (!invalidTokens.isEmpty()) {
            deviceTokenRepository.deleteByTokenIn(invalidTokens);
            log.info("Removed {} dead device token(s)", invalidTokens.size());
        }

        if (transientFailures > 0) {
            // Thrown so the listener's retry and the dead-letter queue work as designed. A redelivery
            // re-sends to the devices that already got it; the collapse key makes that harmless
            log.warn("{}: {} device(s) failed transiently", PUSH_SEND_FAILED, transientFailures);
            throw new PushDeliveryException(PUSH_SEND_FAILED);
        }
    }
}
