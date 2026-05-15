package com.profitsaathi.seller.pricing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.profitsaathi.seller.product.Product;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Table(name = "pricing_analysis")
@Data
@EntityListeners(AuditingEntityListener.class)
public class PricingAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @JsonProperty("product")
    public Map<String, String> getProductDetails() {
        Map<String, String> map = new HashMap<>();
        if (product != null) {
            map.put("id", String.valueOf(product.getId()));
            map.put("name", product.getName());
        }
        return map;
    }

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal totalCost;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal breakEvenPrice;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal safePrice;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal aggressivePrice;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal suggestedPrice;

    private String analysisType;

    @Column(columnDefinition = "TEXT")
    private String aiSummary;

    @Column(precision = 5, scale = 2, nullable = false)
    private BigDecimal profitMargin;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @LastModifiedBy
    private String updatedBy;

    @Column(name = "last_calculated_at")
    private LocalDateTime lastCalculatedAt;
}
