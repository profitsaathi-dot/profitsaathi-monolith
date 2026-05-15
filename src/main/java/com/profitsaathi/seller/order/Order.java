package com.profitsaathi.seller.order;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.profitsaathi.seller.offer.Offer;
import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.user.Seller;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderNo;

    // EAGER: getSellerSummary() reads seller.id/name on every JSON write, so a
    // lazy proxy guarantees a LazyInitializationException once the Hibernate
    // session closes (open-in-view=false). Loading the row up-front avoids
    // that round-trip-after-commit class of bug.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "seller_id", nullable = false)
    @JsonIgnore
    private Seller seller;

    @JsonProperty("seller")
    public Map<String, Object> getSellerSummary() {
        Map<String, Object> map = new HashMap<>();
        if (seller != null) {
            map.put("id", String.valueOf(seller.getId()));
            map.put("name", seller.getName());
        }
        return map;
    }

    @Column(nullable = false)
    private String customerName;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false)
    private String address;

    // EAGER for the same reason as `seller` above — getProductSummary() always
    // reads it during serialization.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnore
    private Product product;

    @JsonProperty("product")
    public Map<String, Object> getProductSummary() {
        Map<String, Object> map = new HashMap<>();
        if (product != null) {
            map.put("id", String.valueOf(product.getId()));
            map.put("name", product.getName());
            map.put("costPrice", product.getCostPrice());
        }
        return map;
    }

    @Column(nullable = false)
    private Integer quantity;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String orderStatus;

    @Column(nullable = false)
    private String paymentStatus;

    private String comments;

    @Column(precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal costPrice;

    private Boolean offerApplied;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "offer_id", nullable = true)
    @JsonIgnore
    private Offer offer;

    @Column(unique = true)
    private String publicToken;

    @Column(name = "shipping_vendor", length = 64)
    private String shippingVendor;

    @Column(name = "tracking_id", length = 128)
    private String trackingId;

    @Column(precision = 12, scale = 2)
    private BigDecimal totalCost;

    @Column(precision = 12, scale = 2)
    private BigDecimal profit;

    private LocalDateTime createdAt;
}
