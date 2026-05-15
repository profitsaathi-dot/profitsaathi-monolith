package com.profitsaathi.seller.scheduler;

import com.profitsaathi.notification.email.WeeklyReportMailService;
import com.profitsaathi.seller.product.ProductRepository;
import com.profitsaathi.seller.sales.WeeklyReportPayload;
import com.profitsaathi.seller.sales.WeeklyReportService;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Mails the weekly business pulse to every active seller. Default cadence is
 * Monday 06:00 IST so the email lands at the start of the working week, but
 * the cron is fully overridable via {@code report.weekly.cron} (set to
 * {@code -} to disable). The whole flow can also be toggled off with
 * {@code report.weekly.enabled=false}.
 *
 * <p>Sellers without any products or with zero revenue for the previous week
 * are skipped — no value in mailing an empty report.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WeeklyBusinessScheduler {

    private final SellerRepository sellerRepository;
    private final ProductRepository productRepository;
    private final WeeklyReportService weeklyReportService;
    private final WeeklyReportMailService mailService;

    @Value("${report.weekly.enabled:true}")
    private boolean enabled;

    @Scheduled(cron = "${report.weekly.cron:0 0 6 * * MON}", zone = "Asia/Kolkata")
    public void sendWeeklyReports() {
        if (!enabled) {
            log.info("Weekly Business Scheduler disabled via report.weekly.enabled=false");
            return;
        }
        long started = System.currentTimeMillis();
        log.info("Weekly Business Scheduler started…");

        List<Seller> sellers = sellerRepository.findByStatus(Seller.Status.ACTIVE);
        int emailed = 0, skipped = 0, failed = 0;

        for (Seller seller : sellers) {
            try {
                if (Boolean.FALSE.equals(seller.getWeeklyReportOptIn())) {
                    skipped++;
                    continue;
                }
                if (!productRepository.existsBySeller(seller)) {
                    skipped++;
                    continue;
                }

                WeeklyReportPayload payload = weeklyReportService.buildFor(seller);
                if (payload.revenue() == null
                        || payload.revenue().compareTo(BigDecimal.ZERO) <= 0) {
                    // No orders last week — skip rather than send an empty mail.
                    skipped++;
                    continue;
                }

                mailService.sendWeeklyReport(seller, payload);
                emailed++;
            } catch (Exception e) {
                log.warn("Weekly report failed for sellerId={}: {}",
                        seller.getId(), e.getMessage());
                failed++;
            }
        }

        log.info("Weekly Business Scheduler done in {} ms — emailed={}, skipped={}, failed={}",
                System.currentTimeMillis() - started, emailed, skipped, failed);
    }
}
