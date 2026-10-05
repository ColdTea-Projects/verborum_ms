package de.coldtea.verborum.msuser.common.utils;

import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.EMAIL_NOT_VERIFIED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NOT_THE_OWNER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NO_AUTHENTICATED_USER;

/**
 * The caller's identity, taken from the validated JWT (roadmap P3-05).
 * <p>
 * Careful: the subject is this user's <b>keycloakId</b>, not ms_user's own `userId`. Everywhere else
 * in the system the subject IS the user id, but ms_user keeps its own primary key — so an ownership
 * check here compares against `User.keycloakId`, never the path's `userId`. See
 * {@code UserServiceImpl.requireOwnProfile}.
 */
public class SecurityUtils {

    private SecurityUtils() {
    }

    /**
     * @return the JWT subject — this user's keycloakId, NOT ms_user's userId
     */
    public static String getCurrentKeycloakId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken().getSubject();
        }
        // Not reachable through the filter chain; this catches a misconfigured SecurityConfig
        throw new IllegalStateException(NO_AUTHENTICATED_USER);
    }

    /**
     * Rejects a request whose token does not belong to the profile being acted on.
     * <p>
     * 403 rather than silent substitution: a client sending the wrong id has a bug, and rewriting it
     * quietly would let that bug ship looking healthy.
     */
    public static void requireSelf(String claimedKeycloakId) {
        if (!getCurrentKeycloakId().equals(claimedKeycloakId)) {
            throw new ForbiddenOperationException(NOT_THE_OWNER);
        }
    }

    /**
     * The caller's e-mail as Keycloak vouches for it (SEC-04): the token's `email` claim, and only when
     * `email_verified` is true. A profile's e-mail is unique, so taking it from the request body let any
     * user claim — and block — someone else's address before its owner signed up.
     * <p>
     * 403 when the token carries no e-mail or an unverified one: a service-account token has none, and
     * the realm requires verification before a user token is issued, so either means "not a user who
     * may own a profile". Malformed claims degrade to that 403, never a 500.
     */
    public static String getVerifiedEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(NO_AUTHENTICATED_USER);
        }
        Jwt jwt = jwtAuth.getToken();
        Object email = jwt.getClaims().get("email");
        Object verified = jwt.getClaims().get("email_verified");
        boolean isVerified = Boolean.TRUE.equals(verified) || "true".equals(verified);
        if (!(email instanceof String address) || address.isBlank() || !isVerified) {
            throw new ForbiddenOperationException(EMAIL_NOT_VERIFIED);
        }
        return address.trim();
    }
}
