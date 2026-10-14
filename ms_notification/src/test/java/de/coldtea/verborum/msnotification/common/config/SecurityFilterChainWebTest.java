package de.coldtea.verborum.msnotification.common.config;

import de.coldtea.verborum.msnotification.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The scaffold has no controller, so this proves only what the shell promises (P4N-02): the real filter
 * chain refuses an anonymous caller, and an authenticated one reaches the handler (a plain 404, not 500).
 */
@WebMvcTest
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SecurityFilterChainWebTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void anyPath_WithoutToken_Is401() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/notifications/devices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownPath_WithToken_Is404NotServerError() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/notifications/devices").with(jwt()))
                .andExpect(status().isNotFound());
    }
}
