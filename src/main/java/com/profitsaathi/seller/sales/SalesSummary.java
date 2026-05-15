package com.profitsaathi.seller.sales;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.profitsaathi.seller.user.Seller;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@Table(name = "sales_summary")
public class SalesSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @JsonProperty("seller")
    public Map<String, String> getSellerDetails() {
        Map<String, String> map = new HashMap<>();
        if (seller != null) {
            map.put("seller_id", String.valueOf(seller.getId()));
            map.put("name", seller.getName());
        }
        return map;
    }

    private Integer month;
    private Integer year;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalSales;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalExpenses;

    @Column(precision = 12, scale = 2)
    private BigDecimal netProfit;

    @Column(precision = 5, scale = 2)
    private BigDecimal profitMargin;

    private Integer healthScore;

    private LocalDateTime createdAt;

    /**
     * When the seller-facing monthly report email was sent for this row.
     * Null until {@code MonthlyBusinessScheduler} dispatches the mail, giving
     * the scheduler an idempotent "skip if already mailed" guard if it ever
     * gets re-run for the same month.
     */
    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;
}
