package com.profitsaathi.seller.payment;

import lombok.Data;

@Data
public class PaymentVerifyRequest {
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;
    private String orderId;
    private Double amount;
}
