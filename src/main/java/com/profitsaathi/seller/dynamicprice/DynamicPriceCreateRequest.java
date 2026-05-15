package com.profitsaathi.seller.dynamicprice;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DynamicPriceCreateRequest {
    private Long productId;
    private BigDecimal price;
    private String customerName;
    private String note;
    private Integer expiryHours;
}
