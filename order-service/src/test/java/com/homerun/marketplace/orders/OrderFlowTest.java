package com.homerun.marketplace.orders;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderFlowTest {
    @Value("${local.server.port}") int port;
    @MockitoBean CatalogClient catalog;
    @MockitoBean JwtDecoder decoder;
    private final HttpClient client = HttpClient.newHttpClient();
    private final String auth = "Bearer test-token";

    @Test
    void checkoutUsesServerPriceAndIsIdempotent() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("admin").claim("scope", "admin")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build());
        String product = UUID.randomUUID().toString();
        when(catalog.product(product)).thenReturn(new OrderModels.CatalogProduct(product, "NAIL-50",
                "Steel nails", "Fasteners", "Box", "pack", 12900, null, true));
        String key = UUID.randomUUID().toString();
        String body = body(product, 2);
        var placed = send("POST", "/api/orders", body, key, false);
        assertEquals(201, placed.statusCode());
        assertTrue(placed.body().contains("\"totalMinor\":25800"));
        assertTrue(placed.body().contains("\"paymentMethod\":\"COD\""));
        String id = id(placed.body());
        assertEquals(id, id(send("POST", "/api/orders", body, key, false).body()));
        assertEquals(409, send("POST", "/api/orders", body(product, 3), key, false).statusCode());
        var priceChanged = send("POST", "/api/orders", body(product, 1), UUID.randomUUID().toString(), false);
        assertEquals(409, priceChanged.statusCode());
        assertTrue(priceChanged.body().contains("Prices changed"));
        assertEquals(401, send("GET", "/api/admin/orders", null, null, false).statusCode());
        var admin = send("GET", "/api/admin/orders", null, null, true);
        assertEquals(200, admin.statusCode());
        assertTrue(admin.body().contains("Steel nails"));
        assertEquals(200, send("PATCH", "/api/admin/orders/" + id + "/status", "{\"status\":\"CONFIRMED\"}", null, true).statusCode());
        assertEquals(409, send("PATCH", "/api/admin/orders/" + id + "/status", "{\"status\":\"DELIVERED\"}", null, true).statusCode());
    }

    private String body(String product, int quantity) {
        return "{\"customerName\":\"Test Buyer\",\"phone\":\"9876543210\",\"addressLine\":\"1 Main Road\","
                + "\"city\":\"Pune\",\"postalCode\":\"411001\",\"expectedTotalMinor\":25800,\"items\":[{\"productId\":\"" + product
                + "\",\"quantity\":" + quantity + "}]}";
    }

    private String id(String json) {
        var match = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(json);
        assertTrue(match.find());
        return match.group(1);
    }

    private HttpResponse<String> send(String method, String path, String body, String key, boolean admin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (admin) builder.header("Authorization", auth);
        if (body != null) builder.header("Content-Type", "application/json");
        if (key != null) builder.header("Idempotency-Key", key);
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
