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
    private static final String LAST_SENT_NOTE = "verborum-email-code-last-sent";
    private static final String RESENDS_NOTE = "verborum-email-code-resends";

    private static final int CODE_LENGTH = 6;
    private static final long TTL_SECONDS = 300;   // 5 minutes
    private static final int MAX_ATTEMPTS = 3;
    // Abuse protection on "Send a new code": a per-request cooldown and a per-session cap so the
    // button cannot be used to flood a user's inbox.
    private static final long RESEND_COOLDOWN_SECONDS = 30;
    private static final int MAX_RESENDS = 3;
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

        if (!issueCode(context, user, 0)) {
            context.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("emailCodeSendFailed").createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }

        context.challenge(context.form().createForm(FORM));
    }

    /** Generates a fresh code, resets the per-code notes, records the send time & resend count, and mails it. */
    private boolean issueCode(AuthenticationFlowContext context, UserModel user, int resendCount) {
        String code = generateCode();
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        authSession.setAuthNote(CODE_NOTE, code);
        authSession.setAuthNote(EXPIRY_NOTE, Long.toString(now() + TTL_SECONDS * 1000L));
        authSession.setAuthNote(ATTEMPTS_NOTE, "0");
        authSession.setAuthNote(LAST_SENT_NOTE, Long.toString(now()));
        authSession.setAuthNote(RESENDS_NOTE, Integer.toString(resendCount));
        return sendCode(context, user, code);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();

        if (formData.containsKey("resend")) {
            handleResend(context);
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

    /** "Send a new code" — throttled by a cooldown and capped per session so it cannot flood mail. */
    private void handleResend(AuthenticationFlowContext context) {
        AuthenticationSessionModel authSession = context.getAuthenticationSession();
        long lastSent = parseLong(authSession.getAuthNote(LAST_SENT_NOTE));
        int resends = parseInt(authSession.getAuthNote(RESENDS_NOTE));

        long elapsedSeconds = (now() - lastSent) / 1000L;
        if (lastSent > 0 && elapsedSeconds < RESEND_COOLDOWN_SECONDS) {
            long wait = RESEND_COOLDOWN_SECONDS - elapsedSeconds;
            context.challenge(context.form().setError("emailCodeResendWait", wait).createForm(FORM));
            return;
        }
        if (resends >= MAX_RESENDS) {
            context.challenge(context.form().setError("emailCodeResendLimit").createForm(FORM));
            return;
        }

        UserModel user = context.getUser();
        if (user == null || !issueCode(context, user, resends + 1)) {
            context.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    context.form().setError("emailCodeSendFailed").createErrorPage(Response.Status.INTERNAL_SERVER_ERROR));
            return;
        }
        context.challenge(context.form().setSuccess("emailCodeResent").createForm(FORM));
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
        authSession.removeAuthNote(LAST_SENT_NOTE);
        authSession.removeAuthNote(RESENDS_NOTE);
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
