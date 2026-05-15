package com.profitsaathi.ai.growthadviser;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per seller — tracks the last Growth Adviser sync so the
 * controller can rate-limit manual syncs and the daily scheduler can skip
 * sellers who already synced in the last 24h.
 *
 * Kept tiny (sellerId PK + last sync timestamps) so the rate-limit check
 * is a single PK lookup on the hot path.
 */
@Entity
@Table(name = "growth_sync_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthSyncState {

    @Id
    @Column(name = "seller_id")
    private Long sellerId;

    /** Most recent successful sync — used for cooldown gating. */
    @Column(name = "last_sync_at")
    private LocalDateTime lastSyncAt;

    /** Source of the last run — "manual" or "scheduled". Audit only. */
    @Column(name = "last_source", length = 16)
    private String lastSource;

    /** Counter of successful syncs today (calendar day, server tz).
     *  Reset by {@link com.profitsaathi.ai.growthadviser.GrowthCardSyncService}
     *  whenever {@code dayBucket} rolls over. */
    @Column(name = "syncs_today", nullable = false)
    @Builder.Default
    private int syncsToday = 0;

    /** Calendar day of {@code syncsToday}. ISO yyyy-MM-dd. */
    @Column(name = "day_bucket", length = 10)
    private String dayBucket;
}
