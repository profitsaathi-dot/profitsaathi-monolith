package com.profitsaathi.usagetracking;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Per-credential feature usage counter. Works for both sellers and customers
 * because we key on {@code credentialsId} (the JWT subject) — not on Seller/Customer
 * directly. One row per (credentialsId, featureName).
 */
@Entity
@Data
@Table(name = "usage_tracking",
        uniqueConstraints = @UniqueConstraint(columnNames = {"credentials_id", "feature_name"}))
public class UsageTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "credentials_id", nullable = false)
    private Long credentialsId;

    /** SELLER | CUSTOMER | ADMIN — matches {@code Credentials.Role}. */
    @Column(name = "subject_role", length = 16)
    private String subjectRole;

    @Column(name = "feature_name", nullable = false, length = 128)
    private String featureName;

    @Column(name = "usage_count")
    private Integer usageCount = 0;

    @Column(name = "last_reset_date")
    private LocalDate lastResetDate;
}
