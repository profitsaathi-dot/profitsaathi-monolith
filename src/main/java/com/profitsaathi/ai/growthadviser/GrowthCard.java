package com.profitsaathi.ai.growthadviser;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * One row per AI-generated growth recommendation. Indexed on
 * {@code seller_id + status} so the dashboard query
 * {@code WHERE seller_id = ? AND status = 'ACTIVE'} is index-only.
 *
 * Distinct from {@link com.profitsaathi.seller.ai.AiSuggestion} — that table
 * is fed by {@code GrowthSuggestionService} on monthly rollups for legacy
 * alerts. The card-driven Growth Adviser owns its own table so we can
 * version the schema independently and drop it without touching the
 * legacy alerts.
 */
@Entity
@Table(name = "growth_cards", indexes = {
        @Index(name = "idx_growth_cards_seller_status", columnList = "seller_id,status"),
        @Index(name = "idx_growth_cards_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GrowthCard {

    public enum CardType { PRICING, INVENTORY, MARKETING, CRM, FESTIVAL, FINANCE, OPERATIONS }

    public enum Priority { HIGH, MEDIUM, LOW }

    public enum Status { ACTIVE, READ, DONE, DISMISSED, STALE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored as a plain FK column. We don't @ManyToOne the Seller to keep
     *  reads cheap — the dashboard never needs the seller object. */
    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CardType type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Priority priority;

    /** Estimated INR impact, e.g. "+₹12,000/month" or "Save ₹4,800". Free
     *  text — the LLM produces it, we don't parse. Nullable for cards
     *  where the impact can't be quantified. */
    @Column(length = 64)
    private String impact;

    /** Short call-to-action label, e.g. "Open pricing" / "Send broadcast". */
    @Column(length = 64)
    private String cta;

    /** Where the CTA leads inside the seller app, e.g. "/pricing" or
     *  "/whatsapp". Nullable — not every card needs deep-linking. */
    @Column(length = 255)
    private String actionUrl;

    /** 0-100 from the LLM; UI buckets into low/medium/high tone. */
    @Column(name = "confidence_score")
    private Integer confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.ACTIVE;

    /** Set when the card was generated; matches {@code sync_runs.id}'s
     *  insertion time so we can group cards by generation batch. */
    @Column(name = "sync_run_at", nullable = false)
    private LocalDateTime syncRunAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
