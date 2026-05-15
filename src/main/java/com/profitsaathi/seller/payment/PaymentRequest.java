package com.profitsaathi.seller.payment;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {
    private BigDecimal amount;
    private String screenshotUrl;
    private String upiRefId;
    private String customerName;
    private String phoneNumber;
}
