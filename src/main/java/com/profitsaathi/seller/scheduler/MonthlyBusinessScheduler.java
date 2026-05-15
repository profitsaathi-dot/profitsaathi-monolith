package com.profitsaathi.seller.scheduler;

import com.profitsaathi.notification.email.MonthlyReportMailService;
import com.profitsaathi.seller.ai.GrowthSuggestionService;
import com.profitsaathi.seller.order.OrderRepository;
import com.profitsaathi.seller.product.ProductRepository;
import com.profitsaathi.seller.sales.SalesSummary;
import com.profitsaathi.seller.sales.SalesSummaryRepository;
import com.profitsaathi.seller.sales.SalesSummaryService;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyBusinessScheduler {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final SellerRepository sellerRepository;
    private final SalesSummaryService salesSummaryService;
    private final GrowthSuggestionService growthSuggestionService;
    private final ProductRepository productRepository;
    private final SalesSummaryRepository salesSummaryRepository;
    private final OrderRepository orderRepository;
    private final MonthlyReportMailService monthlyReportMailService;

    @Value("${report.monthly.enabled:true}")
    private boolean reportEnabled;

    /**
     * Runs on the 1st of every month at 02:00 IST by default. Cron is
     * overridable via {@code report.monthly.cron}; set to {@code -} to
     * disable. Zone is pinned so the "last month" boundary doesn't shift
     * with the host timezone.
     */
    @Scheduled(cron = "${report.monthly.cron:0 0 2 1 * ?}", zone = "Asia/Kolkata")
    public void generateMonthlyBusinessReport() {
        log.info("Running Monthly Business Scheduler...");

        List<Seller> sellers = sellerRepository.findByStatus(Seller.Status.ACTIVE);

        LocalDate lastMonthDate = LocalDate.now(IST).minusMonths(1);
        int month = lastMonthDate.getMonthValue();
        int year = lastMonthDate.getYear();

        int processed = 0, skipped = 0, emailed = 0, failed = 0;
        for (Seller seller : sellers) {
            try {
                if (!productRepository.existsBySeller(seller)) {
                    log.info("Seller {} skipped — no products", seller.getId());
                    skipped++;
                    continue;
                }
                if (salesSummaryRepository.existsBySellerAndMonthAndYear(seller, month, year)) {
                    log.info("Seller {} skipped — summary already exists", seller.getId());
                    skipped++;
                    continue;
                }

                BigDecimal totalSales = orderRepository.getRevenueForMonth(seller, month, year);
                if (totalSales == null || totalSales.compareTo(BigDecimal.ZERO) <= 0) {
                    log.info("Seller {} skipped — no sales", seller.getId());
                    skipped++;
                    continue;
                }
                BigDecimal totalExpenses = orderRepository.getTotalExpensesForMonth(seller, month, year);

                SalesSummary summary = salesSummaryService.createMonthlySummary(
                        seller, month, year, totalSales, totalExpenses);
                growthSuggestionService.generateGrowthSuggestions(seller, summary);

                if (reportEnabled && shouldEmail(seller, summary)) {
                    monthlyReportMailService.sendMonthlyReport(seller, summary);
                    summary.setEmailedAt(LocalDateTime.now());
                    salesSummaryRepository.save(summary);
                    emailed++;
                }

                log.info("Report generated for seller: {}", seller.getId());
                processed++;
            } catch (Exception e) {
                log.error("Error processing seller: {}", seller.getId(), e);
                failed++;
            }
        }

        log.info("Monthly Business Scheduler completed — processed={}, emailed={}, skipped={}, failed={}.",
                processed, emailed, skipped, failed);
    }

    private boolean shouldEmail(Seller seller, SalesSummary summary) {
        if (summary.getEmailedAt() != null) return false;
        if (Boolean.FALSE.equals(seller.getMonthlyReportOptIn())) return false;
        return true;
    }
}
