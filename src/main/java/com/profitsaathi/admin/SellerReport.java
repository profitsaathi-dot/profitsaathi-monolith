package com.profitsaathi.admin;

import com.profitsaathi.seller.user.Seller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Consolidated read-model the admin app renders on /admin/sellers/{id}.
 * Shaped to be one round-trip — adding more fields here is cheaper than
 * spawning more endpoints.
 */
public record SellerReport(
        Seller seller,
        OrderStats orders,
        ProductStats products
) {
    public record OrderStats(
            long total,
            BigDecimal totalRevenue,
            BigDecimal averageOrderValue,
            LocalDateTime lastOrderAt,
            Map<String, Long> byOrderStatus,
            Map<String, Long> byPaymentStatus
    ) {}

    public record ProductStats(
            long total,
            long active,
            long inactive
    ) {}
}
