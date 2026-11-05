package com.homerun.marketplace.orders;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDateTime;
import java.util.List;

public final class OrderModels {
    private OrderModels() {}

    public record CreateOrder(
            @NotBlank @Size(max = 120) String customerName,
            @NotBlank @Pattern(regexp = "[0-9+() -]{7,24}") String phone,
            @NotBlank @Size(max = 255) String addressLine,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(min = 4, max = 16) String postalCode,
            @Min(0) long expectedTotalMinor,
            @NotEmpty @Size(max = 50) List<@Valid ItemRequest> items) {}

    public record ItemRequest(@NotBlank String productId, @Min(1) @Max(999) int quantity) {}
    public record CatalogProduct(String id, String sku, String name, String category,
                                 String description, String unit, long priceMinor,
                                 String imageUrl, boolean active) {}
    public record OrderItem(String productId, String sku, String name, String unit,
                            int quantity, long unitPriceMinor, long lineTotalMinor) {}
    public record Receipt(String id, String status, String paymentMethod, long totalMinor,
                          List<OrderItem> items) {}
    public record AdminOrder(String id, String customerName, String phone,
                             String addressLine, String city, String postalCode,
                             String status, String paymentMethod, long totalMinor,
                             LocalDateTime createdAt, List<OrderItem> items) {}
    public record StatusUpdate(@NotBlank String status) {}
}
