package com.profitsaathi.seller.sales;

import com.profitsaathi.seller.ai.AiSuggestion;
import com.profitsaathi.seller.ai.AiSuggestionRepository;
import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.order.TopProductSummary;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Computes the seven-day rollup a seller sees in their Monday morning email.
 * Pure read — no rows persisted; the weekly window is derived from "today"
 * in IST so the cron's run time doesn't shift the boundaries.
 */
@Service
@RequiredArgsConstructor
public class WeeklyReportService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final OrderRepository orderRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

    public WeeklyReportPayload buildFor(Seller seller) {
        LocalDate today = LocalDate.now(IST);
        // Last seven complete days ending yesterday — when run on Monday this
        // captures the previous Mon–Sun cleanly.
        LocalDate weekStart = today.minusDays(7);
        LocalDate weekEnd = today.minusDays(1);
        LocalDateTime windowFrom = weekStart.atStartOfDay();
        LocalDateTime windowUntil = today.atStartOfDay();
        LocalDateTime priorFrom = weekStart.minusDays(7).atStartOfDay();
        LocalDateTime priorUntil = weekStart.atStartOfDay();

        BigDecimal revenue = nz(orderRepository.getRevenueBetween(seller, windowFrom, windowUntil));
        BigDecimal cogs    = nz(orderRepository.getCogsBetween(seller, windowFrom, windowUntil));
        BigDecimal profit  = nz(orderRepository.getProfitBetween(seller, windowFrom, windowUntil));
        long orderCount    = orderRepository.getOrderCountBetween(seller, windowFrom, windowUntil);

        BigDecimal priorRevenue = nz(orderRepository.getRevenueBetween(seller, priorFrom, priorUntil));
        BigDecimal priorProfit  = nz(orderRepository.getProfitBetween(seller, priorFrom, priorUntil));
        long priorOrders        = orderRepository.getOrderCountBetween(seller, priorFrom, priorUntil);

        BigDecimal margin = revenue.signum() > 0
                ? profit.divide(revenue, 4, RoundingMode.HALF_UP).movePointRight(2)
                : BigDecimal.ZERO;
        BigDecimal aov = orderCount > 0
                ? revenue.divide(BigDecimal.valueOf(orderCount), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        List<TopProductSummary> top = orderRepository.topProductsBetween(
                seller, windowFrom, windowUntil, PageRequest.of(0, 3));

        List<String> tips = aiSuggestionRepository
                .findBySellerAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        seller, "ACTIVE",
                        LocalDateTime.now().minusDays(35),
                        PageRequest.of(0, 3))
                .stream()
                .map(AiSuggestion::getDescription)
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .toList();

        return new WeeklyReportPayload(
                weekStart,
                weekEnd,
                revenue,
                cogs,
                profit,
                margin,
                orderCount,
                aov,
                pctDelta(revenue, priorRevenue),
                pctDelta(profit, priorProfit),
                orderCount - priorOrders,
                top,
                tips
        );
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** Returns null when the prior period was zero — the template renders
     *  "new" instead of dividing by zero. */
    private static BigDecimal pctDelta(BigDecimal current, BigDecimal prior) {
        if (prior == null || prior.signum() <= 0) return null;
        return current.subtract(prior)
                .divide(prior, 4, RoundingMode.HALF_UP)
                .movePointRight(2);
    }
}
