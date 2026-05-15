package com.profitsaathi.ai.cost;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Per-token pricing book. Mirrors the Python {@code CostTracker.PRICES}
 * — extend the map as new models are onboarded. Unknown models fall back
 * to {@link #DEFAULT_PRICE} so requests still log with a non-zero cost.
 */
@Component
public class AiCostTracker {

    private static final BigDecimal DEFAULT_PRICE = new BigDecimal("0.0002");

    private static final Map<String, BigDecimal> PRICES = Map.of(
            "gpt-4o-mini", new BigDecimal("0.00015"),
            "gemini",      new BigDecimal("0.0001")
    );

    public BigDecimal calculate(String model, long tokens) {
        BigDecimal price = PRICES.getOrDefault(model, DEFAULT_PRICE);
        return price.multiply(BigDecimal.valueOf(tokens))
                .setScale(6, RoundingMode.HALF_UP);
    }
}
