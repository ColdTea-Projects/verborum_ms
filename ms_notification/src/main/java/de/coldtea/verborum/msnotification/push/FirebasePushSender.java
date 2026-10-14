package de.coldtea.verborum.msnotification.push;

import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import de.coldtea.verborum.msnotification.common.exception.PushDeliveryException;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static de.coldtea.verborum.msnotification.common.constants.ErrorMessageConstants.PUSH_SEND_FAILED;

/**
 * Sends silent data messages through the Firebase Admin SDK (FCM HTTP v1).
 * <p>
 * Every message is data-only: no `notification` block, normal priority, so Android delivers it to the
 * app in the background and shows nothing. For iOS the same message goes out as a background push
 * (`content-available`, push type `background`, priority 5) — which Apple throttles and may drop, so
 * the apps never depend on one. A short TTL keeps a stale "sync now" from arriving hours later.
 */
@Slf4j
public class FirebasePushSender implements PushSender {

    /** A sync hint is worthless after an hour; the app syncs on open anyway. */
    static final long TTL_MILLIS = 60 * 60 * 1000L;
    static final String APNS_TTL_HEADER = "apns-expiration";

    /** Errors that mean this token will never work again: delete it. */
    private static final Set<MessagingErrorCode> INVALID_TOKEN_CODES =
            Set.of(MessagingErrorCode.UNREGISTERED, MessagingErrorCode.SENDER_ID_MISMATCH);

    /** Errors a retry can fix: quota, outage, a transient auth hiccup. */
    private static final Set<MessagingErrorCode> TRANSIENT_CODES =
            Set.of(MessagingErrorCode.QUOTA_EXCEEDED, MessagingErrorCode.UNAVAILABLE,
                    MessagingErrorCode.INTERNAL, MessagingErrorCode.THIRD_PARTY_AUTH_ERROR);

    private final FirebaseMessaging firebaseMessaging;

    /** `true` validates every request with FCM without delivering anything (local development). */
    private final boolean dryRun;

    public FirebasePushSender(FirebaseMessaging firebaseMessaging, boolean dryRun) {
        this.firebaseMessaging = firebaseMessaging;
        this.dryRun = dryRun;
    }

    @Override
    public PushResult send(PushMessage message, Collection<String> tokens) {
        if (tokens.isEmpty()) {
            return PushResult.none();
        }
        if (tokens.size() > MAX_TOKENS_PER_CALL) {
            throw new IllegalArgumentException("At most " + MAX_TOKENS_PER_CALL + " tokens per call");
        }

        List<String> ordered = new ArrayList<>(tokens);
        List<Message> messages = ordered.stream().map(token -> buildMessage(message, token)).toList();

        BatchResponse response;
        try {
            response = firebaseMessaging.sendEach(messages, dryRun);
        } catch (FirebaseMessagingException ex) {
            // The whole call failed (credentials, network): not a verdict on any token — retry it all
            log.error("{} (code {})", PUSH_SEND_FAILED, ex.getMessagingErrorCode(), ex);
            throw new PushDeliveryException(PUSH_SEND_FAILED);
        }

        return classify(ordered, response);
    }

    /** Package-private so the message shape can be asserted without a network. */
    static Message buildMessage(PushMessage message, String token) {
        Message.Builder builder = Message.builder()
                .setToken(token)
                .putData("type", message.type())
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(AndroidConfig.Priority.NORMAL)
                        .setTtl(TTL_MILLIS)
                        .setCollapseKey(message.collapseKey())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .putHeader("apns-push-type", "background")
                        .putHeader("apns-priority", "5")
                        .putHeader("apns-collapse-id", message.collapseKey())
                        .setAps(Aps.builder().setContentAvailable(true).build())
                        .build());
        if (message.dictionaryId() != null) {
            builder.putData("dictionaryId", message.dictionaryId());
        }
        return builder.build();
    }

    private PushResult classify(List<String> tokens, BatchResponse response) {
        List<String> invalid = new ArrayList<>();
        int transientFailures = 0;

        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (sendResponse.isSuccessful()) {
                continue;
            }
            FirebaseMessagingException ex = sendResponse.getException();
            MessagingErrorCode code = ex == null ? null : ex.getMessagingErrorCode();
            if (code != null && INVALID_TOKEN_CODES.contains(code)) {
                invalid.add(tokens.get(i));
            } else if (code != null && TRANSIENT_CODES.contains(code)) {
                transientFailures++;
            } else {
                // INVALID_ARGUMENT and the rest: a retry would fail the same way and the token may be
                // fine, so neither delete nor retry. The token itself is never logged
                log.warn("FCM refused one message: code={}", code);
            }
        }
        return new PushResult(invalid, transientFailures);
    }
}
