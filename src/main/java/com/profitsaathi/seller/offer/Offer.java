package com.profitsaathi.seller.offer;

import com.profitsaathi.seller.product.Product;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "offers")
public class Offer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String name;
    private String icon;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    private Integer stockLimit;
    private Integer sold;

    private LocalDateTime endTime;

    private Boolean active = true;
}
