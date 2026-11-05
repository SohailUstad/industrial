package com.homerun.marketplace.sourcing;

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
class SourcingFlowTest {
    @Value("${local.server.port}") int port;
    @MockitoBean JwtDecoder decoder;
    private final HttpClient client = HttpClient.newHttpClient();
    private final String auth = "Bearer test-token";

    @Test
    void oneProductCanHaveTwoPrivateVendorOffers() throws Exception {
        when(decoder.decode("test-token")).thenReturn(Jwt.withTokenValue("test-token")
                .header("alg", "RS256").subject("admin").claim("scope", "admin")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(900)).build());
        assertEquals(401, send("GET", "/api/admin/vendors", null, false).statusCode());
        String vendor1 = id(send("POST", "/api/admin/vendors", vendor("A"), true));
        String vendor2 = id(send("POST", "/api/admin/vendors", vendor("B"), true));
        String product = "00000000-0000-0000-0000-000000000001";
        assertEquals(201, send("POST", "/api/admin/offers", offer(product, vendor1), true).statusCode());
        assertEquals(201, send("POST", "/api/admin/offers", offer(product, vendor2), true).statusCode());
        assertEquals(409, send("POST", "/api/admin/offers", offer(product, vendor1), true).statusCode());
        var offers = send("GET", "/api/admin/offers?productId=" + product, null, true);
        assertEquals(200, offers.statusCode());
        assertTrue(offers.body().contains(vendor1));
        assertTrue(offers.body().contains(vendor2));
        assertNotEquals(200, send("GET", "/api/vendors", null, false).statusCode());
    }

    private String vendor(String name) { return "{\"name\":\"" + name + "\",\"email\":null,\"phone\":null,\"active\":true}"; }
    private String offer(String product, String vendor) {
        return "{\"productId\":\"" + product + "\",\"vendorId\":\"" + vendor
                + "\",\"vendorSku\":null,\"costMinor\":100,\"availableQuantity\":5,\"leadDays\":2,\"active\":true}";
    }
    private String id(HttpResponse<String> response) {
        assertEquals(201, response.statusCode());
        var match = Pattern.compile("\"id\":\"([^\"]+)\"").matcher(response.body());
        assertTrue(match.find());
        return match.group(1);
    }
    private HttpResponse<String> send(String method, String path, String body, boolean admin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (admin) builder.header("Authorization", auth);
        if (body != null) builder.header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
