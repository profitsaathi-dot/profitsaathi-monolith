package com.profitsaathi.seller.coupon;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "coupons")
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    private DiscountType discountType;

    private BigDecimal discountValue;
    private BigDecimal maxDiscount;
    private BigDecimal minOrderAmount;

    private Integer usageLimit;
    private Integer usedCount = 0;
    private Integer perUserLimit = 1;

    private Boolean active = true;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDateTime expiryDate;

    private LocalDateTime createdAt = LocalDateTime.now();
}
