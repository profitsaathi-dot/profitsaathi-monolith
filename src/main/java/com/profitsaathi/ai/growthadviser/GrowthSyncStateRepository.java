package com.profitsaathi.ai.growthadviser;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface GrowthSyncStateRepository extends JpaRepository<GrowthSyncState, Long> {

    /** Sellers with no sync state at all OR whose last sync is older than
     *  {@code before}. The scheduler uses this to pick today's batch. */
    @org.springframework.data.jpa.repository.Query("""
           SELECT s.sellerId FROM GrowthSyncState s
            WHERE s.lastSyncAt IS NULL OR s.lastSyncAt < :before
           """)
    List<Long> findSellerIdsDueForSync(
            @org.springframework.data.repository.query.Param("before") LocalDateTime before);
}
