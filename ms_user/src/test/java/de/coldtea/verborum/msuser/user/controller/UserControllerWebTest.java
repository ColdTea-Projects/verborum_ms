package de.coldtea.verborum.msuser.user.controller;

import de.coldtea.verborum.msuser.common.config.SecurityConfig;
import de.coldtea.verborum.msuser.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msuser.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msuser.common.exception.InvalidProfileException;
import de.coldtea.verborum.msuser.common.exception.ProfileConflictException;
import de.coldtea.verborum.msuser.common.exception.RecordNotFoundException;
import de.coldtea.verborum.msuser.user.dto.ProfileInfoRequestDTO;
import de.coldtea.verborum.msuser.user.dto.ProfileResponseDTO;
import de.coldtea.verborum.msuser.user.dto.UserResponseDTO;
import de.coldtea.verborum.msuser.user.service.UserService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
    // The e-mail the test body sends — a verified token must carry the same one (SEC-04)
    private static final String VERIFIED_EMAIL = "a@b.co";

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
        when(userService.saveUser(any(), anyString(), anyString())).thenThrow(new ForbiddenOperationException("nope"));

        mockMvc.perform(post("/users/")
                        .with(verifiedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        // a real UUID since P4-09 — a malformed id is now a 400 before the service runs
                        .content(body("0f1e2d3c-4b5a-4968-8776-655443322110")))
                .andExpect(status().isForbidden());
    }

    // ---- SEC-04: the profile e-mail comes from the token, verified ----

    @Test
    void createUser_PassesTheTokensVerifiedEmail() throws Exception {
        when(userService.saveUser(any(), eq(KEYCLOAK_ID), eq(VERIFIED_EMAIL))).thenReturn(new UserResponseDTO());

        mockMvc.perform(post("/users/")
                        .with(verifiedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEYCLOAK_ID)))
                .andExpect(status().isCreated());
        // the stub only matches with the token's e-mail, so a 201 proves it was passed through
    }

    @Test
    void createUser_UnverifiedEmail_Is403AndNeverReachesTheService() throws Exception {
        mockMvc.perform(post("/users/")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID).claim("email", VERIFIED_EMAIL).claim("email_verified", false)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEYCLOAK_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    @Test
    void updateUser_TokenWithoutEmail_Is403AndNeverReachesTheService() throws Exception {
        // A service-account token carries no e-mail — it is not a user who may own a profile
        mockMvc.perform(put("/users/")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEYCLOAK_ID)))
                .andExpect(status().isForbidden());

        verifyNoInteractions(userService);
    }

    /** A user token as Keycloak issues it after verification: subject plus a verified e-mail. */
    private static RequestPostProcessor verifiedUser() {
        return jwt().jwt(j -> j.subject(KEYCLOAK_ID).claim("email", VERIFIED_EMAIL).claim("email_verified", true));
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

    // ---- P4-14: GET /users/me, PUT /users/me/profile-info ----

    @Test
    void getMyProfile_Unauthenticated_Is401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    @Test
    void getMyProfile_UsesTheTokenSubjectAndReturnsTheProfileShape() throws Exception {
        // "me" must route here, not to GET /users/{userId} with userId = "me"
        when(userService.getMyProfile(KEYCLOAK_ID)).thenReturn(ProfileResponseDTO.builder()
                .id(USER_ID).email("a@b.co").displayName("Anna Bauer")
                .marketplaceAgreementAccepted(true).marketplaceAgreementVersion("v1").build());

        mockMvc.perform(get("/users/me").with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(USER_ID))
                .andExpect(jsonPath("$.email").value("a@b.co"))
                .andExpect(jsonPath("$.displayName").value("Anna Bauer"))
                .andExpect(jsonPath("$.marketplaceAgreementAccepted").value(true))
                .andExpect(jsonPath("$.marketplaceAgreementVersion").value("v1"));
    }

    @Test
    void getMyProfile_NoProfileYet_Is404() throws Exception {
        when(userService.getMyProfile(KEYCLOAK_ID)).thenThrow(new RecordNotFoundException("nope"));

        mockMvc.perform(get("/users/me").with(jwt().jwt(j -> j.subject(KEYCLOAK_ID))))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateProfileInfo_PassesBodyAndSubject_Is201() throws Exception {
        ProfileInfoRequestDTO expected = ProfileInfoRequestDTO.builder()
                .displayName("Anna Bauer").marketplaceAgreementAccepted(true).marketplaceAgreementVersion("v1").build();
        when(userService.updateProfileInfo(expected, KEYCLOAK_ID)).thenReturn(ProfileResponseDTO.builder().id(USER_ID).build());

        mockMvc.perform(put("/users/me/profile-info")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"displayName":"Anna Bauer","marketplaceAgreementAccepted":true,"marketplaceAgreementVersion":"v1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Updated successfully user " + USER_ID));
    }

    @Test
    void updateProfileInfo_BrokenProfileRule_Is400WithTheRule() throws Exception {
        when(userService.updateProfileInfo(any(), anyString()))
                .thenThrow(new InvalidProfileException("marketplaceAgreementVersion is required to accept the marketplace agreement"));

        mockMvc.perform(put("/users/me/profile-info")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"marketplaceAgreementAccepted":true}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidProfileException"))
                .andExpect(jsonPath("$.errorDetail").value("marketplaceAgreementVersion is required to accept the marketplace agreement"));
    }

    @Test
    void updateProfileInfo_VersionTooLong_Is400AndNeverReachesTheService() throws Exception {
        mockMvc.perform(put("/users/me/profile-info")
                        .with(jwt().jwt(j -> j.subject(KEYCLOAK_ID)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"marketplaceAgreementAccepted":true,"marketplaceAgreementVersion":"%s"}
                                """.formatted("v".repeat(51))))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(userService);
    }

    @Test
    void updateProfileInfo_Unauthenticated_Is401() throws Exception {
        mockMvc.perform(put("/users/me/profile-info")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(userService);
    }

    // ---- duplicate profile data → 409 ----

    @Test
    void createUser_DuplicateProfile_Is409WithTheReason() throws Exception {
        when(userService.saveUser(any(), anyString(), anyString()))
                .thenThrow(new ProfileConflictException("This account already has a profile; load it with GET /users/me instead of creating another"));

        mockMvc.perform(post("/users/")
                        .with(verifiedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEYCLOAK_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ProfileConflictException"));
    }

    @Test
    void createUser_ConstraintRace_Is409AndDoesNotLeakTheConstraint() throws Exception {
        // The backstop: a unique constraint fired after the service's own check passed
        when(userService.saveUser(any(), anyString(), anyString())).thenThrow(new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"users_keycloak_id_key\""));

        mockMvc.perform(post("/users/")
                        .with(verifiedUser())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(KEYCLOAK_ID)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorDetail").value("The request conflicts with existing data"));
    }
}
