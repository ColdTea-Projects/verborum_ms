package de.coldtea.verborum.msgateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A service that is not running: 502 in the error envelope, with no host name, port or stack trace.
 * Port 1 on the loopback interface refuses every connection.
 */
@SpringBootTest(properties = {"USER_URL=http://127.0.0.1:1"})
@AutoConfigureMockMvc
class UpstreamDownTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void unreachableUpstream_Is502InTheEnvelope() throws Exception {
        mockMvc.perform(get("/users/me").with(jwt()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.errorDetail").value("The service is not reachable right now"))
                .andExpect(jsonPath("$.errorDetail", not(containsString("127.0.0.1"))));
    }
}
