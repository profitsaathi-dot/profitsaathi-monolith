package com.profitsaathi.ai.growthadviser;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * Wire shape for a single growth card. Mirrors {@link GrowthCard} minus
 * the seller_id (it's implicit in the authenticated request) and any
 * audit columns the client doesn't render.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GrowthCardDto(
        Long id,
        String type,
        String title,
        String description,
        String priority,
        String impact,
        String cta,
        String actionUrl,
        Integer confidenceScore,
        String status,
        LocalDateTime syncRunAt,
        LocalDateTime createdAt
) {
    public static GrowthCardDto of(GrowthCard c) {
        return new GrowthCardDto(
                c.getId(),
                c.getType() == null ? null : c.getType().name(),
                c.getTitle(),
                c.getDescription(),
                c.getPriority() == null ? null : c.getPriority().name(),
                c.getImpact(),
                c.getCta(),
                c.getActionUrl(),
                c.getConfidenceScore(),
                c.getStatus() == null ? null : c.getStatus().name(),
                c.getSyncRunAt(),
                c.getCreatedAt());
    }
}
