package com.profitsaathi.seller.review;

import com.profitsaathi.seller.order.Order;
import com.profitsaathi.seller.product.Product;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Product review submitted by customers after order delivery.
 * Reviews are linked to orders to prevent duplicate reviews and verify purchase.
 */
@Entity
@Data
@Table(name = "product_reviews", indexes = {
        @Index(name = "idx_product_id", columnList = "product_id"),
        @Index(name = "idx_order_id", columnList = "order_id"),
        @Index(name = "idx_created_at", columnList = "created_at")
})
public class ProductReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Column(nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false)
    private Integer rating; // 1-5 stars

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false)
    private Boolean verified = true; // Always true since linked to order

    @Column(nullable = false)
    private Boolean approved = true; // Auto-approved, can be moderated later

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
