package com.profitsaathi.seller.order;

import com.profitsaathi.seller.product.Product;
import com.profitsaathi.seller.user.Seller;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * DTO for Order responses. Avoids EAGER fetching by explicitly projecting
 * only the fields we need from related entities.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDTO {
    
    private Long id;
    private String orderNo;
    
    // Seller summary (only id and name)
    private Map<String, Object> seller;
    
    private String customerName;
    private String phoneNumber;
    private String address;
    
    // Product summary (only id, name, costPrice)
    private Map<String, Object> product;
    
    private Integer quantity;
    private String status;
    private String orderStatus;
    private String paymentStatus;
    private String comments;
    private BigDecimal unitPrice;
    private BigDecimal costPrice;
    private Boolean offerApplied;
    private String publicToken;
    private String shippingVendor;
    private String trackingId;
    private BigDecimal totalCost;
    private BigDecimal profit;
    private LocalDateTime createdAt;
    
    /**
     * Factory method to create DTO from Order entity.
     * Assumes seller and product are already loaded (via JOIN FETCH).
     * 
     * Uses capitalized keys (Id, Name, CostPrice) to match frontend expectations.
     */
    public static OrderDTO fromEntity(Order order) {
        Map<String, Object> sellerMap = new HashMap<>();
        if (order.getSeller() != null) {
            sellerMap.put("Id", String.valueOf(order.getSeller().getId()));
            sellerMap.put("Name", order.getSeller().getName());
        }
        
        Map<String, Object> productMap = new HashMap<>();
        if (order.getProduct() != null) {
            productMap.put("Id", String.valueOf(order.getProduct().getId()));
            productMap.put("Name", order.getProduct().getName());
            productMap.put("CostPrice", order.getProduct().getCostPrice());
        }
        
        return OrderDTO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .seller(sellerMap)
                .customerName(order.getCustomerName())
                .phoneNumber(order.getPhoneNumber())
                .address(order.getAddress())
                .product(productMap)
                .quantity(order.getQuantity())
                .status(order.getStatus())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .comments(order.getComments())
                .unitPrice(order.getUnitPrice())
                .costPrice(order.getCostPrice())
                .offerApplied(order.getOfferApplied())
                .publicToken(order.getPublicToken())
                .shippingVendor(order.getShippingVendor())
                .trackingId(order.getTrackingId())
                .totalCost(order.getTotalCost())
                .profit(order.getProfit())
                .createdAt(order.getCreatedAt())
                .build();
    }
}
