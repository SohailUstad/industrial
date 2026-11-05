package com.homerun.marketplace.catalog;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CatalogFlowTest {
    @Value("${local.server.port}") int port;
    @MockitoBean JwtDecoder decoder;
    private final HttpClient client = HttpClient.newHttpClient();
    private final String auth = "Bearer test-token";

    @Test
    void customerSeesOnlyActiveProductData() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("admin").claim("scope", "admin")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build());
        String body = """
                {"sku":"NAIL-50","name":"Steel nails","category":"Fasteners",
                 "description":"Box of nails","unit":"box","priceMinor":12900,
                 "imageUrl":null,"active":true}
                """;
        var unauthorized = send("GET", "/api/admin/products", null, false);
        assertEquals(401, unauthorized.statusCode());
        var created = send("POST", "/api/admin/products", body, true);
        assertEquals(201, created.statusCode());
        var match = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(created.body());
        assertTrue(match.find());
        String id = match.group(1);
        var publicList = send("GET", "/api/products", null, false);
        assertEquals(200, publicList.statusCode());
        assertTrue(publicList.body().contains("Steel nails"));
        assertFalse(publicList.body().contains("vendor"));
        assertFalse(publicList.body().contains("costMinor"));
        assertEquals(204, send("DELETE", "/api/admin/products/" + id, null, true).statusCode());
        assertFalse(send("GET", "/api/products", null, false).body().contains("Steel nails"));
    }

    private HttpResponse<String> send(String method, String path, String body, boolean admin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (admin) builder.header("Authorization", auth);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
