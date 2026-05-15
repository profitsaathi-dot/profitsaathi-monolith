package com.profitsaathi.seller.coupon;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ApplyCouponRequest {
    private String code;
    private Long product_id;
    private BigDecimal orderAmount;
}
