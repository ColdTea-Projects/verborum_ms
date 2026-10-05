package de.coldtea.verborum.msuser.common.utils;

import de.coldtea.verborum.msuser.common.exception.ReauthenticationRequiredException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.NO_AUTHENTICATED_USER;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.REAUTHENTICATION_REQUIRED;

/**
 * SEC-13: an irreversible action — deleting the account, which also deletes the Keycloak identity and
 * every dictionary — must not be possible with any valid access token, only with one from a login in
 * the last `verborum.account-deletion.max-login-age-seconds`. A stolen token or an unlocked phone would
 * otherwise be enough to erase the account.
 * <p>
 * The login time is the token's `auth_time`, which a refresh carries over unchanged — refreshing never
 * makes a login recent. Tokens from the app's browser login always carry it (Keycloak 25+ `basic` scope).
 * Only a direct password grant omits it (the local-only `verborum-dev-cli`, which must not exist in a
 * shared realm); there each token is itself a fresh password login, so `iat` stands in.
 */
@Component
public class RecentLoginGuard {

    private final Duration maxLoginAge;

    public RecentLoginGuard(@Value("${verborum.account-deletion.max-login-age-seconds}") long maxLoginAgeSeconds) {
        this.maxLoginAge = Duration.ofSeconds(maxLoginAgeSeconds);
    }

    /** 403 ReauthenticationRequiredException unless the caller logged in within the allowed age. */
    public void requireRecentLogin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            throw new IllegalStateException(NO_AUTHENTICATED_USER);
        }
        Instant loginTime = loginTime(jwtAuth.getToken());
        if (loginTime == null || loginTime.isBefore(Instant.now().minus(maxLoginAge))) {
            throw new ReauthenticationRequiredException(REAUTHENTICATION_REQUIRED);
        }
    }

    private static Instant loginTime(Jwt jwt) {
        Object authTime = jwt.getClaims().get("auth_time");
        if (authTime instanceof Number seconds) {
            return Instant.ofEpochSecond(seconds.longValue());
        }
        if (authTime instanceof Instant instant) {
            return instant;
        }
        return jwt.getIssuedAt();
    }
}
