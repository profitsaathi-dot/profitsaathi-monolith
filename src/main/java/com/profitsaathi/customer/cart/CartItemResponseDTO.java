package com.profitsaathi.customer.cart;

import lombok.Data;

import java.util.Map;

@Data
public class CartItemResponseDTO {
    private Long id;
    private Long customerId;
    private String name;
    private Integer qty;

    /** id + sellingPrice + mainImageUrl. */
    private Map<String, Object> productDetails;
}
