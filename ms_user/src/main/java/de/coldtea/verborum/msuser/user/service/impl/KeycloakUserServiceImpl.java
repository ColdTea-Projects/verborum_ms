package de.coldtea.verborum.msuser.user.service.impl;

import de.coldtea.verborum.msuser.user.service.KeycloakUserService;
import jakarta.ws.rs.core.Response;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.KEYCLOAK_ADMIN_NOT_CONFIGURED;
import static de.coldtea.verborum.msuser.common.constants.ErrorMessageConstants.KEYCLOAK_USER_DELETE_FAILED;

@Service
@Slf4j
public class KeycloakUserServiceImpl implements KeycloakUserService {

    private final String authServerUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakUserServiceImpl(
            @Value("${keycloak.auth-server-url}") String authServerUrl,
            @Value("${keycloak.realm}") String realm,
            @Value("${keycloak.admin.client-id}") String clientId,
            @Value("${keycloak.admin.client-secret}") String clientSecret) {
        this.authServerUrl = authServerUrl;
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public void deleteUser(String keycloakId) {
        // No secret means no admin access. A developer running locally without
        // KEYCLOAK_ADMIN_CLIENT_SECRET should still be able to delete a profile, so this warns
        // loudly and moves on instead of failing the request — the identity simply outlives the
        // profile, which is the pre-P3-04 behaviour.
        if (!StringUtils.hasText(clientSecret)) {
            log.warn(KEYCLOAK_ADMIN_NOT_CONFIGURED, keycloakId);
            return;
        }

        // Built per call rather than held as a bean: account deletion is rare, and a long-lived
        // admin connection is a credential kept warm for no benefit.
        try (Keycloak keycloak = KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm(realm)
                .grantType("client_credentials")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .build();
             Response response = keycloak.realm(realm).users().delete(keycloakId)) {

            // 404 is not an error worth shouting about: the identity is already gone, which is the
            // state we wanted. Anything else means the deletion did not happen.
            if (response.getStatus() >= 300 && response.getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
                log.error(KEYCLOAK_USER_DELETE_FAILED, keycloakId, response.getStatus());
            }
        } catch (Exception e) {
            // Swallowed on purpose — see the contract on KeycloakUserService. The profile and its
            // data are already deleted; the ERROR line is the record that an identity was left behind.
            log.error(KEYCLOAK_USER_DELETE_FAILED, keycloakId, -1, e);
        }
    }
}
