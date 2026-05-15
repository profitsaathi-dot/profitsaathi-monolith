package com.profitsaathi.seller.subscription;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.profitsaathi.seller.user.Seller;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Entity
@Data
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAGER: getSellerDetails() reads seller.id/name on every JSON write, and
    // open-in-view=false would otherwise close the session before Jackson runs.
    @ManyToOne(fetch = FetchType.EAGER)
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

    private String planName;
    private String status;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    private String paymentProvider;
    private String paymentReference;

    private LocalDateTime createdAt;
}
