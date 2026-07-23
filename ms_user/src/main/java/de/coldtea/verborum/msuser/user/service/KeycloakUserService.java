package de.coldtea.verborum.msuser.user.service;

/**
 * Writes to Keycloak. ms_user is the only service that does — everyone else merely validates
 * tokens.
 * <p>
 * Deliberately narrow: it deletes identities, it does not create them. Sign-up goes through
 * Keycloak's hosted registration page (see docs/integration/frontend-backend-integration.md §6.1a),
 * so nothing needs an Admin API create path. This interface exists because deleting a profile while
 * leaving the identity alive lets the account log back in and re-register.
 */
public interface KeycloakUserService {

    /**
     * Deletes the Keycloak account for the given subject.
     * <p>
     * Best-effort and non-throwing by contract: the profile row and its cascade are already gone by
     * the time this runs, so failing the caller would suggest nothing happened. A failure is logged
     * at ERROR with the id, which is the signal that a manual cleanup is owed.
     */
    void deleteUser(String keycloakId);
}
