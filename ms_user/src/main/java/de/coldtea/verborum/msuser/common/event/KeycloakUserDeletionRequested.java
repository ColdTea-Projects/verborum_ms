package de.coldtea.verborum.msuser.common.event;

/**
 * Raised when a profile is deleted, so the Keycloak identity is removed **after** the transaction
 * commits (rule 7 in docs/agent/rabbitmq.md).
 * <p>
 * Deleting the identity inside the transaction had the same phantom problem as publishing inside it,
 * and worse consequences: a Keycloak account cannot be recreated with the same subject, so a
 * rollback after the call would leave a profile whose owner can never log in again.
 * <p>
 * Internal to this service.
 */
public record KeycloakUserDeletionRequested(String keycloakId) {
}
