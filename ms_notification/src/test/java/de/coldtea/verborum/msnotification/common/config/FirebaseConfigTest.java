package de.coldtea.verborum.msnotification.common.config;

import de.coldtea.verborum.msnotification.push.NoOpPushSender;
import de.coldtea.verborum.msnotification.push.PushSender;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FirebaseConfigTest {

    private final FirebaseConfig config = new FirebaseConfig();

    @Test
    void pushSender_NoCredentialsFile_IsTheNoOpSender() throws IOException {
        // Act
        PushSender sender = config.pushSender("", "", false);

        // Assert — the service starts without a key
        assertInstanceOf(NoOpPushSender.class, sender);
    }

    @Test
    void pushSender_BlankCredentialsFile_IsTheNoOpSender() throws IOException {
        // Act & Assert
        assertInstanceOf(NoOpPushSender.class, config.pushSender("   ", "verborum-dev", false));
    }

    @Test
    void pushSender_UnreadableCredentialsFile_FailsLoudly() {
        // Act & Assert — a half-configured push channel must not start silently
        assertThrows(IOException.class, () -> config.pushSender("/nonexistent/firebase-key.json", "verborum-dev", false));
    }
}
