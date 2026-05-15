package com.profitsaathi.seller.payment;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "payment_audit_log")
public class PaymentAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", length = 64)
    private String orderNo;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(length = 64, nullable = false)
    private String action;

    /** Actor's email (replaces the old keycloakId field). */
    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(length = 2000)
    private String details;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
