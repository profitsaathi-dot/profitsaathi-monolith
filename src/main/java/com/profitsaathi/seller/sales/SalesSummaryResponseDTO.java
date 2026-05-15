package com.profitsaathi.seller.sales;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesSummaryResponseDTO {
    private Long id;
    private Integer month;
    private Integer year;
    private BigDecimal totalSales;
    private BigDecimal netProfit;
}
