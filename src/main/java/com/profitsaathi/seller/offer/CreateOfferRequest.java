package com.profitsaathi.seller.offer;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreateOfferRequest {
    private Long productId;
    private String name;
    private String icon;
    private BigDecimal price;
    private Integer stockLimit;
    private Integer sold;
    private LocalDateTime endTime;
}
