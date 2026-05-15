package com.profitsaathi.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Consolidated read-model for /admin/whatsapp. WAHA server health, plus
 * one row per session enriched with the seller info from our DB.
 */
public record WhatsAppOverview(
        ServerStatus server,
        Counts counts,
        List<SessionRow> sessions
) {
    public enum ServerStatus { UP, DOWN, UNKNOWN }

    public record Counts(
            int total,
            int working,
            int connected,
            int scanning,
            int failed,
            int stopped,
            int other,
            Map<String, Long> byStatus
    ) {}

    public record SessionRow(
            String name,
            String status,
            String wahaStatus,
            boolean connected,
            String phoneNumber,
            String pushName,
            Long sellerId,
            String sellerName,
            String sellerEmail,
            String storeName,
            LocalDateTime updatedAt
    ) {}
}
