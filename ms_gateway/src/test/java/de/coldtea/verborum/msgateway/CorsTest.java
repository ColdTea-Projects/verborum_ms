package de.coldtea.verborum.msgateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5-06: the gateway answers CORS itself. The upstream stub counts what reaches it — a preflight must never.
 */
@SpringBootTest(properties = "verborum.cors.allowed-origins=https://app.verborum.test, http://localhost:8280")
@AutoConfigureMockMvc
class CorsTest {

    private static final String ALLOWED = "https://app.verborum.test";
    private static final String OTHER = "https://evil.test";

    private static HttpServer upstream;
    private static final List<String> received = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        upstream.start();
        String url = "http://127.0.0.1:" + upstream.getAddress().getPort();
        registry.add("USER_URL", () -> url);
        registry.add("DICTIONARY_URL", () -> url);
    }

    @AfterAll
    static void stop() {
        upstream.stop(0);
    }

    @BeforeEach
    void clear() {
        received.clear();
    }

    @Test
    void preflight_FromAllowedOrigin_IsAnsweredWithoutAToken_AndNeverForwarded() throws Exception {
        mockMvc.perform(options("/dictionaries/")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "PUT")
                        .header("Access-Control-Request-Headers", "authorization,content-type,x-device-id"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS"))
                .andExpect(header().exists("Access-Control-Allow-Headers"))
                .andExpect(header().string("Access-Control-Max-Age", "3600"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));

        assertTrue(received.isEmpty(), "the preflight reached a service: " + received);
    }

    @Test
    void preflight_AllowsEveryHeaderTheClientsSend() throws Exception {
        String headers = mockMvc.perform(options("/users/me")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type, X-Device-Id"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Access-Control-Allow-Headers");

        assertTrue(headers.contains("X-Device-Id"), headers);
        assertTrue(headers.contains("Authorization"), headers);
        assertTrue(headers.contains("Content-Type"), headers);
    }

    @Test
    void preflight_FromUnknownOrigin_IsRefused() throws Exception {
        mockMvc.perform(options("/dictionaries/")
                        .header("Origin", OTHER)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        assertTrue(received.isEmpty());
    }

    @Test
    void preflight_ForAMethodTheApiDoesNotUse_IsRefused() throws Exception {
        mockMvc.perform(options("/dictionaries/")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "PATCH"))
                .andExpect(status().isForbidden());
    }

    @Test
    void preflight_WithAnUnlistedHeader_IsRefused() throws Exception {
        mockMvc.perform(options("/dictionaries/")
                        .header("Origin", ALLOWED)
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "x-user-id"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualRequest_FromAllowedOrigin_CarriesTheOriginHeader() throws Exception {
        mockMvc.perform(get("/users/me").with(jwt()).header("Origin", ALLOWED))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    void unauthorizedResponse_ForAllowedOrigin_StillCarriesTheOriginHeader() throws Exception {
        // The browser app reads this 401 to refresh its token; without the header it sees only a network error
        mockMvc.perform(get("/users/me").header("Origin", ALLOWED))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED));
    }

    @Test
    void actualRequest_FromUnknownOrigin_IsRefused() throws Exception {
        mockMvc.perform(get("/users/me").with(jwt()).header("Origin", OTHER))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        assertTrue(received.isEmpty());
    }

    @Test
    void requestWithoutAnOrigin_IsUnaffected() throws Exception {
        mockMvc.perform(get("/users/me").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
