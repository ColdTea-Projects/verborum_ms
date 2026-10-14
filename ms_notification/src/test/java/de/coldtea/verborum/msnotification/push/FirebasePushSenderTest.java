package de.coldtea.verborum.msnotification.push;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.SendResponse;
import de.coldtea.verborum.msnotification.common.exception.PushDeliveryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FirebasePushSenderTest {

    private static final PushMessage SYNC = new PushMessage(PushMessage.TYPE_SYNC, "dict-1", "sync");

    @Mock
    private FirebaseMessaging firebaseMessaging;

    private FirebasePushSender sender;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sender = new FirebasePushSender(firebaseMessaging, false);
    }

    private static SendResponse ok() {
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(true);
        return response;
    }

    private static SendResponse failed(MessagingErrorCode code) {
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(ex.getMessagingErrorCode()).thenReturn(code);
        SendResponse response = mock(SendResponse.class);
        when(response.isSuccessful()).thenReturn(false);
        when(response.getException()).thenReturn(ex);
        return response;
    }

    private void givenResponses(SendResponse... responses) throws FirebaseMessagingException {
        BatchResponse batch = mock(BatchResponse.class);
        when(batch.getResponses()).thenReturn(List.of(responses));
        when(firebaseMessaging.sendEach(anyList(), anyBoolean())).thenReturn(batch);
    }

    @Test
    void buildMessage_IsSilent_DataOnly_NormalPriority_Collapsing() throws IOException {
        // Act
        Message message = FirebasePushSender.buildMessage(SYNC, "token-1");
        String json = GsonFactory.getDefaultInstance().toString(message);

        // Assert — data only: the OS shows nothing
        assertFalse(json.contains("\"notification\""), json);
        assertTrue(json.contains("\"type\":\"SYNC\""), json);
        assertTrue(json.contains("\"dictionaryId\":\"dict-1\""), json);
        // Android: normal priority and a collapse key, so a burst leaves one pending message
        assertTrue(json.contains("\"priority\":\"normal\""), json);
        assertTrue(json.contains("\"ttl\":\"3600s\""), json);
        assertTrue(json.contains("\"collapse_key\":\"sync\""), json);
        // iOS: a background push, which Apple may throttle — never relied upon
        assertTrue(json.contains("\"apns-push-type\":\"background\""), json);
        assertTrue(json.contains("\"content-available\":1"), json);
    }

    @Test
    void buildMessage_WithoutDictionaryId_OmitsTheKey() throws IOException {
        // Act
        String json = GsonFactory.getDefaultInstance().toString(
                FirebasePushSender.buildMessage(new PushMessage(PushMessage.TYPE_REFRESH, null, "refresh"), "t"));

        // Assert
        assertFalse(json.contains("dictionaryId"), json);
    }

    @Test
    void send_AllDelivered_ReportsNothing() throws FirebaseMessagingException {
        // Arrange
        givenResponses(ok(), ok());

        // Act
        PushResult result = sender.send(SYNC, List.of("t1", "t2"));

        // Assert
        assertTrue(result.invalidTokens().isEmpty());
        assertEquals(0, result.transientFailures());
    }

    @Test
    void send_Unregistered_ReportsTheTokenAsInvalid() throws FirebaseMessagingException {
        // Arrange — the second token's app was uninstalled
        givenResponses(ok(), failed(MessagingErrorCode.UNREGISTERED), ok());

        // Act
        PushResult result = sender.send(SYNC, List.of("t1", "t2", "t3"));

        // Assert — matched by position to the right token
        assertEquals(List.of("t2"), result.invalidTokens());
        assertEquals(0, result.transientFailures());
    }

    @Test
    void send_QuotaOrOutage_IsTransient() throws FirebaseMessagingException {
        // Arrange
        givenResponses(failed(MessagingErrorCode.QUOTA_EXCEEDED), failed(MessagingErrorCode.UNAVAILABLE), ok());

        // Act
        PushResult result = sender.send(SYNC, List.of("t1", "t2", "t3"));

        // Assert
        assertTrue(result.invalidTokens().isEmpty());
        assertEquals(2, result.transientFailures());
    }

    @Test
    void send_InvalidArgument_IsNeitherDeletedNorRetried() throws FirebaseMessagingException {
        // Arrange — a payload problem looks the same as a bad token; deleting would be wrong, retrying useless
        givenResponses(failed(MessagingErrorCode.INVALID_ARGUMENT));

        // Act
        PushResult result = sender.send(SYNC, List.of("t1"));

        // Assert
        assertTrue(result.invalidTokens().isEmpty());
        assertEquals(0, result.transientFailures());
    }

    @Test
    void send_WholeCallFails_Throws() throws FirebaseMessagingException {
        // Arrange
        FirebaseMessagingException ex = mock(FirebaseMessagingException.class);
        when(firebaseMessaging.sendEach(anyList(), anyBoolean())).thenThrow(ex);

        // Act & Assert — not a verdict on any token: the caller retries the lot
        assertThrows(PushDeliveryException.class, () -> sender.send(SYNC, List.of("t1")));
    }

    @Test
    void send_NoTokens_DoesNotCallFcm() {
        // Act
        PushResult result = sender.send(SYNC, List.of());

        // Assert
        assertTrue(result.invalidTokens().isEmpty());
        verifyNoInteractions(firebaseMessaging);
    }

    @Test
    void send_MoreThan500Tokens_IsRejected() {
        // Arrange
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < PushSender.MAX_TOKENS_PER_CALL + 1; i++) {
            tokens.add("t" + i);
        }

        // Act & Assert — the dispatch service batches; the sender refuses an over-size call outright
        assertThrows(IllegalArgumentException.class, () -> sender.send(SYNC, tokens));
    }

    @Test
    void send_DryRun_IsPassedToFcm() throws FirebaseMessagingException {
        // Arrange
        FirebasePushSender dryRunSender = new FirebasePushSender(firebaseMessaging, true);
        givenResponses(ok());

        // Act
        dryRunSender.send(SYNC, List.of("t1"));

        // Assert
        ArgumentCaptor<Boolean> dryRun = ArgumentCaptor.forClass(Boolean.class);
        verify(firebaseMessaging).sendEach(anyList(), dryRun.capture());
        assertTrue(dryRun.getValue());
    }
}
