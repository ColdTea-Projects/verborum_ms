package de.coldtea.verborum.msnotification.push;

import de.coldtea.verborum.msnotification.common.config.FirebaseConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Opt-in smoke test against the real FCM (P4N-04): runs only when FIREBASE_CREDENTIALS_FILE is set, and
 * always as a <b>dry run</b>, so nothing is delivered to anyone. It proves the key, the project and the
 * Admin SDK wiring are accepted by Google — a rejected credential would throw. A made-up token comes back
 * as an FCM verdict for that one token (not a delivery), which is exactly what a healthy channel answers.
 * <p>
 * Run: {@code FIREBASE_CREDENTIALS_FILE=~/.config/verborum/<key>.json FIREBASE_PROJECT_ID=verborum-dev
 * ./mvnw -pl ms_notification test -Dtest=FirebasePushSenderLiveTest}
 */
@EnabledIfEnvironmentVariable(named = "FIREBASE_CREDENTIALS_FILE", matches = ".+")
class FirebasePushSenderLiveTest {

    @Test
    void dryRun_IsAcceptedByFcm() throws IOException {
        // Arrange — the same wiring the service uses, forced to dry run
        PushSender sender = new FirebaseConfig().pushSender(
                System.getenv("FIREBASE_CREDENTIALS_FILE"),
                System.getenv().getOrDefault("FIREBASE_PROJECT_ID", ""),
                true);
        assertInstanceOf(FirebasePushSender.class, sender);

        // Act — no exception means the credentials and project were accepted
        PushResult result = sender.send(new PushMessage(PushMessage.TYPE_SYNC, "dictionary-smoke-test", "sync"),
                List.of("not-a-real-fcm-token"));

        // Assert — a verdict on the token, never a retryable failure of the channel itself
        assertEquals(0, result.transientFailures());
    }
}
