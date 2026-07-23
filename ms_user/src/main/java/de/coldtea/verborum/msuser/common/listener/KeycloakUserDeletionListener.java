package de.coldtea.verborum.msuser.common.listener;

import de.coldtea.verborum.msuser.common.event.KeycloakUserDeletionRequested;
import de.coldtea.verborum.msuser.user.service.KeycloakUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Removes the Keycloak identity once the profile deletion has actually committed (rule 7 in
 * docs/agent/rabbitmq.md).
 * <p>
 * `KeycloakUserService.deleteUser` is non-throwing by contract, so nothing here needs a try/catch —
 * a failure is already logged at ERROR with the id, which is the record that a manual cleanup is
 * owed.
 */
@Component
@RequiredArgsConstructor
public class KeycloakUserDeletionListener {

    private final KeycloakUserService keycloakUserService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handle(KeycloakUserDeletionRequested event) {
        keycloakUserService.deleteUser(event.keycloakId());
    }
}
