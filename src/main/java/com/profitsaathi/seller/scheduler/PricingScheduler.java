package com.profitsaathi.seller.scheduler;

import com.profitsaathi.seller.pricing.PricingEngineService;
import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.product.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Walks ACTIVE products once per day and asks the pricing engine to recompute
 * their suggested prices. The previous version ran every 5 minutes against
 * {@code findAll()}, which (a) wasted 288 full-table scans per day even
 * though {@link PricingEngineService#runAutoPricing} short-circuits after the
 * first call of the day, (b) loaded INACTIVE products into memory, and
 * (c) wrapped the entire loop (including a 5-30 s LLM call per product) in a
 * single transaction.
 *
 * <p>Defaults to 03:00 IST daily ({@code 0 0 3 * * ?}). Override via
 * {@code pricing.scheduler.cron}; set to {@code -} to disable. Provider
 * throttle is configurable via {@code pricing.scheduler.delay-ms} (default
 * 1500 ms between products, same idiom as {@code GrowthCardScheduler}).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PricingScheduler {

    private static final int PAGE_SIZE = 200;

    private final ProductRepository productRepository;
    private final PricingEngineService pricingEngineService;

    @Value("${pricing.scheduler.delay-ms:1500}")
    private long perProductDelayMs;

    @Scheduled(cron = "${pricing.scheduler.cron:0 0 3 * * ?}", zone = "Asia/Kolkata")
    public void autoRunPricing() {
        long started = System.currentTimeMillis();
        log.info("Auto Pricing Engine started");

        int processed = 0, failed = 0, page = 0;
        Page<Product> slice;
        do {
            slice = productRepository.findByStatus("ACTIVE", PageRequest.of(page, PAGE_SIZE));
            for (Product p : slice.getContent()) {
                try {
                    pricingEngineService.runAutoPricing(p);
                    processed++;
                } catch (Exception e) {
                    // Contain per-product failures — one bad product or an LLM
                    // timeout shouldn't take the whole batch down.
                    log.warn("Auto-pricing failed for productId={}: {}",
                            p.getId(), e.getMessage());
                    failed++;
                }
                sleep(perProductDelayMs);
            }
            page++;
        } while (slice.hasNext());

        log.info("Auto Pricing Engine completed in {} ms — processed={}, failed={}",
                System.currentTimeMillis() - started, processed, failed);
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
