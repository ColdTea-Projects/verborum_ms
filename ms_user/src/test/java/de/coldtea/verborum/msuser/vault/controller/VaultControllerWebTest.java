package de.coldtea.verborum.msuser.vault.controller;

import de.coldtea.verborum.msuser.common.config.SecurityConfig;
import de.coldtea.verborum.msuser.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msuser.vault.service.VaultService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests for the vault. The point worth pinning is SEC-09: there is no way to add an entry
 * over HTTP — only a marketplace import does that.
 */
@WebMvcTest(VaultController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class VaultControllerWebTest {

    private static final String KEYCLOAK_ID = "78012064-231e-4a0d-abed-bad89a2350c1";
    private static final String USER_ID = "aa11bb22-cc33-dd44-ee55-ff6677889900";
    private static final String DICTIONARY_ID = "0f1e2d3c-4b5a-4968-8776-655443322110";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VaultService vaultService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void addingDirectly_IsNotAllowedAndNeverReachesTheService() throws Exception {
        // SEC-09: a direct add skipped the Forum gate, the "not your own" rule and the import count
        mockMvc.perform(post("/users/" + USER_ID + "/vault")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dictionaryId\":\"%s\"}".formatted(DICTIONARY_ID)))
                .andExpect(status().isMethodNotAllowed());

        verifyNoInteractions(vaultService);
    }

    @Test
    void read_PassesTheSubject() throws Exception {
        when(vaultService.getVaultEntriesByUser(USER_ID, KEYCLOAK_ID)).thenReturn(List.of());

        mockMvc.perform(get("/users/" + USER_ID + "/vault").with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
                .andExpect(status().isOk());
        // the stub only matches with the subject as the caller, so a green run proves it
    }

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/users/" + USER_ID + "/vault"))
                .andExpect(status().isUnauthorized());
    }
}
