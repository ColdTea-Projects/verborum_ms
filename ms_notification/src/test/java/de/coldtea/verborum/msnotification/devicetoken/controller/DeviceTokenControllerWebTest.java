package de.coldtea.verborum.msnotification.devicetoken.controller;

import de.coldtea.verborum.msnotification.common.config.SecurityConfig;
import de.coldtea.verborum.msnotification.common.exception.ForbiddenOperationException;
import de.coldtea.verborum.msnotification.common.exception.GlobalExceptionHandler;
import de.coldtea.verborum.msnotification.devicetoken.dto.DeviceRequestDTO;
import de.coldtea.verborum.msnotification.devicetoken.entity.DevicePlatform;
import de.coldtea.verborum.msnotification.devicetoken.service.DeviceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceTokenController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class DeviceTokenControllerWebTest {

    private static final String SUB = "b87fb499-2002-47a7-b88f-8ae517932802";
    private static final String DEVICE_ID = "3f2c1b8e-6a54-4f0e-9d2a-1b7c5e8a9f10";
    private static final String BODY = """
            {"deviceId":"%s","token":"fcm-token-1","platform":"ANDROID","locale":"de-DE","appVersion":"1.0.0"}
            """.formatted(DEVICE_ID);

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DeviceTokenService deviceTokenService;

    /** The filter chain needs a decoder bean; the jwt() post-processor supplies the token itself. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void put_WithoutToken_Is401() throws Exception {
        mockMvc.perform(put("/notifications/devices").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        verify(deviceTokenService, never()).registerDevice(any(), any());
    }

    @Test
    void delete_WithoutToken_Is401() throws Exception {
        mockMvc.perform(delete("/notifications/devices/" + DEVICE_ID))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void put_Registers_AndTheOwnerIsTheTokenSubject() throws Exception {
        mockMvc.perform(put("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(201))
                .andExpect(jsonPath("$.message").value("Registered successfully device " + DEVICE_ID));

        // the owner is the subject, never anything in the body
        verify(deviceTokenService).registerDevice(DeviceRequestDTO.builder().deviceId(DEVICE_ID).token("fcm-token-1")
                .platform(DevicePlatform.ANDROID).locale("de-DE").appVersion("1.0.0").build(), SUB);
    }

    @Test
    void put_BlankToken_Is400() throws Exception {
        mockMvc.perform(put("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + DEVICE_ID + "\",\"token\":\" \",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest());
        verify(deviceTokenService, never()).registerDevice(any(), any());
    }

    @Test
    void put_DeviceIdNotAUuid_Is400() throws Exception {
        mockMvc.perform(put("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"not-a-uuid\",\"token\":\"t\",\"platform\":\"ANDROID\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_UnknownPlatform_Is400() throws Exception {
        // web is not a push platform (no FCM there)
        mockMvc.perform(put("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + DEVICE_ID + "\",\"token\":\"t\",\"platform\":\"WEB\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_MissingPlatform_Is400() throws Exception {
        mockMvc.perform(put("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"deviceId\":\"" + DEVICE_ID + "\",\"token\":\"t\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_AnotherUsersDevice_Is403() throws Exception {
        doThrow(new ForbiddenOperationException("This resource does not belong to the authenticated user"))
                .when(deviceTokenService).registerDevice(any(), any());

        mockMvc.perform(put("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB)))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_RemovesTheCallersDevice_Is200() throws Exception {
        mockMvc.perform(delete("/notifications/devices/" + DEVICE_ID).with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Removed device " + DEVICE_ID));

        verify(deviceTokenService).removeDevice(DEVICE_ID, SUB);
    }

    @Test
    void delete_DeviceIdNotAUuid_Is400() throws Exception {
        mockMvc.perform(delete("/notifications/devices/not-a-uuid").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownPath_WithToken_Is404NotServerError() throws Exception {
        // the scaffold's own check (P4N-02), kept now that the shell has a controller
        mockMvc.perform(get("/notifications/nothing-here").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_IsNotSupported_Is405() throws Exception {
        // there is deliberately no read endpoint: a token is a delivery address, not data to hand back
        mockMvc.perform(get("/notifications/devices").with(jwt().jwt(j -> j.subject(SUB))))
                .andExpect(status().isMethodNotAllowed());
    }
}
