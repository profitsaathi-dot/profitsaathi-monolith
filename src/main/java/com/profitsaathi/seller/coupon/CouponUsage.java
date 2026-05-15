package com.profitsaathi.seller.coupon;

import com.profitsaathi.seller.product.Product;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "coupon_usage")
public class CouponUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String couponId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String orderId;

    private LocalDateTime usedAt = LocalDateTime.now();
}
