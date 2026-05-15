package com.profitsaathi.ai.growthadviser;

import com.profitsaathi.ai.AiProperties;
import com.profitsaathi.seller.user.Seller;
import com.profitsaathi.seller.user.SellerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Daily auto-sync. Wakes up at the configured cron, finds active sellers
 * whose last sync was longer than {@code freeCooldown} ago, and runs a
 * fresh sync for each. Free sellers thus get exactly one auto-refresh per
 * day; Premium sellers also benefit (and can additionally hit /sync more
 * often within their tighter cooldown).
 *
 * Cron defaults to 04:00 server-time, override with
 * {@code growth-card.scheduler.cron}. Set to {@code -} to disable entirely.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrowthCardScheduler {

    private final SellerRepository sellerRepository;
    private final GrowthSyncStateRepository syncStateRepository;
    private final GrowthCardRepository cardRepository;
    private final GrowthCardSyncService syncService;
    private final AiProperties props;

    /** Throttle between LLM calls so we don't burst OpenRouter's rate limit. */
    @Value("${growth-card.scheduler.delay-ms:1500}")
    private long perSellerDelayMs;

    @Scheduled(cron = "${growth-card.scheduler.cron:0 0 4 * * *}", zone = "Asia/Kolkata")
    public void runDailySync() {
        if (props.getOpenrouterKey() == null || props.getOpenrouterKey().isBlank()) {
            log.info("Growth card scheduler skipped — ai.openrouter-key not configured.");
            return;
        }
        long started = System.currentTimeMillis();
        LocalDateTime cutoff = LocalDateTime.now()
                .minusHours(Math.max(1, props.getGrowthCardCooldownHoursFree()));

        // Sellers due via the sync_state table.
        Set<Long> due = new HashSet<>(syncStateRepository.findSellerIdsDueForSync(cutoff));
        // Plus active sellers who have never synced (not in the state table yet).
        for (Seller s : sellerRepository.findByStatus(Seller.Status.ACTIVE)) {
            if (s.getId() != null && syncStateRepository.findById(s.getId()).isEmpty()) {
                due.add(s.getId());
            }
        }
        log.info("Growth card scheduler — {} sellers due for sync.", due.size());

        int ok = 0, skipped = 0, failed = 0;
        for (Long sellerId : due) {
            try {
                syncService.sync(sellerId, "scheduled");
                ok++;
            } catch (GrowthCardSyncService.CooldownActiveException e) {
                skipped++;
            } catch (GrowthCardSyncService.SellerNotFoundException e) {
                skipped++;
            } catch (Exception e) {
                failed++;
                log.warn("Scheduled growth-card sync failed for sellerId={}: {}",
                        sellerId, e.getMessage());
            }
            sleep(perSellerDelayMs);
        }
        log.info("Growth card scheduler done in {} ms. ok={} skipped={} failed={}",
                System.currentTimeMillis() - started, ok, skipped, failed);
    }

    /** Weekly GC of stale/dismissed/done cards older than 60 days. */
    @Scheduled(cron = "${growth-card.gc.cron:0 30 4 * * SUN}", zone = "Asia/Kolkata")
    @org.springframework.transaction.annotation.Transactional
    public void gcOldCards() {
        try {
            int n = cardRepository.deleteOlderThan(LocalDateTime.now().minusDays(60));
            if (n > 0) log.info("Growth card GC removed {} old rows.", n);
        } catch (Exception e) {
            log.warn("Growth card GC failed: {}", e.getMessage());
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Test seam — kept simple, returns the IDs without performing the sync. */
    public List<Long> dueSellerIds(LocalDateTime before) {
        return syncStateRepository.findSellerIdsDueForSync(before);
    }
}
