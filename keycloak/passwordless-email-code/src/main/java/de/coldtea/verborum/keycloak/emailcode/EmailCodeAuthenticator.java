package de.coldtea.verborum.keycloak.emailcode;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;

/**
 * Passwordless sign-in with a one-time code emailed to the user.
 *
 * Placed as an ALTERNATIVE next to the password form in the browser flow, so the user chooses
 * password OR code. It runs only for a known user whose email is verified — otherwise it bows out
 * with {@link AuthenticationFlowContext#attempted()} so the password path stays usable and an
 * unverified account is never offered the code option (the product rule: code only after verify).
 */
public class EmailCodeAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(EmailCodeAuthenticator.class);

    private static final String CODE_NOTE = "verborum-email-code";
    private static final String EXPIRY_NOTE = "verborum-email-code-expiry";
    private static final String ATTEMPTS_NOTE = "verborum-email-code-attempts";

    private static final int CODE_LENGTH = 6;
    private static final long TTL_SECONDS = 300;   // 5 minutes
    private static final int MAX_ATTEMPTS = 3;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final String FORM = "login-email-code.ftl";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        UserModel user = context.getUser();
        if (user == null || isBlank(user.getEmail()) || !user.isEmailVerified()) {
            // Not eligible for the code path — let the alternative (password) handle it.
            context.attempted();
            return;
        }

        String code = generateCode();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        authSession.setAuthNote(CODE_NOTE, code);
        authSession.setAuthNote(EXPIRY_NOTE, Long.toString(now() + TTL_SECONDS * 1000L));
        authSession.setAuthNote(ATTEMPTS_NOTE, "0");

        if (!sendCode(context, user, code)) {
            context.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("emailCodeSendFailed").createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }

        context.challenge(context.form().createForm(FORM));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData.containsKey("resend")) {
            authenticate(context);
            return;
        }

        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        String expected = authSession.getAuthNote(CODE_NOTE);
        String expiry = authSession.getAuthNote(EXPIRY_NOTE);

        if (expected == null || expiry == null || now() > parseLong(expiry)) {
            context.challenge(context.form().setError("emailCodeExpired").createForm(FORM));
            return;
        }

        int attempts = parseInt(authSession.getAuthNote(ATTEMPTS_NOTE)) + 1;
        authSession.setAuthNote(ATTEMPTS_NOTE, Integer.toString(attempts));

        String input = formData.getFirst("code");
        if (input != null && expected.equals(input.trim())) {
            clearNotes(authSession);
            context.success();
            return;
        }

        if (attempts >= MAX_ATTEMPTS) {
            clearNotes(authSession);
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    context.form().setError("emailCodeTooManyAttempts").createErrorPage(Response.Status.UNAUTHORIZED));
            return;
        }

        context.challenge(context.form().setError("emailCodeInvalid").createForm(FORM));
    }

    private boolean sendCode(AuthenticationFlowContext context, UserModel user, String code) {
        try {
            KeycloakSession session = context.getSession();
            RealmModel realm = context.getRealm();
            Map<String, Object> attributes = new HashMap<>();
            attributes.put("code", code);
            attributes.put("ttlMinutes", TTL_SECONDS / 60);
            session.getProvider(EmailTemplateProvider.class)
                    .setRealm(realm)
                    .setUser(user)
                    .send("emailCodeSubject", "email-code.ftl", attributes);
            return true;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to send Verborum email code to user %s", user.getId());
            return false;
        }
    }

    private static void clearNotes(AuthenticationSessionModel authSession) {
        authSession.removeAuthNote(CODE_NOTE);
        authSession.removeAuthNote(EXPIRY_NOTE);
        authSession.removeAuthNote(ATTEMPTS_NOTE);
    }

    private static String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private static long parseLong(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int parseInt(String s) {
        try {
            return s == null ? 0 : Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public boolean requiresUser() {
        return true;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return user.getEmail() != null && !user.getEmail().isBlank();
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {
        // nothing to set up
    }

    @Override
    public void close() {
        // nothing to release
    }
}
