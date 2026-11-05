package com.homerun.marketplace.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"ADMIN_PASSWORD=test-password", "AUTH_DEV_EPHEMERAL_KEYS=true"})
class AuthFlowTest {
    @Value("${local.server.port}") int port;
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void loginIssuesSignedShortLivedJwtWithoutPublishingPrivateKey() throws Exception {
        var wrong = login("wrong-password");
        assertEquals(401, wrong.statusCode());

        var good = login("test-password");
        assertEquals(200, good.statusCode());
        var match = Pattern.compile("\"accessToken\":\"([^\"]+)\"").matcher(good.body());
        assertTrue(match.find());
        String token = match.group(1);
        assertEquals(3, token.split("\\.").length);

        String jwksUrl = "http://localhost:" + port + "/.well-known/jwks.json";
        var decoder = NimbusJwtDecoder.withJwkSetUri(jwksUrl).build();
        var jwt = decoder.decode(token);
        assertEquals("admin", jwt.getSubject());
        assertEquals("http://localhost:8084", jwt.getIssuer().toString());
        assertTrue(jwt.getAudience().contains("homerun-admin"));
        assertEquals("admin", jwt.getClaimAsString("scope"));
        assertTrue(jwt.getExpiresAt().isAfter(jwt.getIssuedAt()));

        var jwks = client.send(HttpRequest.newBuilder(URI.create(jwksUrl)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, jwks.statusCode());
        assertFalse(jwks.body().contains("\"d\""));
    }

    private HttpResponse<String> login(String password) throws Exception {
        String json = "{\"username\":\"admin\",\"password\":\"" + password + "\"}";
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
