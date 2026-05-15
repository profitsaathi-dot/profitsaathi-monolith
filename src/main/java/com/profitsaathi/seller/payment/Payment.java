package com.profitsaathi.seller.payment;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;

    private String orderId;
    private Double amount;

    private String status; // PENDING / SUCCESS / FAILED / REFUNDED

    @Column(name = "payment_type", length = 32)
    private String paymentType;

    @Column(name = "proof_image_url", length = 1024)
    private String proofImageUrl;

    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "refund_id", length = 64)
    private String refundId;

    @Column(name = "refund_amount")
    private Double refundAmount;

    @Column(name = "refund_proof_url", length = 1024)
    private String refundProofUrl;

    @Column(name = "refund_reason", length = 512)
    private String refundReason;

    @Column(name = "refunded_at")
    private LocalDateTime refundedAt;
}
