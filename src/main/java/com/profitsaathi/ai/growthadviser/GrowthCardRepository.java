package com.profitsaathi.ai.growthadviser;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface GrowthCardRepository extends JpaRepository<GrowthCard, Long> {

    List<GrowthCard> findBySellerIdAndStatusOrderByPriorityAscCreatedAtDesc(
            Long sellerId, GrowthCard.Status status);

    List<GrowthCard> findBySellerIdOrderByCreatedAtDesc(Long sellerId);

    /**
     * Bulk-mark all currently-active cards for a seller as STALE before a
     * fresh sync inserts new ones. Cards the seller has already actioned
     * (DONE / DISMISSED) keep their state — only ACTIVE/READ are
     * superseded.
     */
    @Modifying
    @Query("""
           UPDATE GrowthCard c
              SET c.status = com.profitsaathi.ai.growthadviser.GrowthCard.Status.STALE
            WHERE c.sellerId = :sellerId
              AND c.status IN (com.profitsaathi.ai.growthadviser.GrowthCard.Status.ACTIVE,
                               com.profitsaathi.ai.growthadviser.GrowthCard.Status.READ)
           """)
    int markActiveCardsStale(@Param("sellerId") Long sellerId);

    /** Garbage-collect old non-active cards so the table doesn't bloat. */
    @Modifying
    @Query("DELETE FROM GrowthCard c WHERE c.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
