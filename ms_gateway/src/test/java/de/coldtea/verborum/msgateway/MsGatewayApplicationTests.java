package de.coldtea.verborum.msgateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole gateway context, no network: proves the module boots with the gateway starter on the
 * classpath and that the secured shell behaves (P5-01 "done when").
 */
@SpringBootTest
@AutoConfigureMockMvc
class MsGatewayApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    /** The resource-server chain needs a decoder; the jwt() post-processor supplies the token itself. */
    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void health_IsOpen() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void anonymousCall_Is401() throws Exception {
        mockMvc.perform(get("/dictionaries/anything")).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPath_WithToken_Is404InTheEnvelope() throws Exception {
        mockMvc.perform(get("/nothing-here").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("NoResourceFoundException"))
                .andExpect(jsonPath("$.path").value("/nothing-here"));
    }

    @Test
    void oversizedBody_Is413() throws Exception {
        byte[] body = new byte[2097153];
        mockMvc.perform(post("/users/").with(jwt()).contentType("application/json").content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void actuatorEnv_IsNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isNotFound());
    }
}
