package de.coldtea.verborum.msmarketplace.common.utils;

import de.coldtea.verborum.msmarketplace.common.exception.ForbiddenOperationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msmarketplace.common.constants.ErrorMessageConstants.NO_AUTHENTICATED_USER;

/**
 * The caller's identity, taken from the validated JWT.
 * <p>
 * The token subject is what this service stores as `fk_user_id` — the same value ms_dictionary
 * stores, and ms_user's `keycloak_id` (not its `user_id`). Never take an owner from the request body.
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    public static String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject(); // Keycloak subject = the userId every service stores
        }
        // Not reachable through the filter chain (everything but actuator/Swagger requires a JWT);
        // this catches a misconfigured SecurityConfig rather than a real request
        throw new IllegalStateException(NO_AUTHENTICATED_USER);
    }

    /**
     * Rejects a request that names a user other than the caller.
     * <p>
     * Deliberately a 403 rather than silently substituting the caller's id: a client that sends the
     * wrong id has a bug, and quietly rewriting it would let that bug ship while looking healthy.
     */
    public static void requireSelf(String claimedUserId) {
        String currentUserId = getCurrentUserId();
        if (!currentUserId.equals(claimedUserId)) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }
    }
}
