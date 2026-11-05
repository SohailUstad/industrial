package com.homerun.marketplace.orders;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

@Component
public class CatalogClient {
    private final RestClient client;

    public CatalogClient(@Value("${app.catalog-base-url}") String url) {
        var requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(Duration.ofSeconds(3));
        requests.setReadTimeout(Duration.ofSeconds(5));
        this.client = RestClient.builder().baseUrl(url).requestFactory(requests).build();
    }

    public OrderModels.CatalogProduct product(String id) {
        try {
            var product = client.get().uri("/api/products/{id}", id).retrieve().body(OrderModels.CatalogProduct.class);
            if (product == null || !product.active()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product is unavailable");
            return product;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A product is unavailable");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog could not be checked");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Catalog is unavailable");
        }
    }
}
