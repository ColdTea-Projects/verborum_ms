package de.coldtea.verborum.msgateway;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * P5-03: the real decoder, nothing mocked. Tokens are signed with a key the test publishes as a JWKS, so the
 * gateway verifies signature, issuer, audience and expiry exactly as in production. The upstream is a stub
 * that counts what reaches it: a refused token must leave the count at zero.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtValidationTest {

    private static final String ISSUER = "http://issuer.test/realms/verborum";
    private static final String AUDIENCE = "verborum-api";

    private static RSAKey signingKey;
    private static HttpServer jwks;
    private static HttpServer upstream;
    private static final List<String> received = new CopyOnWriteArrayList<>();

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("k1").generate();

        jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        jwks.createContext("/certs", exchange -> {
            byte[] body = new JWKSet(signingKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwks.start();

        upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/", exchange -> {
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " userId=" + exchange.getRequestHeaders().getFirst("X-User-Id")
                    + " forwardedUser=" + exchange.getRequestHeaders().getFirst("X-Forwarded-User")
                    + " device=" + exchange.getRequestHeaders().getFirst("X-Device-Id"));
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        upstream.start();

        registry.add("KEYCLOAK_ISSUER_URI", () -> ISSUER);
        registry.add("KEYCLOAK_JWK_SET_URI", () -> "http://127.0.0.1:" + jwks.getAddress().getPort() + "/certs");
        registry.add("USER_URL", () -> "http://127.0.0.1:" + upstream.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        jwks.stop(0);
        upstream.stop(0);
    }

    @BeforeEach
    void clear() {
        received.clear();
    }

    private static String token(RSAKey key, String issuer, String audience, Date expiry) throws Exception {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject("kc-subject-1").issuer(issuer)
                .issueTime(new Date(System.currentTimeMillis() - 10_000))
                .expirationTime(expiry);
        if (audience != null) {
            claims.audience(audience);
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID())
                .type(JOSEObjectType.JWT).build(), claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }

    private static Date in(long seconds) {
        return new Date(System.currentTimeMillis() + seconds * 1000);
    }

    private void expectRefused(String bearer) throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + bearer)).andExpect(status().isUnauthorized());
        assertTrue(received.isEmpty(), "reached the upstream: " + received);
    }

    @Test
    void validToken_IsRouted() throws Exception {
        mockMvc.perform(get("/users/me").header("Authorization", "Bearer " + token(signingKey, ISSUER, AUDIENCE, in(300))))
                .andExpect(status().isOk());

        assertEquals(1, received.size());
    }

    @Test
    void expiredToken_IsRefusedAtTheGateway() throws Exception {
        expectRefused(token(signingKey, ISSUER, AUDIENCE, in(-300)));
    }

    @Test
    void wrongAudience_IsRefusedAtTheGateway() throws Exception {
        expectRefused(token(signingKey, ISSUER, "account", in(300)));
    }

    @Test
    void missingAudience_IsRefusedAtTheGateway() throws Exception {
        expectRefused(token(signingKey, ISSUER, null, in(300)));
    }

    @Test
    void wrongIssuer_IsRefusedAtTheGateway() throws Exception {
        expectRefused(token(signingKey, "http://evil.test/realms/verborum", AUDIENCE, in(300)));
    }

    @Test
    void tokenSignedWithAnotherKey_IsRefusedAtTheGateway() throws Exception {
        RSAKey other = new RSAKeyGenerator(2048).keyID("k1").generate();
        expectRefused(token(other, ISSUER, AUDIENCE, in(300)));
    }

    @Test
    void unsignedToken_IsRefusedAtTheGateway() throws Exception {
        PlainJWT plain = new PlainJWT(new JWTClaimsSet.Builder().subject("kc-subject-1").issuer(ISSUER)
                .audience(AUDIENCE).expirationTime(in(300)).build());
        expectRefused(plain.serialize());
    }

    @Test
    void garbageToken_IsRefusedAtTheGateway() throws Exception {
        expectRefused("not-a-jwt");
    }

    @Test
    void noToken_IsRefusedAtTheGateway() throws Exception {
        mockMvc.perform(get("/users/me")).andExpect(status().isUnauthorized());
        assertTrue(received.isEmpty());
    }

    @Test
    void tokenInTheQueryString_IsNotAccepted() throws Exception {
        mockMvc.perform(get("/users/me").queryParam("access_token", token(signingKey, ISSUER, AUDIENCE, in(300))))
                .andExpect(status().isUnauthorized());
        assertTrue(received.isEmpty());
    }

    @Test
    void identityHeadersFromTheClient_NeverReachTheService() throws Exception {
        mockMvc.perform(get("/users/me")
                        .header("Authorization", "Bearer " + token(signingKey, ISSUER, AUDIENCE, in(300)))
                        .header("X-User-Id", "someone-else")
                        .header("X-Forwarded-User", "someone-else")
                        .header("X-Device-Id", "dev-1"))
                .andExpect(status().isOk());

        assertEquals(1, received.size());
        assertTrue(received.get(0).contains("userId=null"), received.get(0));
        assertTrue(received.get(0).contains("forwardedUser=null"), received.get(0));
        assertTrue(received.get(0).contains("device=dev-1"), received.get(0));
    }
}
