package com.profitsaathi.seller.smartPricing.DTO;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PricingRequest(
        @NotNull Double costPrice,
        Double shippingCost,
        Double packagingCost,
        Double competitorPrice,
        @NotBlank String productName,
        @NotBlank String sellingMethod,
        @NotBlank String description,
        Double lat,
        Double lng
) {}

