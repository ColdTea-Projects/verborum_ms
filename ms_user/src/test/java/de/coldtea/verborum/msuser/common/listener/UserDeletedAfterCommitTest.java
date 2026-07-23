package de.coldtea.verborum.msuser.common.listener;

import de.coldtea.verborum.msuser.common.event.UserDeletedEvent;
import de.coldtea.verborum.msuser.user.entity.User;
import de.coldtea.verborum.msuser.user.repository.UserRepository;
import de.coldtea.verborum.msuser.user.service.KeycloakUserService;
import de.coldtea.verborum.msuser.user.service.UserService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.EXCHANGE;
import static de.coldtea.verborum.msuser.common.config.RabbitMQConfig.ROUTING_KEY_USER_DELETED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The test that justifies the whole after-commit refactor: it drives a real transaction and asserts
 * that a rollback publishes <b>nothing</b>, and a commit publishes exactly once.
 * <p>
 * Under the previous pattern (send as the last statement inside the transaction) the rollback case
 * would have emitted `user.deleted` for a user who still exists — and ms_dictionary reacts to that
 * by deleting the user's dictionaries and words. This is the regression that must never come back.
 * <p>
 * Needs the compose stack: it is a `@SpringBootTest` against the real datasource.
 * `RabbitTemplate` and `KeycloakUserService` are mocked, so nothing leaves the process.
 */
@SpringBootTest
class UserDeletedAfterCommitTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @MockBean
    private KeycloakUserService keycloakUserService;

    private String userId;

    @AfterEach
    void cleanUp() {
        if (userId != null) {
            userRepository.deleteById(userId);
        }
    }

    private User givenAUser() {
        userId = UUID.randomUUID().toString();
        return userRepository.saveAndFlush(User.builder()
                .userId(userId)
                .keycloakId(UUID.randomUUID().toString())
                .email(userId + "@example.com")
                .displayName("After commit")
                .build());
    }

    @Test
    void commit_PublishesOnce() {
        // Arrange
        User user = givenAUser();

        // Act
        transactionTemplate.executeWithoutResult(status -> userService.deleteUser(userId, user.getKeycloakId()));

        // Assert
        verify(rabbitTemplate).convertAndSend(eq(EXCHANGE), eq(ROUTING_KEY_USER_DELETED), any(UserDeletedEvent.class));
        verify(keycloakUserService).deleteUser(user.getKeycloakId());
        userId = null; // already deleted
    }

    @Test
    void rollback_PublishesNothing() {
        // Arrange
        User user = givenAUser();

        // Act — roll back after the service has done its work and raised its events
        transactionTemplate.executeWithoutResult(status -> {
            userService.deleteUser(userId, user.getKeycloakId());
            status.setRollbackOnly();
        });

        // Assert — no phantom event, no destroyed identity, and the row is still there
        verifyNoInteractions(rabbitTemplate);
        verifyNoInteractions(keycloakUserService);
        org.junit.jupiter.api.Assertions.assertTrue(userRepository.findById(userId).isPresent());
    }
}
