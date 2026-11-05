package com.homerun.marketplace.sourcing;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VendorOffer(String id, String productId, String vendorId, String vendorSku,
                          long costMinor, int availableQuantity, int leadDays, boolean active) {
    public record Input(@NotBlank String productId,
                        @NotBlank String vendorId,
                        @Size(max = 100) String vendorSku,
                        @Min(0) long costMinor,
                        @Min(0) int availableQuantity,
                        @Min(0) int leadDays,
                        boolean active) {}
}
