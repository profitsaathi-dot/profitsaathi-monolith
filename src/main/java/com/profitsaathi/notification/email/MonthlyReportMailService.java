package com.profitsaathi.notification.email;

import com.profitsaathi.seller.ai.AiSuggestion;
import com.profitsaathi.seller.ai.AiSuggestionRepository;
import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.order.TopProductSummary;
import com.profitsaathi.seller.sales.SalesSummary;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends the post-month-close business report email to a seller. Builds the
 * template variables from the persisted {@link SalesSummary} plus a fresh
 * top-products query and the seller's most recent growth tips. The actual
 * SMTP dispatch is {@code @Async} inside {@link EmailService}, so callers
 * (the scheduler) return immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MonthlyReportMailService {

    private final EmailService emailService;
    private final OrderRepository orderRepository;
    private final AiSuggestionRepository aiSuggestionRepository;

    @Value("${profitsaathi.public-api-url}")
    private String publicUrl;

    public void sendMonthlyReport(Seller seller, SalesSummary summary) {
        if (seller == null || summary == null) return;
        if (seller.getEmail() == null || seller.getEmail().isBlank()) {
            log.warn("Skipping monthly report — seller {} has no email", seller.getId());
            return;
        }

        List<TopProductSummary> topProducts = orderRepository.topProductsForMonth(
                seller, summary.getMonth(), summary.getYear(), PageRequest.of(0, 3));

        // Recent suggestions: anything created in the last 35 days so the
        // batch the monthly scheduler just generated is included.
        List<AiSuggestion> recentTips = aiSuggestionRepository
                .findBySellerAndStatusAndCreatedAtAfterOrderByCreatedAtDesc(
                        seller, "ACTIVE",
                        LocalDateTime.now().minusDays(35),
                        PageRequest.of(0, 3));

        Map<String, Object> vars = new HashMap<>();
        vars.put("sellerName", seller.getName() == null ? "Seller" : seller.getName());
        vars.put("monthLabel", monthLabel(summary.getMonth(), summary.getYear()));
        vars.put("revenue", formatINR(summary.getTotalSales()));
        vars.put("cogs", formatINR(summary.getTotalExpenses()));
        vars.put("netProfit", formatINR(summary.getNetProfit()));
        vars.put("margin", formatPercent(summary.getProfitMargin()));
        vars.put("healthScore", summary.getHealthScore() == null ? 0 : summary.getHealthScore());
        vars.put("healthBand", healthBand(summary.getHealthScore()));
        vars.put("topProducts", topProducts.stream()
                .map(tp -> Map.of(
                        "name", tp.name() == null ? "—" : tp.name(),
                        "profit", formatINR(tp.profit()),
                        "units", tp.units() == null ? 0 : tp.units()))
                .toList());
        vars.put("tips", recentTips.stream()
                .map(s -> s.getDescription() == null ? "" : s.getDescription().trim())
                .filter(s -> !s.isEmpty())
                .toList());
        vars.put("dashboardUrl", publicUrl + "/profit");

        emailService.send(EmailRequest.builder()
                .to(List.of(seller.getEmail()))
                .subject("Your ProfitSaathi report — " + vars.get("monthLabel"))
                .templateName("monthly-report")
                .variables(vars)
                .build());
    }

    private String monthLabel(Integer month, Integer year) {
        if (month == null || year == null) return "this month";
        try {
            return Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
        } catch (Exception e) {
            return month + "/" + year;
        }
    }

    private String formatINR(BigDecimal v) {
        if (v == null) return "₹0";
        return "₹" + v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(BigDecimal v) {
        if (v == null) return "0%";
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private String healthBand(Integer score) {
        if (score == null) return "Unknown";
        if (score >= 80) return "Excellent";
        if (score >= 60) return "Healthy";
        if (score >= 40) return "Needs work";
        return "Critical";
    }
}
