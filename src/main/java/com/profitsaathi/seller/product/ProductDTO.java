package com.profitsaathi.seller.product;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductDTO {
    private String name;
    private String description;
    private String status;
    private BigDecimal costPrice;
    private BigDecimal shippingCost;
    private BigDecimal packagingCost;
    private BigDecimal competitorPrice;
    private BigDecimal sellingPrice;
}
