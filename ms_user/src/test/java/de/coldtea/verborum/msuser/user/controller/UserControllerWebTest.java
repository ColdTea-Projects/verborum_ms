package de.coldtea.verborum.msuser.user.controller;

import de.coldtea.verborum.msuser.common.config.SecurityConfig;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer smoke tests — see the note on ms_dictionary's DictionaryControllerWebTest. The one
 * ms_user-specific thing worth pinning here is that the value handed to the service is the JWT
 * subject (this user's keycloakId), never the path's userId.
 */
@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerWebTest {

    private static final String KEYCLOAK_ID = "78012064-231e-4a0d-abed-bad89a2350c1";
    private static final String USER_ID = "aa11bb22-cc33-dd44-ee55-ff6677889900";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtDecoder jwtDecoder;

    private static String body(String keycloakId) {
        return """
                {"userId":"%s","keycloakId":"%s","email":"a@b.co","displayName":"A"}
                """.formatted(USER_ID, keycloakId);
    }

    @Test
    void unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/users/" + USER_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedRead_PassesTheSubjectNotThePathId() throws Exception {
        when(userService.getUserById(USER_ID, KEYCLOAK_ID)).thenReturn(new UserResponseDTO());

        mockMvc.perform(get("/users/" + USER_ID).with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
                .andExpect(status().isOk());
        // the stub only matches when the caller argument is the subject, so a green run proves it
    }

    @Test
    void invalidEmail_Is400() throws Exception {
        mockMvc.perform(post("/users/")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","keycloakId":"%s","email":"not-an-email","displayName":"A"}
                                """.formatted(USER_ID, KEYCLOAK_ID)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void displayNameTooLong_Is400AndNeverReachesTheService() throws Exception {
        // P4-13: VARCHAR(255) — a longer name was a 500 from the database
        mockMvc.perform(post("/users/")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","keycloakId":"%s","email":"a@b.co","displayName":"%s"}
                                """.formatted(USER_ID, KEYCLOAK_ID, "a".repeat(256))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("displayName: displayName must be at most 255 characters"));

        verifyNoInteractions(userService);
    }

    @Test
    void nonUuidKeycloakId_Is400AndNeverReachesTheService() throws Exception {
        // P4-09: @ValidUUID was inert until 2026-09-27 and stored whatever it was sent
        mockMvc.perform(post("/users/")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("not-a-uuid")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorDetail").value("keycloakId: must be a valid UUID"));

        verifyNoInteractions(userService);
    }

    @Test
    void serviceForbidden_MapsTo403() throws Exception {
        when(userService.saveUser(any(), anyString())).thenThrow(new ForbiddenOperationException("nope"));

        mockMvc.perform(post("/users/")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        // a real UUID since P4-09 — a malformed id is now a 400 before the service runs
                        .content(body("0f1e2d3c-4b5a-4968-8776-655443322110")))
                .andExpect(status().isForbidden());
    }

    @Test
    void serviceRecordNotFound_MapsTo404() throws Exception {
        when(userService.getUserById(anyString(), anyString())).thenThrow(new RecordNotFoundException("nope"));

        mockMvc.perform(get("/users/" + USER_ID).with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void unhandledException_Is500_AndDoesNotLeakTheMessage() throws Exception {
        when(userService.getUserById(anyString(), anyString()))
                .thenThrow(new IllegalStateException("relation \"users\" violates constraint \"uq_secret\""));

        mockMvc.perform(get("/users/" + USER_ID).with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorDetail").value("Internal server error"));
    }
}
