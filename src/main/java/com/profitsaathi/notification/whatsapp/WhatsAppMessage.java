package com.profitsaathi.notification.whatsapp;

import lombok.Builder;

/**
 * In-process equivalent of the old Kafka {@code WhatsAppEvent}.
 * One of {@code otp} / {@code text} must be non-blank.
 */
@Builder
public record WhatsAppMessage(
        String session,   // null/blank → "default" (single-tenant WAHA)
        String chatId,
        String otp,
        int expiry,
        String text
) {}
