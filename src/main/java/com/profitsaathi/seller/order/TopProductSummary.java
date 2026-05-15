package com.profitsaathi.seller.order;

import java.math.BigDecimal;

/**
 * Projection used by the report mail flows to surface each seller's top
 * products by profit contribution for a given period. Pure data — no JPA
 * mapping; populated via JPQL constructor expression.
 */
public record TopProductSummary(
        Long productId,
        String name,
        BigDecimal revenue,
        BigDecimal profit,
        Long units
) {}
