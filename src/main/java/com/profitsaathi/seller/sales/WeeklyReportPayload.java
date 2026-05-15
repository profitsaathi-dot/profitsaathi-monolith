package com.profitsaathi.seller.sales;

import com.profitsaathi.seller.order.TopProductSummary;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Snapshot of a seller's last seven business days, plus a week-over-week
 * delta against the prior seven. Not persisted — recomputed on each weekly
 * scheduler tick because storage adds no value here.
 */
public record WeeklyReportPayload(
        LocalDate weekStart,         // inclusive
        LocalDate weekEnd,           // inclusive
        BigDecimal revenue,
        BigDecimal cogs,
        BigDecimal netProfit,
        BigDecimal margin,           // percent
        long orderCount,
        BigDecimal aov,              // average order value
        BigDecimal revenueDeltaPct,  // week-over-week (null when prior week was zero)
        BigDecimal profitDeltaPct,
        Long orderCountDelta,
        List<TopProductSummary> topProducts,
        List<String> tips
) {}
