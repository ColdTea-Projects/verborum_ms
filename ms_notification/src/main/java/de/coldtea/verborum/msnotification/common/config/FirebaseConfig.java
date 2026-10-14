package de.coldtea.verborum.msnotification.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import de.coldtea.verborum.msnotification.push.FirebasePushSender;
import de.coldtea.verborum.msnotification.push.NoOpPushSender;
import de.coldtea.verborum.msnotification.push.PushSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * The FCM wiring (P4N-04). The service-account key is a credential: it is read from a path outside
 * the repo (`FIREBASE_CREDENTIALS_FILE`) and never logged. No path means a logging no-op sender, so the
 * service starts and every other feature works locally without a key. A path that is set but unreadable
 * or invalid fails the start-up on purpose — a half-configured push channel must not go unnoticed.
 */
@Slf4j
@Configuration
public class FirebaseConfig {

    @Bean
    public PushSender pushSender(@Value("${firebase.credentials-file:}") String credentialsFile,
                                 @Value("${firebase.project-id:}") String projectId,
                                 @Value("${firebase.dry-run:false}") boolean dryRun) throws IOException {
        if (credentialsFile.isBlank()) {
            log.warn("FIREBASE_CREDENTIALS_FILE is not set: pushes are logged and not delivered");
            return new NoOpPushSender();
        }

        FirebaseApp app;
        if (FirebaseApp.getApps().isEmpty()) {
            try (InputStream key = new FileInputStream(credentialsFile)) {
                FirebaseOptions.Builder options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(key));
                if (!projectId.isBlank()) {
                    options.setProjectId(projectId);
                }
                app = FirebaseApp.initializeApp(options.build());
            }
        } else {
            app = FirebaseApp.getInstance();
        }

        log.info("FCM sender ready{}", dryRun ? " (dry run: requests are validated, nothing is delivered)" : "");
        return new FirebasePushSender(FirebaseMessaging.getInstance(app), dryRun);
    }
}
