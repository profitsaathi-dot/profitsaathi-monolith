package com.profitsaathi.notification.email;

import com.profitsaathi.seller.sales.WeeklyReportPayload;
import com.profitsaathi.seller.user.Seller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hands a pre-computed {@link WeeklyReportPayload} to the shared Thymeleaf
 * mailer. Only formats the numbers for display — no business logic here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WeeklyReportMailService {

    private static final DateTimeFormatter WEEK_FMT = DateTimeFormatter.ofPattern("MMM d");
    private static final DateTimeFormatter WEEK_FMT_FULL = DateTimeFormatter.ofPattern("MMM d, yyyy");

    private final EmailService emailService;

    @Value("${profitsaathi.public-api-url}")
    private String publicUrl;

    public void sendWeeklyReport(Seller seller, WeeklyReportPayload p) {
        if (seller == null || p == null) return;
        if (seller.getEmail() == null || seller.getEmail().isBlank()) {
            log.warn("Skipping weekly report — seller {} has no email", seller.getId());
            return;
        }

        Map<String, Object> vars = new HashMap<>();
        vars.put("sellerName", seller.getName() == null ? "Seller" : seller.getName());
        vars.put("weekLabel", p.weekStart().format(WEEK_FMT) + " – " + p.weekEnd().format(WEEK_FMT_FULL));
        vars.put("revenue", formatINR(p.revenue()));
        vars.put("cogs", formatINR(p.cogs()));
        vars.put("netProfit", formatINR(p.netProfit()));
        vars.put("margin", formatPercent(p.margin()));
        vars.put("orderCount", p.orderCount());
        vars.put("aov", formatINR(p.aov()));
        vars.put("revenueDelta", formatDelta(p.revenueDeltaPct()));
        vars.put("revenueDeltaUp", isPositive(p.revenueDeltaPct()));
        vars.put("profitDelta", formatDelta(p.profitDeltaPct()));
        vars.put("profitDeltaUp", isPositive(p.profitDeltaPct()));
        vars.put("orderCountDelta", p.orderCountDelta() == null ? 0L : p.orderCountDelta());
        vars.put("orderCountDeltaUp", p.orderCountDelta() != null && p.orderCountDelta() >= 0);
        vars.put("topProducts", p.topProducts().stream()
                .map(tp -> Map.of(
                        "name", tp.name() == null ? "—" : tp.name(),
                        "profit", formatINR(tp.profit()),
                        "units", tp.units() == null ? 0 : tp.units()))
                .toList());
        vars.put("tips", p.tips() == null ? List.of() : p.tips());
        vars.put("dashboardUrl", publicUrl + "/profit");

        emailService.send(EmailRequest.builder()
                .to(List.of(seller.getEmail()))
                .subject("Your weekly business pulse — " + vars.get("weekLabel"))
                .templateName("weekly-report")
                .variables(vars)
                .build());
    }

    private String formatINR(BigDecimal v) {
        if (v == null) return "₹0";
        return "₹" + v.setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private String formatPercent(BigDecimal v) {
        if (v == null) return "0%";
        return v.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    /** Null delta → "new" (prior period had nothing to compare against). */
    private String formatDelta(BigDecimal v) {
        if (v == null) return "new";
        String sign = v.signum() >= 0 ? "+" : "";
        return sign + v.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    private boolean isPositive(BigDecimal v) {
        return v == null || v.signum() >= 0;
    }
}
