package de.coldtea.verborum.msgateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5-02: the route table against a real stub upstream (the JDK's own HTTP server, so no extra
 * dependency). One stub stands behind all four services; it records what it received and echoes the
 * path, so a test sees both where a request went and what the gateway forwarded.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoutingTest {

    private static HttpServer upstream;

    private static final List<String> received = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @BeforeAll
    static void startUpstream() throws IOException {
        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            received.add(exchange.getRequestMethod() + " " + path
                    + " auth=" + exchange.getRequestHeaders().getFirst("Authorization")
                    + " device=" + exchange.getRequestHeaders().getFirst("X-Device-Id")
                    + " length=" + exchange.getRequestHeaders().getFirst("Content-Length")
                    + " chunked=" + exchange.getRequestHeaders().getFirst("Transfer-Encoding"));
            int status = path.contains("/missing") ? 404 : 200;
            byte[] body = ("{\"path\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
    }

    @AfterAll
    static void stopUpstream() {
        upstream.stop(0);
    }

    @DynamicPropertySource
    static void routes(DynamicPropertyRegistry registry) throws IOException {
        // started here as well: DynamicPropertySource runs before @BeforeAll
        if (upstream == null) {
            startUpstream();
        }
        String url = "http://127.0.0.1:" + upstream.getAddress().getPort();
        registry.add("DICTIONARY_URL", () -> url);
        registry.add("USER_URL", () -> url);
        registry.add("MARKETPLACE_URL", () -> url);
        registry.add("NOTIFICATION_URL", () -> url);
    }

    @Test
    void everyContractPrefix_IsRouted() throws Exception {
        for (String path : List.of("/dictionaries/d1", "/dictionaries/d1/tags", "/words/w1", "/users/me",
                "/users/u1/vault", "/marketplace/dictionaries", "/notifications/devices/dev1")) {
            mockMvc.perform(get(path).with(jwt()))
                    .andExpect(status().isOk())
                    .andExpect(content().json("{\"path\":\"" + path + "\"}"));
        }
    }

    /**
     * The order of the parameters may change on the way through; their names and encoded values may not.
     * No literal `+` here: MockMvc reads it as a plus, Tomcat as a space. Against a real Tomcat the gateway
     * turns `au+lait` into `au%20lait` and keeps `%2B` (checked by hand, P5-02).
     */
    @Test
    void queryString_PassesThrough() throws Exception {
        String body = mockMvc.perform(get(java.net.URI.create("/marketplace/dictionaries?page=2&size=20&search=caf%C3%A9%20au%20lait&q=a%2Bb")).with(jwt()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("page=2"), body);
        assertTrue(body.contains("size=20"), body);
        assertTrue(body.contains("search=caf%C3%A9%20au%20lait"), body);
        assertTrue(body.contains("q=a%2Bb"), body);
    }

    @Test
    void authorizationAndDeviceId_PassThroughUnchanged() throws Exception {
        received.clear();

        // A real bearer token through the real chain: the decoder accepts it, so the header reaches the route
        when(jwtDecoder.decode("abc.def.ghi")).thenReturn(Jwt.withTokenValue("abc.def.ghi")
                .header("alg", "RS256").subject("kc-1").build());

        mockMvc.perform(put("/notifications/devices")
                        .header("Authorization", "Bearer abc.def.ghi")
                        .header("X-Device-Id", "11111111-2222-3333-4444-555555555555")
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk());

        assertEquals(1, received.size());
        assertTrue(received.get(0).startsWith("PUT /notifications/devices"));
        assertTrue(received.get(0).contains("auth=Bearer abc.def.ghi"));
        assertTrue(received.get(0).contains("device=11111111-2222-3333-4444-555555555555"));
    }

    /**
     * Regression (P5-02): the gateway used to strip Content-Length and forward the body chunked, and every
     * service's RequestBodyLimitFilter answers a body with no declared length with 411 — so every write
     * through the gateway failed while every read worked.
     */
    @Test
    void requestBody_KeepsItsContentLength_AndIsNotChunked() throws Exception {
        received.clear();
        when(jwtDecoder.decode("abc.def.ghi")).thenReturn(Jwt.withTokenValue("abc.def.ghi")
                .header("alg", "RS256").subject("kc-1").build());

        mockMvc.perform(put("/dictionaries/")
                        .header("Authorization", "Bearer abc.def.ghi")
                        .contentType("application/json").content("{\"a\":1}"))
                .andExpect(status().isOk());

        assertTrue(received.get(0).contains("length=7"), received.get(0));
        assertTrue(received.get(0).contains("chunked=null"), received.get(0));
    }

    @Test
    void vaultDelete_IsRouted() throws Exception {
        received.clear();

        mockMvc.perform(delete("/users/u1/vault/d1").with(jwt())).andExpect(status().isOk());

        assertTrue(received.get(0).startsWith("DELETE /users/u1/vault/d1"));
    }

    @Test
    void upstreamError_PassesThroughUnchanged() throws Exception {
        mockMvc.perform(get("/dictionaries/missing").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.path").value("/dictionaries/missing"));
    }

    @Test
    void unknownPath_IsNeverRouted() throws Exception {
        received.clear();

        mockMvc.perform(get("/elsewhere/x").with(jwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NoResourceFoundException"));

        assertTrue(received.isEmpty());
    }

    @Test
    void anonymous_NeverReachesTheUpstream() throws Exception {
        received.clear();

        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());

        assertTrue(received.isEmpty());
    }
}
