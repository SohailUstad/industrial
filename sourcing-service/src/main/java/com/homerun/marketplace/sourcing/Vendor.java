package com.homerun.marketplace.sourcing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record Vendor(String id, String name, String email, String phone, boolean active) {
    public record Input(@NotBlank @Size(max = 255) String name,
                        @Size(max = 255) String email,
                        @Size(max = 80) String phone,
                        boolean active) {}
}
