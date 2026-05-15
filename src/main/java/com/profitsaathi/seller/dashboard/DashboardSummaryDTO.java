package com.profitsaathi.seller.dashboard;

import java.math.BigDecimal;

public class DashboardSummaryDTO {

    private BigDecimal totalSales;
    private BigDecimal totalExpenses;
    private BigDecimal netProfit;
    private Long totalProducts;

    public DashboardSummaryDTO(BigDecimal totalSales,
                               BigDecimal totalExpenses,
                               BigDecimal netProfit,
                               Long totalProducts) {
        this.totalSales = totalSales;
        this.totalExpenses = totalExpenses;
        this.netProfit = netProfit;
        this.totalProducts = totalProducts;
    }

    public BigDecimal getTotalSales() { return totalSales; }
    public BigDecimal getTotalExpenses() { return totalExpenses; }
    public BigDecimal getNetProfit() { return netProfit; }
    public Long getTotalProducts() { return totalProducts; }
}
