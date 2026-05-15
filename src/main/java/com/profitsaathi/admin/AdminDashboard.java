package com.profitsaathi.admin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Read-model for /admin overview. One round-trip — header KPIs, daily GMV
 * trend, and top sellers all bundled together so the dashboard renders in
 * a single fetch.
 */
public record AdminDashboard(
        int windowDays,
        Totals totals,
        List<DailyGmv> gmvByDay,
        List<TopSeller> topSellers
) {
    public record Totals(
            long activeSellers,
            long totalSellers,
            long newSellersInWindow,
            long ordersInWindow,
            BigDecimal gmvInWindow,
            BigDecimal averageOrderValue
    ) {}

    public record DailyGmv(LocalDate date, BigDecimal value) {}

    public record TopSeller(
            Long sellerId,
            String name,
            String email,
            String storeName,
            String status,
            long orders,
            BigDecimal gmv
    ) {}
}
