package com.profitsaathi.seller.sales;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class SalesSummaryRequestDTO {
    private Integer month;
    private Integer year;
    private BigDecimal totalSales;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private BigDecimal profitMargin;
    private Integer healthScore;
}
