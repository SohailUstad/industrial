package com.homerun.marketplace.catalog;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Product(String id, String sku, String name, String category, String description,
                      String unit, long priceMinor, String imageUrl, boolean active) {
    public record Input(
            @NotBlank @Size(max = 80) String sku,
            @NotBlank @Size(max = 255) String name,
            @NotBlank @Size(max = 120) String category,
            @NotBlank @Size(max = 3000) String description,
            @NotBlank @Size(max = 40) String unit,
            @Min(0) long priceMinor,
            @Size(max = 2000) String imageUrl,
            boolean active) {}
}
