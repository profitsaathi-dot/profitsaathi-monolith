package com.profitsaathi.seller.order;

import lombok.Data;

@Data
public class OrderRequest {
    private String orderNumber;
    private String customerName;
    private String phoneNumber;
    private String address;
    private Long productId;
    private Integer quantity;
    private String purchaseType;
    private Long offerId;
    private String comments;
    /** Optional. When set, the order is created against a one-time
     *  dynamic-price listing — productId/price are sourced from the listing
     *  and the listing flips ACTIVE → USED on success. */
    private String dynamicPriceToken;
}
