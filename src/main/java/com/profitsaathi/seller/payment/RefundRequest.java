package com.profitsaathi.seller.payment;

import lombok.Data;

@Data
public class RefundRequest {
    private Double amount;
    private String reason;
    private String finalOrderStatus;
}
