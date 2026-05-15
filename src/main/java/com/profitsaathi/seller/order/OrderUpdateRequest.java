package com.profitsaathi.seller.order;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * PATCH /api/v1/order/{id} payload. All fields optional — client sends only
 * what changed. Identity comes from the JWT, never from the body.
 *
 * Locked fields: productId, quantity, unitPrice, costPrice, profit. Those are
 * the financial truth of a placed order — to "fix" them, cancel and re-create.
 */
@Data
public class OrderUpdateRequest {

    @Size(min = 2, max = 255, message = "customerName must be 2-255 characters")
    private String customerName;

    @Pattern(regexp = "^[6-9]\\d{9}$|^$",
             message = "phoneNumber must be a 10-digit Indian mobile number")
    private String phoneNumber;

    @Size(min = 5, max = 1024, message = "address must be 5-1024 characters")
    private String address;

    @Size(max = 1024, message = "comments must be 1024 characters or less")
    private String comments;

    @Pattern(regexp = "PENDING|CONFIRMED|SHIPPED|DELIVERED|DELIVERY_FAILED|CANCELLED",
             message = "orderStatus must be one of: PENDING, CONFIRMED, SHIPPED, DELIVERED, DELIVERY_FAILED, CANCELLED")
    private String orderStatus;

    @Pattern(regexp = "INITIATED|PAID|FAILED|REFUND",
             message = "paymentStatus must be one of: INITIATED, PAID, FAILED, REFUND")
    private String paymentStatus;

    @Size(max = 64, message = "shippingVendor must be 64 characters or less")
    private String shippingVendor;

    @Size(max = 128, message = "trackingId must be 128 characters or less")
    private String trackingId;
}
