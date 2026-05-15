package com.profitsaathi.ai.log;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row per AI request. Captures prompt + response (truncated, see
 * length cap), model, cost, and the requesting principal so admins can
 * audit usage.
 *
 * Kept in {@code ai_chat_logs} so the table is grep-able and can be
 * dropped wholesale if the AI module is extracted later.
 */
@Entity
@Table(name = "ai_chat_logs", indexes = {
        @Index(name = "idx_ai_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_ai_logs_principal", columnList = "principal_email"),
        @Index(name = "idx_ai_logs_model", columnList = "model")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiChatLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "principal_email", length = 255)
    private String principalEmail;

    @Column(name = "principal_role", length = 32)
    private String principalRole;

    @Column(nullable = false, length = 64)
    private String model;

    /**
     * Prompt is capped at 4000 chars. Anything longer is truncated with a
     * marker so logs stay scannable.
     */
    @Column(nullable = false, length = 4000)
    private String prompt;

    /** Response capped to 8000 chars for the same reason. */
    @Column(length = 8000)
    private String response;

    @Column(nullable = false, precision = 12, scale = 6)
    private BigDecimal cost;

    /** "text" or "payment_verification" — mirrors {@code AiChatResponse.type}. */
    @Column(name = "response_type", length = 32)
    private String responseType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
