package com.profitsaathi.seller.product;

import com.profitsaathi.seller.user.Seller;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "products")
@EntityListeners(AuditingEntityListener.class)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private Seller seller;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ElementCollection
    @CollectionTable(name = "product_images", joinColumns = @JoinColumn(name = "product_id"))
    @Column(name = "image_path", length = 512)
    private List<String> imagePaths = new ArrayList<>();

    @Column
    private Integer mainImageIndex = 0;

    @Column(nullable = false)
    private String status;

    private String publicToken;

    @Column(precision = 10, scale = 2)
    private BigDecimal costPrice;

    @Column(precision = 10, scale = 2)
    private BigDecimal shippingCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal packagingCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal competitorPrice;

    @Column(precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @LastModifiedBy
    private String updatedBy;

    public Product() {}

    public Product(Long id, Seller seller, String name, String description,
                   String status, BigDecimal costPrice, BigDecimal shippingCost,
                   BigDecimal packagingCost, BigDecimal competitorPrice,
                   BigDecimal sellingPrice, LocalDateTime createdAt,
                   LocalDateTime updatedAt, String updatedBy) {
        this.id = id;
        this.seller = seller;
        this.name = name;
        this.description = description;
        this.status = status;
        this.costPrice = costPrice;
        this.shippingCost = shippingCost;
        this.packagingCost = packagingCost;
        this.competitorPrice = competitorPrice;
        this.sellingPrice = sellingPrice;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }
}
