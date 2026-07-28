package de.coldtea.verborum.keycloak.emailcode;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel.Requirement;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

import java.util.List;

/**
 * Registers the Verborum email-code authenticator with Keycloak. Passwordless sign-in: emails a
 * one-time code and validates it, offered as an ALTERNATIVE to the password so a user may pick
 * either. Gated on a verified email inside the authenticator itself.
 */
public class EmailCodeAuthenticatorFactory implements AuthenticatorFactory {

    public static final String PROVIDER_ID = "verborum-email-code";

    // Stateless authenticator — one shared instance is the Keycloak convention.
    private static final EmailCodeAuthenticator SINGLETON = new EmailCodeAuthenticator();

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Verborum Email Code";
    }

    @Override
    public String getReferenceCategory() {
        return "email-code";
    }

    @Override
    public boolean isConfigurable() {
        return false;
    }

    @Override
    public Requirement[] getRequirementChoices() {
        return new Requirement[]{ Requirement.REQUIRED, Requirement.ALTERNATIVE, Requirement.DISABLED };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public String getHelpText() {
        return "Emails a one-time code to the user and validates it. Passwordless sign-in; "
                + "only offered to users with a verified email address.";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return List.of();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
    }

    @Override
    public void init(Config.Scope config) {
        // no configuration
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
        // nothing to warm up
    }

    @Override
    public void close() {
        // nothing to release
    }
}
