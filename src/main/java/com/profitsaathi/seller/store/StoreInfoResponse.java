package com.profitsaathi.seller.store;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Public-facing store identity. Returned by /api/v1/store/info — never
 * includes password hash, internal credentials id, or onboarding metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StoreInfoResponse {
    private String name;
    private String storeName;
    private String whatsapp;
    private String email;
    private String sellerType;
    private String paymentType;
    private String paymentQRCode;
    private String bankAccountNumber;
    private String bankAccountIfsc;
}
