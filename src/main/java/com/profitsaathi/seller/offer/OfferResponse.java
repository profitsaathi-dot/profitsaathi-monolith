package com.profitsaathi.seller.offer;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class OfferResponse {
    private Long offerId;
    private String name;
    private String icon;
    private BigDecimal price;
    private Integer stockLimit;
    private Integer sold;
    private LocalDateTime endTime;
}
