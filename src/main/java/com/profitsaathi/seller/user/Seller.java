package com.profitsaathi.seller.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.profitsaathi.util.aes.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "sellers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EntityListeners(AuditingEntityListener.class)
public class Seller {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(nullable = false, unique = true)
    private String publicToken;

    @Column(name = "onboarded_at")
    private LocalDateTime onboardedAt;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "store_name", length = 255)
    private String storeName;

    @Column(name = "seller_type", length = 50)
    private String sellerType;

    @Column(name = "mobile", length = 16)
    private String mobile;

    @Column(name = "language", length = 8)
    private String language;

    @Column(name = "theme", length = 16)
    private String theme;

    @Column(name = "accent", length = 16)
    private String accent;

    @Column(name = "payment_type", length = 50)
    private String paymentType;

    @Column(name = "payment_qrcode", length = 1024)
    private String paymentQRCode;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "bank_account_number", length = 256)
    private String bankAccountNumber;

    @Column(name = "bank_account_ifsc", length = 16)
    private String bankAccountIfsc;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "gemini_api_key", length = 512)
    private String geminiApiKey;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "openrouter_api_key", length = 512)
    private String openrouterApiKey;

    @JsonIgnore
    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "nvidia_api_key", length = 512)
    private String nvidiaApiKey;



    /**
     * Seller's preferred primary LLM. The chat service tries this first;
     * if it errors out, it falls back through the other two providers
     * in a fixed order. Stored as a string so reorders of the enum stay
     * safe.
     */
    @Column(name = "ai_primary_provider", length = 16)
    private String aiPrimaryProvider;

    /**
     * Whether the seller wants the automated weekly/monthly business report
     * emails. Default true on signup so opt-out is explicit. Toggled via the
     * existing {@code /seller/preferences} endpoint.
     *
     * <p>{@code columnDefinition} is set so Hibernate's {@code ddl-auto=update}
     * mode can add the column to an existing table — without an explicit
     * default it skips the ALTER (existing rows would violate NOT NULL).
     */
    @Builder.Default
    @Column(name = "weekly_report_opt_in", nullable = false,
            columnDefinition = "boolean default true")
    private Boolean weeklyReportOptIn = Boolean.TRUE;

    @Builder.Default
    @Column(name = "monthly_report_opt_in", nullable = false,
            columnDefinition = "boolean default true")
    private Boolean monthlyReportOptIn = Boolean.TRUE;

    @Transient
    @JsonProperty("geminiApiKeySet")
    public boolean isGeminiApiKeySet() {
        return geminiApiKey != null && !geminiApiKey.isBlank();
    }

    @Transient
    @JsonProperty("openrouterApiKeySet")
    public boolean isOpenrouterApiKeySet() {
        return openrouterApiKey != null && !openrouterApiKey.isBlank();
    }

    @Transient
    @JsonProperty("nvidiaApiKeySet")
    public boolean isNvidiaApiKeySet() {
        return nvidiaApiKey != null && !nvidiaApiKey.isBlank();
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @LastModifiedBy
    private String updatedBy;

    @Transient
    @JsonProperty("bankAccountMasked")
    public String getBankAccountMasked() {
        if (bankAccountNumber == null || bankAccountNumber.length() < 4) return null;
        return "•••• " + bankAccountNumber.substring(bankAccountNumber.length() - 4);
    }

    @PrePersist
    public void ensurePublicToken() {
        if (publicToken == null || publicToken.isBlank()) {
            publicToken = java.util.UUID.randomUUID().toString().replace("-", "");
        }
    }

    public enum Status { ACTIVE, INACTIVE, SUSPENDED, BLOCKED }
}
