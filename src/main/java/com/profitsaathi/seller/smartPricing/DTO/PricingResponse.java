package com.profitsaathi.seller.smartPricing.DTO;

public record PricingResponse(

        // --- Core Costs & Prices ---
        double totalCost,
        double breakEven,
        double aggressivePrice,
        double safePrice,
        double premiumPrice,
        Margins margin,
        double suggested,

        // --- AI Strategy Insights ---
        String strategy,
        String warning,

        // --- Location Context (Direct from Java/GeoCodeService) ---
        String location, // Mapped from location.displayName()
        String city,
        String state,

        // --- AI Market Analysis ---
        String localMarket,
        String demandLevel,
        String festivalPotential,
        String competitorDensity

) {

    public record Margins(
            double breakEven,
            double aggressive,
            double safe,
            double premium
    ) {}
}