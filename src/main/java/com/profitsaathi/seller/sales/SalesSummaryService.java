package com.profitsaathi.seller.sales;

import com.profitsaathi.seller.ai.AiSuggestion;
import com.profitsaathi.seller.ai.AiSuggestionRepository;
import com.profitsaathi.seller.dashboard.DashboardSummaryDTO;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class SalesSummaryService {

    private final SalesSummaryRepository repository;
    private final AiSuggestionRepository aiSuggestionRepository;

    public SalesSummary createMonthlySummary(Seller seller,
                                             Integer month,
                                             Integer year,
                                             BigDecimal totalSales,
                                             BigDecimal totalExpenses) {
        return persistSummary(seller, month, year, totalSales, totalExpenses, true);
    }

    /**
     * Overwrites the existing {@link SalesSummary} row for (seller, month,
     * year) with freshly-computed totals. Skips the low-profit alert and any
     * side-effects intended for first-time computation — the use-case is
     * backfilling historical rows that were wrong because the old
     * {@code getTotalSalesForMonth} repository query summed COGS.
     *
     * <p>If no row exists for the period, returns {@code null} — the admin
     * recompute endpoint deliberately doesn't materialise new rows; that's a
     * job for the scheduler.
     */
    public SalesSummary recomputeMonthlySummary(Seller seller,
                                                Integer month,
                                                Integer year,
                                                BigDecimal totalSales,
                                                BigDecimal totalExpenses) {
        if (repository.findBySellerIdAndMonthAndYear(seller.getId(), month, year).isEmpty()) {
            return null;
        }
        return persistSummary(seller, month, year, totalSales, totalExpenses, false);
    }

    private SalesSummary persistSummary(Seller seller,
                                        Integer month,
                                        Integer year,
                                        BigDecimal totalSales,
                                        BigDecimal totalExpenses,
                                        boolean fireAlerts) {
        BigDecimal netProfit = totalSales.subtract(totalExpenses);
        BigDecimal profitMargin = BigDecimal.ZERO;

        if (totalSales.compareTo(BigDecimal.ZERO) > 0) {
            profitMargin = netProfit
                    .divide(totalSales, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        if (fireAlerts && profitMargin.compareTo(BigDecimal.valueOf(5)) < 0) {
            triggerLowProfitAlert(seller, profitMargin);
        }

        Integer healthScore = calculateHealthScore(profitMargin);

        // Reuse the existing row if one exists — keeps the row's id stable so
        // foreign-key references (e.g. AiSuggestion linkbacks) survive a
        // recompute.
        SalesSummary summary = repository
                .findBySellerIdAndMonthAndYear(seller.getId(), month, year)
                .orElseGet(SalesSummary::new);
        summary.setSeller(seller);
        summary.setMonth(month);
        summary.setYear(year);
        summary.setTotalSales(totalSales);
        summary.setTotalExpenses(totalExpenses);
        summary.setNetProfit(netProfit);
        summary.setProfitMargin(profitMargin);
        summary.setHealthScore(healthScore);
        if (summary.getCreatedAt() == null) {
            summary.setCreatedAt(LocalDateTime.now());
        }

        repository.save(summary);
        return summary;
    }

    private Integer calculateHealthScore(BigDecimal margin) {
        if (margin.compareTo(BigDecimal.valueOf(30)) >= 0) return 95;
        if (margin.compareTo(BigDecimal.valueOf(20)) >= 0) return 85;
        if (margin.compareTo(BigDecimal.valueOf(10)) >= 0) return 70;
        if (margin.compareTo(BigDecimal.valueOf(5)) >= 0) return 50;
        return 30;
    }

    private void triggerLowProfitAlert(Seller seller, BigDecimal margin) {
        AiSuggestion alert = new AiSuggestion();
        alert.setSeller(seller);
        alert.setSuggestionType("ALERT");
        alert.setTitle("Low Profit Warning");
        alert.setDescription("Your profit margin is very low (" + margin + "%). "
                + "Check pricing or reduce expenses immediately.");
        alert.setPriority("HIGH");
        alert.setConfidenceScore(95);
        alert.setCreatedAt(LocalDateTime.now());
        aiSuggestionRepository.save(alert);
    }

    public DashboardSummaryDTO getDashboard(Long sellerId) {
        return repository.getDashboardSummary(sellerId);
    }
}
